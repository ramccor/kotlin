/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.swiftexport.standalone.builders

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.sir.*
import org.jetbrains.kotlin.sir.bridge.*
import org.jetbrains.kotlin.sir.providers.SirSession
import org.jetbrains.kotlin.sir.providers.source.KotlinPropertyAccessorOrigin
import org.jetbrains.kotlin.sir.providers.source.KotlinSource
import org.jetbrains.kotlin.sir.providers.source.kaSymbolOrNull
import org.jetbrains.kotlin.sir.providers.utils.isAbstract
import org.jetbrains.kotlin.sir.util.*
import org.jetbrains.kotlin.utils.addIfNotNull

internal fun SirSession.constructFunctionBridgeRequests(function: SirFunction, generator: BridgeGenerator): List<FunctionBridgeRequest> {
    val fqName = function.kaSymbolOrNull<KaFunctionSymbol>()
        ?.callableId?.asSingleFqName()
        ?.pathSegments()?.map { it.toString() }
        ?: return emptyList()

    return listOfNotNull(
        patchCallableBodyAndGenerateRequest(function, generator, fqName)
    )
}

internal fun SirSession.constructFunctionBridgeRequests(variable: SirVariable, generator: BridgeGenerator): List<FunctionBridgeRequest> {
    val fqName = when (val origin = variable.origin) {
        is KotlinSource -> variable.kaSymbolOrNull<KaVariableSymbol>()
            ?.callableId?.asSingleFqName()
            ?.pathSegments()?.map { it.toString() }
        is SirOrigin.ObjectAccessor -> ((origin.`for` as KotlinSource).symbol as KaNamedClassSymbol)
            .classId?.asSingleFqName()
            ?.pathSegments()?.map { it.toString() }
        else -> null
    } ?: return emptyList()

    val res = mutableListOf<FunctionBridgeRequest>()
    variable.accessors.forEach {
        res.addIfNotNull(
            patchCallableBodyAndGenerateRequest(it, generator, fqName)
        )
    }

    return res.toList()
}

internal fun SirSession.constructFunctionBridgeRequests(init: SirInit, generator: BridgeGenerator): List<FunctionBridgeRequest> {
    return analyze(sirSession.useSiteModule) {
        if (init.origin is SirOrigin.KotlinBaseInitOverride) {
            val names = init.parameters.map { it.argumentName!! }
            init.body = SirFunctionBody(buildList {
                add("super.init(${names.joinToString(separator = ", ") { "$it: $it" }})")
            })
            return@analyze emptyList()
        }

        val constructedClassSymbol = (init.parent as SirClass).kaSymbolOrNull<KaClassSymbol>()
        if (constructedClassSymbol?.modality?.isAbstract() != false) {
            return@analyze emptyList()
        }
        // Array constructors are not supported by bridge generator for now.
        if (constructedClassSymbol.defaultType.isArrayOrPrimitiveArray) {
            return@analyze emptyList()
        }
        val fqName = init.kaSymbolOrNull<KaConstructorSymbol>()
            ?.containingClassId?.asSingleFqName()
            ?.pathSegments()?.map { it.toString() }
            ?: return@analyze emptyList()

        return@analyze listOfNotNull(
            patchCallableBodyAndGenerateRequest(init, generator, fqName)
        )
    }
}

internal fun SirSession.constructPropertyAccessorsBridgeRequests(function: SirFunction, generator: BridgeGenerator): List<FunctionBridgeRequest> {
    val fqName = (function.origin as? KotlinPropertyAccessorOrigin)?.propertySymbol
        ?.callableId?.asSingleFqName()
        ?.pathSegments()?.map { it.toString() }
        ?: return emptyList()

    return listOfNotNull(
        patchCallableBodyAndGenerateRequest(function, generator, fqName)
    )
}

private fun SirSession.patchCallableBodyAndGenerateRequest(
    callable: SirCallable,
    generator: BridgeGenerator,
    fqName: List<String>,
): FunctionBridgeRequest? {
    val typesUsed = listOf(callable.returnType) + callable.allParameters.map { it.type }
    if (typesUsed.any { !isSupported(it) })
        return null
    if (callable.allParameters.any { it.type.isNever })
        return null // If any of the parameters is never - there should be no ability to call this function - therefor we can skip the bridge generation
    if (callable.parent is SirProtocol) {
        return null
    }
    val suffix = callable.bridgeSuffix
    val request = FunctionBridgeRequest(
        callable,
        fqName.forBridge.joinToString("_") + suffix,
        fqName,
    )
    callable.body = generator.generateSirFunctionBody(request)
    return request
}

private fun SirSession.isSupported(type: SirType): Boolean = analyze(useSiteModule) {
    when (type) {
        is SirNominalType -> {
            val declarationSupported = when (val declaration = type.typeDeclaration) {
                is SirTypealias -> isSupported(declaration.type)
                else -> type.typeDeclaration.kaSymbolOrNull<KaNamedClassSymbol>()?.sirAvailability()?.let { it is SirAvailability.Available } != false
            }
            declarationSupported && type.typeArguments.all(::isSupported)
        }
        is SirFunctionalType -> isSupported(type.returnType) && type.parameterTypes.all(::isSupported)
        is SirExistentialType -> type.protocols.all { it.kaSymbolOrNull<KaClassSymbol>()?.sirAvailability() is SirAvailability.Available != false }
        else -> false
    }
}

private val SirCallable.bridgeSuffix: String
    get() = when (this) {
        is SirAccessor -> "_$bridgeSuffix"
        is SirInit -> "_init"
        else -> when (kaSymbolOrNull<KaFunctionSymbol>()) {
            is KaPropertyGetterSymbol -> "_get"
            is KaPropertySetterSymbol -> "_set"
            else -> ""
        }
    }

private val SirAccessor.bridgeSuffix: String
    get() = when (this) {
        is SirGetter -> "get"
        is SirSetter -> "set"
    }

private val List<String>.forBridge: List<String>
    get() = if (this.count() == 1) {
        listOf("__root__", this.first()) // todo: should be changed with correct mangling KT-64970
    } else {
        this
    }
