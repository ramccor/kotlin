/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.components.DebuggerExtension
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.LLResolutionFacade
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.getOrBuildFir
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.isInlinable
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.get
import kotlin.collections.iterator
import kotlin.collections.set

class InlineStackData(
    val capturedReifiedTypeParameterMapping: Map<FirTypeParameterSymbol, ConeKotlinType>,
    val inlineLambdaArgumentsToDepth: Map<FirExpression, Int>,
)

internal fun retrieveInlineStackData(
    file: FirFile,
    resolutionFacade: LLResolutionFacade,
    debuggerExtension: DebuggerExtension?,
): InlineStackData {

    if (debuggerExtension == null) return InlineStackData(emptyMap(), emptyMap())

    val unmappedTypeParameters = mutableSetOf<FirTypeParameterSymbol>()
    file.collectCapturedReifiedTypeParameters(unmappedTypeParameters, resolutionFacade)
    val capturedTypeParameters = unmappedTypeParameters.toSet()

    // We need to save the order to make a substitution on the correct order later
    val mapping = linkedMapOf<FirTypeParameterSymbol, FirTypeRef>()

    val unsubstitutedInlineLambdaParameters = mutableSetOf<FirValueParameterSymbol>()
    collectInlineLambdaParameters(file, unsubstitutedInlineLambdaParameters, resolutionFacade.useSiteFirSession)

    // We basically roll back along the execution stack until either all required type parameters are mapped on arguments, or
    // we are unable to proceed further for some reason
    // (e.g., we've reached the execution stack beginning, or we failed to extract relevant info from the call)
    // Note that there are cases when a reified type parameter is captured by code fragment, but we are still able to compile it
    // without reification, that is why we avoid fast-failing here if not all the type parameters are mapped.
    val stackIterator = debuggerExtension.stack.iterator()
    var depth = 0
    val substitutedExprToDepth = mutableMapOf<FirExpression, Int>()
    while (stackIterator.hasNext() && (unmappedTypeParameters.isNotEmpty() || unsubstitutedInlineLambdaParameters.isNotEmpty())) {
        val previousExprPsi = stackIterator.next() ?: continue
        depth++
        updateReifiedTypeParametersInfo(previousExprPsi, resolutionFacade, unmappedTypeParameters, mapping)
        updateInlineLambdasInfo(previousExprPsi, resolutionFacade, file, substitutedExprToDepth, depth)
        continue
    }

    val toConeTypeMapping: LinkedHashMap<FirTypeParameterSymbol, ConeKotlinType> =
        mapping.mapValues { (_, firTypeRef) -> firTypeRef.coneType }.toMap(LinkedHashMap())


    val typeSubstitutor = substitutorByMap(toConeTypeMapping, resolutionFacade.useSiteFirSession)

    // The parameters are ordered in the map according the order of declaring function in execution stack, e.g.:
    //
    // fun <reified T3> foo3() {
    //     ...suspension point...
    // }
    // fun <reified T2> foo2() {
    //     foo3<T2>()
    // }
    // fun <reified T1> foo1() {
    //     foo2<T1>()
    // }
    // ... entry point...
    // fun main() {
    //     foo1<Int>()
    // }
    //
    // Parameters will be ordered as T3, T2, T1, i.e. argument follows the parameter.
    // Thus, processing them in reversive order gives the transitive closure of substitution.
    for (typeParameter in toConeTypeMapping.keys.reversed().iterator()) {
        toConeTypeMapping[typeParameter] =
            typeSubstitutor.substituteOrSelf(toConeTypeMapping[typeParameter]!!)
    }

    // It's vital to leave only parameters immediately captured by code fragment, as JVM ReifiedTypeInliner does not distinguish
    // different type parameters with the same name
    // See IntelliJ test:
    // community/plugins/kotlin/jvm-debugger/test/testData/evaluation/singleBreakpoint/reifiedTypeParameters/crossfileInlining.kt
    return InlineStackData(toConeTypeMapping.filterKeys { it in capturedTypeParameters }, substitutedExprToDepth)
}

private fun updateInlineLambdasInfo(
    previousExprPsi: PsiElement,
    resolutionFacade: LLResolutionFacade,
    file: FirFile,
    substitutedExprToDepth: MutableMap<FirExpression, Int>,
    depth: Int,
) {
    val inlineCall: FirCall = previousExprPsi.parentsWithSelf.firstNotNullOfOrNull { psiElement ->
        if (psiElement is KtElement) psiElement.getOrBuildFir(resolutionFacade) as? FirCall else null
    } ?: return
    substituteArgumentToInlineLambdaParam(
        file,
        inlineCall.resolvedArgumentMapping,
        substitutedExprToDepth,
        depth
    )
}

