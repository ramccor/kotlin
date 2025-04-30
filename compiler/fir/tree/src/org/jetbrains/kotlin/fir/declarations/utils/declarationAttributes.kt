/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.utils

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.SourceFile
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyBackingField
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.synthetic.FirSyntheticProperty
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.references.impl.FirPropertyFromParameterResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.Name

private object IsFromVarargKey : FirDeclarationDataKey()
private object IsReferredViaField : FirDeclarationDataKey()
private object IsFromPrimaryConstructor : FirDeclarationDataKey()
private object ComponentFunctionSymbolKey : FirDeclarationDataKey()
private object SourceElementKey : FirDeclarationDataKey()
private object ModuleNameKey : FirDeclarationDataKey()
private object DanglingTypeConstraintsKey : FirDeclarationDataKey()
private object KlibSourceFile : FirDeclarationDataKey()
private object EvaluatedValue : FirDeclarationDataKey()
private object CompilerPluginMetadata : FirDeclarationDataKey()
private object OriginalReplSnippet : FirDeclarationDataKey()
private object ReplSnippetTopLevelDeclaration : FirDeclarationDataKey()

var FirProperty.isFromVararg: Boolean? by FirDeclarationDataRegistry.data(IsFromVarargKey)
var FirProperty.isReferredViaField: Boolean? by FirDeclarationDataRegistry.data(IsReferredViaField)
var FirProperty.fromPrimaryConstructor: Boolean? by FirDeclarationDataRegistry.data(IsFromPrimaryConstructor)
var FirProperty.componentFunctionSymbol: FirNamedFunctionSymbol? by FirDeclarationDataRegistry.data(ComponentFunctionSymbolKey)
var FirClassLikeDeclaration.sourceElement: SourceElement? by FirDeclarationDataRegistry.data(SourceElementKey)
var FirRegularClass.moduleName: String? by FirDeclarationDataRegistry.data(ModuleNameKey)
var FirDeclaration.compilerPluginMetadata: Map<String, ByteArray>? by FirDeclarationDataRegistry.data(CompilerPluginMetadata)
var FirDeclaration.originalReplSnippetSymbol: FirReplSnippetSymbol? by FirDeclarationDataRegistry.data(OriginalReplSnippet)

/**
 * Denotes a declaration on the REPL snippet level - top-level and all nested ones, but not the ones declared inside bodies.
 * Required to distinguish these declarations from "real" local ones, declared in bodies.
 * TODO: Revisit along with KT-75301
 */
var FirDeclaration.isReplSnippetDeclaration: Boolean? by FirDeclarationDataRegistry.data(ReplSnippetTopLevelDeclaration)
val FirBasedSymbol<*>.isReplSnippetDeclaration: Boolean?
    get() = fir.isReplSnippetDeclaration

/**
 * @see [FirBasedSymbol.klibSourceFile]
 */
var FirDeclaration.klibSourceFile: SourceFile? by FirDeclarationDataRegistry.data(KlibSourceFile)

val FirClassLikeSymbol<*>.sourceElement: SourceElement?
    get() = fir.sourceElement

val FirPropertySymbol.fromPrimaryConstructor: Boolean
    get() = fir.fromPrimaryConstructor ?: false

/**
 * Declarations like classes, functions, and properties can encode their containing Kotlin source file into .klibs using
 * klib specific metadata extensions.
 * If present in the klib and deserialized by the corresponding deserializer/symbol provider,
 * then this source file is available here
 * @see FirDeclaration.klibSourceFile
 */
val FirBasedSymbol<FirDeclaration>.klibSourceFile: SourceFile?
    get() = fir.klibSourceFile

var FirProperty.evaluatedInitializer: FirEvaluatorResult? by FirDeclarationDataRegistry.data(EvaluatedValue)

/**
 * Constraint without corresponding type argument
 */
data class DanglingTypeConstraint(val name: Name, val source: KtSourceElement)

var <T> T.danglingTypeConstraints: List<DanglingTypeConstraint>?
        where T : FirDeclaration, T : FirTypeParameterRefsOwner
        by FirDeclarationDataRegistry.data(DanglingTypeConstraintsKey)

// ----------------------------------- Utils -----------------------------------

val FirProperty.hasExplicitBackingField: Boolean
    get() = backingField != null && backingField !is FirDefaultPropertyBackingField

val FirPropertySymbol.hasExplicitBackingField: Boolean
    get() = fir.hasExplicitBackingField

fun FirProperty.getExplicitBackingField(): FirBackingField? {
    return if (hasExplicitBackingField) {
        backingField
    } else {
        null
    }
}

val FirProperty.canNarrowDownGetterType: Boolean
    get() {
        val backingFieldHasDifferentType = backingField != null && backingField?.returnTypeRef?.coneType != returnTypeRef.coneType
        return backingFieldHasDifferentType && getter is FirDefaultPropertyGetter
    }

val FirPropertySymbol.canNarrowDownGetterType: Boolean
    get() = fir.canNarrowDownGetterType

// See [BindingContext.BACKING_FIELD_REQUIRED]
val FirPropertySymbol.hasBackingField: Boolean
    get() {
        if (!fir.mayHaveBackingField) return false
        if (hasExplicitBackingField) return true
        if (isLateInit) return true
        when (origin) {
            is FirDeclarationOrigin.SubstitutionOverride -> return false
            FirDeclarationOrigin.IntersectionOverride -> return false
            FirDeclarationOrigin.Delegated -> return false
            else -> {
                if (isExpect || isAbstract) return false
                val getter = getterSymbol ?: return true
                val setter = setterSymbol
                if (isVar && setter == null) return true
                if (setter?.fir?.isCustom == false && !setter.isAbstract && !setter.isInline) return true
                if (!getter.fir.isCustom && !getter.isAbstract && !getter.isInline) return true
                if (getter.isInline && setter?.isInline != true) return false

                lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
                return fir.isReferredViaField == true
            }
        }
    }

val FirProperty.mayHaveBackingField: Boolean
    get() = this !is FirSyntheticProperty &&
            receiverParameter == null &&
            returnTypeRef !is FirImplicitTypeRef &&
            delegate == null &&
            contextParameters.isEmpty() &&
            typeParameters.isEmpty() &&
            !isStatic // For Enum.entries

val FirProperty.hasBackingField: Boolean
    get() = symbol.hasBackingField

fun FirDeclaration.getDanglingTypeConstraintsOrEmpty(): List<DanglingTypeConstraint> {
    return when (this) {
        is FirRegularClass -> danglingTypeConstraints
        is FirSimpleFunction -> danglingTypeConstraints
        is FirAnonymousFunction -> danglingTypeConstraints
        is FirProperty -> danglingTypeConstraints
        else -> null
    } ?: emptyList()
}

val FirPropertySymbol.correspondingValueParameterFromPrimaryConstructor: FirValueParameterSymbol?
    get() = fir.correspondingValueParameterFromPrimaryConstructor

val FirProperty.correspondingValueParameterFromPrimaryConstructor: FirValueParameterSymbol?
    get() {
        if (fromPrimaryConstructor != true) return null
        val initializer = initializer as? FirPropertyAccessExpression ?: return null
        val reference = initializer.calleeReference as? FirPropertyFromParameterResolvedNamedReference ?: return null
        return reference.resolvedSymbol as? FirValueParameterSymbol
    }