private fun substituteArgumentToInlineLambdaParam(
    firElement: FirElement,
    resolvedArgumentMapping: LinkedHashMap<FirExpression, FirValueParameter>?,
    substitutedExprToDepth: MutableMap<FirExpression, Int>,
    depth: Int,
) {
    val paramToExpr = resolvedArgumentMapping?.entries?.associate { (key, value) -> value.symbol to key } ?: return

    firElement.accept(object : FirTransformer<Nothing?>() {
        override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
            element.transformChildren(this, null)
            return element
        }

        override fun transformPropertyAccessExpression(
            propertyAccessExpression: FirPropertyAccessExpression,
            data: Nothing?,
        ): FirStatement {
            propertyAccessExpression.transformChildren(this, null)
            val argument = paramToExpr[propertyAccessExpression.toResolvedCallableSymbol()]
            return if (argument != null) {
                substitutedExprToDepth[argument] = depth
                argument
            } else
                propertyAccessExpression
        }
    }, null)
}

private fun collectInlineLambdaParameters(
    element: FirElement,
    unsubstitutedInlineLambdaParameters: MutableSet<FirValueParameterSymbol>,
    session: FirSession,
) {
    element.accept(object : FirDefaultVisitorVoid() {
        override fun visitElement(element: FirElement) {
            element.acceptChildren(this)
        }

        override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression) {
            propertyAccessExpression.acceptChildren(this)
            val valueParam = propertyAccessExpression.toResolvedCallableSymbol() as? FirValueParameterSymbol ?: return
            if (valueParam.fir.isInlinable(session)) unsubstitutedInlineLambdaParameters.add(valueParam)
        }
    })
}

private fun updateReifiedTypeParametersInfo(
    previousExprPsi: PsiElement,
    resolutionFacade: LLResolutionFacade,
    unmappedTypeParameters: MutableSet<FirTypeParameterSymbol>,
    mapping: LinkedHashMap<FirTypeParameterSymbol, FirTypeRef>,
) {
    // Rolling back by parents trying to find type arguments
    // The property setter call is a special case as it's represented as `FirVariableAssignment`
    // and the type arguments should be extracted from its `lvalue`
    val typeArgumentHolder: FirQualifiedAccessExpression = previousExprPsi.parentsWithSelf.firstNotNullOfOrNull { psiElement ->
        if (psiElement is KtElement) {
            val fir = psiElement.getOrBuildFir(resolutionFacade)
            when (fir) {
                is FirQualifiedAccessExpression -> fir
                is FirVariableAssignment -> if (fir.lValue is FirQualifiedAccessExpression) {
                    fir.lValue as FirQualifiedAccessExpression
                } else {
                    null
                }
                else -> null
            }
        } else {
            null
        }
    } ?: return

    val extractedFromPreviousExpression = extractReifiedTypeArguments(typeArgumentHolder)

    for ((extractedParam, extractedArg) in extractedFromPreviousExpression) {
        if (extractedParam in unmappedTypeParameters) {
            mapping[extractedParam] = extractedArg
            unmappedTypeParameters.remove(extractedParam)
            extractedArg.collectTypeParameters(unmappedTypeParameters)
        }
    }
}

private fun extractReifiedTypeArguments(typeArgumentsHolder: FirQualifiedAccessExpression): Map<FirTypeParameterSymbol, FirTypeRef> {
    val callableSymbol = typeArgumentsHolder.calleeReference.toResolvedCallableSymbol() ?: return emptyMap()
    return buildMap {
        for ((typeParameterSymbol, typeArgument) in callableSymbol.typeParameterSymbols.zip(typeArgumentsHolder.typeArguments)) {
            if (typeParameterSymbol.isReified && typeArgument is FirTypeProjectionWithVariance) {
                put(typeParameterSymbol, typeArgument.typeRef)
            }
        }
    }
}

private fun FirElement.collectCapturedReifiedTypeParameters(
    destination: MutableSet<FirTypeParameterSymbol>,
    resolutionFacade: LLResolutionFacade,
) {
    this.accept(object : FirDefaultVisitorVoid() {
        override fun visitElement(element: FirElement) {
            when (element) {
                is FirExpression -> {
                    val symbol = element.resolvedType.toSymbol(resolutionFacade.useSiteFirSession)
                    if (symbol is FirTypeParameterSymbol && symbol.isReified) destination.add(symbol)
                }
                is FirResolvedTypeRef -> {
                    processConeType(element.coneType)
                }
            }
        }

        private fun processConeType(type: ConeKotlinType) {
            if (type is ConeTypeParameterType) {
                val symbol = type.lookupTag.typeParameterSymbol
                if (symbol.isReified) destination.add(symbol)
            }
            for (typeArgument in type.typeArguments) {
                typeArgument.type?.let { processConeType(it) }
            }
        }
    })
}

private fun FirTypeRef.collectTypeParameters(destination: MutableSet<FirTypeParameterSymbol>) =
    (this as? FirResolvedTypeRef)?.coneType?.collectTypeParameters(destination)

private fun ConeKotlinType.collectTypeParameters(destination: MutableSet<FirTypeParameterSymbol>) {
    if (this is ConeTypeParameterType) {
        destination.add(lookupTag.typeParameterSymbol)
        return
    }
    typeArguments.forEach { typeArgument ->
        typeArgument.type?.collectTypeParameters(destination)
    }
}
