/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.references

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.fir.references.KDocReferenceResolver.getTypeQualifiedExtensions
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.analysis.utils.printer.parentsOfType
import org.jetbrains.kotlin.load.java.possibleGetMethodNames
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import kotlin.reflect.KClass

internal object KDocReferenceResolver {
    /**
     * [symbol] is the symbol referenced by this resolve result.
     *
     * [receiverClassReference] is an optional receiver type in
     * the case of extension function references (see [getTypeQualifiedExtensions]).
     */
    private data class ResolveResult(val symbol: KaSymbol, val receiverClassReference: KaClassLikeSymbol?)

    private fun KaSymbol.toResolveResult(receiverClassReference: KaClassLikeSymbol? = null): ResolveResult =
        ResolveResult(symbol = this, receiverClassReference)

    /**
     * Resolves the [selectedFqName] of KDoc
     *
     * To properly resolve qualifier parts in the middle,
     * we need to resolve the whole qualifier to understand which parts of the qualifier are package or class qualifiers.
     * And then we will be able to resolve the qualifier selected by the user to the proper class, package or callable.
     *
     * It's possible that the whole qualifier is invalid, in this case we still want to resolve our [selectedFqName].
     * To do this, we are trying to resolve the whole qualifier until we succeed.
     *
     * @param selectedFqName the selected fully qualified name of the KDoc
     * @param fullFqName the whole fully qualified name of the KDoc
     * @param contextElement the context element in which the KDoc is defined
     *
     * @return the collection of [KaSymbol](s) resolved from the fully qualified name
     *         based on the selected FqName and context element
     */
    internal fun resolveKdocFqName(
        analysisSession: KaSession,
        selectedFqName: FqName,
        fullFqName: FqName,
        contextElement: KtElement,
    ): Collection<KaSymbol> {
        with(analysisSession) {
            //ensure file context is provided for "non-physical" code as well
            val contextDeclarationOrSelf = PsiTreeUtil.getContextOfType(contextElement, KtDeclaration::class.java, false)
                ?: contextElement
            val fullSymbolsResolved =
                resolveKdocFqName(fullFqName, contextDeclarationOrSelf).sortedByDescending { it.symbol is KaClassLikeSymbol }
            if (selectedFqName == fullFqName) return fullSymbolsResolved.mapTo(mutableSetOf()) { it.symbol }
            if (fullSymbolsResolved.isEmpty()) {
                val parent = fullFqName.parent()
                return resolveKdocFqName(analysisSession, selectedFqName, parent, contextDeclarationOrSelf)
            }
            val goBackSteps = fullFqName.pathSegments().size - selectedFqName.pathSegments().size
            check(goBackSteps > 0) {
                "Selected FqName ($selectedFqName) should be smaller than the whole FqName ($fullFqName)"
            }
            return fullSymbolsResolved.mapNotNullTo(mutableSetOf()) { findParentSymbol(it, goBackSteps, selectedFqName) }
        }
    }

    /**
     * Finds the parent symbol of the given [ResolveResult] by traversing back up the symbol hierarchy a [goBackSteps] steps,
     * or until the containing class or object symbol is found.
     *
     * Knows about the [ResolveResult.receiverClassReference] field and uses it in case it's not empty.
     */
    private fun KaSession.findParentSymbol(resolveResult: ResolveResult, goBackSteps: Int, selectedFqName: FqName): KaSymbol? {
        return if (resolveResult.receiverClassReference != null) {
            findParentSymbol(resolveResult.receiverClassReference, goBackSteps - 1, selectedFqName)
        } else {
            findParentSymbol(resolveResult.symbol, goBackSteps, selectedFqName)
        }
    }

    /**
     * Finds the parent symbol of the given [KaSymbol] by traversing back up the symbol hierarchy a certain number of steps,
     * or until the containing class or object symbol is found.
     *
     * @param symbol The [KaSymbol] whose parent symbol needs to be found.
     * @param goBackSteps The number of steps to go back up the symbol hierarchy.
     * @param selectedFqName The fully qualified name of the selected package.
     * @return The [goBackSteps]-th parent [KaSymbol]
     */
    private fun KaSession.findParentSymbol(symbol: KaSymbol, goBackSteps: Int, selectedFqName: FqName): KaSymbol? {
        if (symbol !is KaDeclarationSymbol && symbol !is KaPackageSymbol) return null

        if (symbol is KaDeclarationSymbol) {
            goToNthParent(symbol, goBackSteps)?.let { return it }
        }

        return findPackage(selectedFqName)
    }

    /**
     * N.B. Works only for [KaClassSymbol] parents chain.
     */
    private fun KaSession.goToNthParent(symbol: KaDeclarationSymbol, steps: Int): KaDeclarationSymbol? {
        var currentSymbol = symbol

        repeat(steps) {
            currentSymbol = currentSymbol.containingDeclaration as? KaClassSymbol ?: return null
        }

        return currentSymbol
    }

    private fun KaSession.resolveKdocFqName(
        fqName: FqName,
        contextElement: KtElement,
        trySyntheticGetters: Boolean = true,
    ): Collection<ResolveResult> {
        getExtensionReceiverSymbolByThisQualifier(fqName, contextElement).ifNotEmpty { return this }

        buildList {
            getSymbolsFromScopes(fqName, contextElement).firstOrNull()?.mapTo(this) { it.toResolveResult() }
            addAll(getTypeQualifiedExtensions(fqName, contextElement))
            addIfNotNull(findPackage(fqName)?.toResolveResult())
        }.ifNotEmpty { return this }

        getNonImportedSymbolsByFullyQualifiedName(fqName).map { it.toResolveResult() }.ifNotEmpty { return this }

        AdditionalKDocResolutionProvider.resolveKdocFqName(useSiteSession, fqName, contextElement)
            .map { it.toResolveResult() }
            .ifNotEmpty { return this }

        if (trySyntheticGetters) {
            getSymbolsFromSyntheticProperty(fqName, contextElement).ifNotEmpty { return this }
        }

        return emptyList()
    }

    private fun KaSession.getSymbolsFromSyntheticProperty(fqName: FqName, contextElement: KtElement): Collection<ResolveResult> {
        val getterNames = possibleGetMethodNames(fqName.shortNameOrSpecial())
        return getterNames.flatMap { getterName ->
            resolveKdocFqName(fqName.parent().child(getterName), contextElement, trySyntheticGetters = false)
        }.filter {
            val symbolLocation = it.symbol.location
            val symbolOrigin = it.symbol.origin
            symbolLocation == KaSymbolLocation.CLASS && (symbolOrigin == KaSymbolOrigin.JAVA_LIBRARY || symbolOrigin == KaSymbolOrigin.JAVA_SOURCE)
        }
    }

    private fun KaSession.getExtensionReceiverSymbolByThisQualifier(
        fqName: FqName,
        contextElement: KtElement,
    ): Collection<ResolveResult> {
        val owner = contextElement.parentOfType<KtDeclaration>(withSelf = true) ?: return emptyList()
        if (fqName.pathSegments().singleOrNull()?.asString() == "this") {
            if (owner is KtCallableDeclaration && owner.receiverTypeReference != null) {
                val symbol = owner.symbol as? KaCallableSymbol ?: return emptyList()
                return listOfNotNull(symbol.receiverParameter?.toResolveResult())
            }
        }
        return emptyList()
    }


    /**
     * Returns a sequence containing collections of gathered symbols.
     * Each collection represents one symbol scope,
     * coming from the closest one to [contextElement] to the farthest one.
     * Every collection in the resulting sequence is guaranteed to be non-empty.
     *
     * @param fqName - The name of the symbol to search for
     * @param contextElement - The name of the element to start search from
     * @return The sequence containing suitable [KaSymbol] found in different scopes
     */
    private fun KaSession.getSymbolsFromScopes(
        fqName: FqName,
        contextElement: KtElement
    ): Sequence<Collection<KaSymbol>> =
        sequence {
            getSymbolsFromParentMemberScopes(fqName, contextElement).ifNotEmpty { yield(this) }

            val importScopeContext = contextElement.containingKtFile.importingScopeContext
            getSymbolsFromImportingScope(
                importScopeContext,
                fqName,
                KaScopeKind.ExplicitSimpleImportingScope::class,
            ).ifNotEmpty { yield(this) }

            getSymbolsFromPackageScope(fqName, contextElement).ifNotEmpty { yield(this) }
            getSymbolsFromImportingScope(
                importScopeContext,
                fqName,
                KaScopeKind.DefaultSimpleImportingScope::class,
            ).ifNotEmpty { yield(this) }

            getSymbolsFromImportingScope(
                importScopeContext,
                fqName,
                KaScopeKind.ExplicitStarImportingScope::class,
            ).ifNotEmpty { yield(this) }

            getSymbolsFromImportingScope(
                importScopeContext,
                fqName,
                KaScopeKind.DefaultStarImportingScope::class
            ).ifNotEmpty { yield(this) }
        }


    private fun KaSession.getSymbolsFromDeclaration(name: Name, owner: KtDeclaration): List<KaSymbol> = buildList {
        if (owner is KtNamedDeclaration) {
            if (owner.nameAsName == name) {
                add(owner.symbol)
            }
        }

        owner.modifierList?.contextReceiverLists?.flatMap { it.contextParameters() }?.forEach { contextParameter ->
            if (contextParameter.nameAsName == name) {
                add(contextParameter.symbol)
            }
        }

        if (owner is KtTypeParameterListOwner) {
            for (typeParameter in owner.typeParameters) {
                if (typeParameter.nameAsName == name) {
                    add(typeParameter.symbol)
                }
            }
        }
        if (owner is KtCallableDeclaration) {
            for (typeParameter in owner.valueParameters) {
                if (typeParameter.nameAsName == name) {
                    add(typeParameter.symbol)
                }
            }
        }

        if (owner is KtClassOrObject) {
            owner.primaryConstructor?.let { addAll(getSymbolsFromDeclaration(name, it)) }
        }
    }

    /**
     * Returns the [KaSymbol]s called [fqName] found in the member scope and companion object's member scope of the [KtDeclaration]s that
     * contain the [contextElement].
     *
     * If [fqName] has two or more segments, e.g. `Foo.bar`, the member and companion object scope of the containing [KtDeclaration] will be
     * queried for a class `Foo` first, and then that class `Foo` will be queried for the member `bar` by short name.
     */
    private fun KaSession.getSymbolsFromParentMemberScopes(fqName: FqName, contextElement: KtElement): Collection<KaSymbol> {
        val declaration = PsiTreeUtil.getContextOfType(contextElement, KtDeclaration::class.java, false) ?: return emptyList()
        for (ktDeclaration in declaration.parentsOfType<KtDeclaration>(withSelf = true)) {
            if (fqName.pathSegments().size == 1) {
                getSymbolsFromDeclaration(fqName.shortName(), ktDeclaration).ifNotEmpty { return this }
            }
            if (ktDeclaration is KtClassOrObject) {
                val symbol = ktDeclaration.classSymbol ?: continue

                val scope = getCompositeCombinedMemberAndCompanionObjectScope(symbol)

                val symbolsFromScope = getSymbolsFromMemberScope(fqName, scope)
                if (symbolsFromScope.isNotEmpty()) return symbolsFromScope
            }
        }
        return emptyList()
    }

    private fun KaSession.getCompositeCombinedMemberAndCompanionObjectScope(symbol: KaDeclarationContainerSymbol): KaScope =
        listOfNotNull(
            symbol.combinedMemberScope,
            getCompanionObjectMemberScope(symbol),
        ).asCompositeScope()

    private fun KaSession.getCompanionObjectMemberScope(symbol: KaDeclarationContainerSymbol): KaScope? {
        val namedClassSymbol = symbol as? KaNamedClassSymbol ?: return null
        val companionSymbol = namedClassSymbol.companionObject ?: return null
        return companionSymbol.memberScope
    }

    private fun KaSession.getSymbolsFromPackageScope(fqName: FqName, contextElement: KtElement): Collection<KaDeclarationSymbol> {
        val containingFile = contextElement.containingKtFile
        val packageFqName = containingFile.packageFqName
        val packageSymbol = findPackage(packageFqName) ?: return emptyList()
        val packageScope = packageSymbol.packageScope
        return getSymbolsFromMemberScope(fqName, packageScope)
    }

    private fun KaSession.getSymbolsFromImportingScope(
        scopeContext: KaScopeContext,
        fqName: FqName,
        acceptScopeKind: KClass<out KaScopeKind>,
    ): Collection<KaDeclarationSymbol> {
        val importingScope = scopeContext.compositeScope { acceptScopeKind.java.isAssignableFrom(it::class.java) }
        return getSymbolsFromMemberScope(fqName, importingScope)
    }

    private fun KaSession.getSymbolsFromMemberScope(fqName: FqName, scope: KaScope): Collection<KaDeclarationSymbol> {
        val finalScope = fqName.pathSegments()
            .dropLast(1)
            .fold(scope) { currentScope, fqNamePart ->
                currentScope
                    .classifiers(fqNamePart)
                    .filterIsInstance<KaDeclarationContainerSymbol>()
                    .map { getCompositeCombinedMemberAndCompanionObjectScope(it) }
                    .toList()
                    .asCompositeScope()
            }

        return finalScope.getAllSymbolsFromScopeByShortName(fqName)
    }

    private fun KaScope.getAllSymbolsFromScopeByShortName(fqName: FqName): Collection<KaDeclarationSymbol> {
        val shortName = fqName.shortName()
        return buildSet {
            addAll(callables(shortName))
            addAll(classifiers(shortName))
        }
    }

    /**
     * Tries to resolve [fqName] into available extension callables (functions or properties)
     * prefixed with a suitable extension receiver type (like in `Foo.bar`, or `foo.Foo.bar`).
     *
     * Relies on the fact that in such references only the last qualifier refers to the
     * actual extension callable, and the part before that refers to the receiver type (either fully
     * or partially qualified).
     *
     * For example, `foo.Foo.bar` may only refer to the extension callable `bar` with
     * a `foo.Foo` receiver type, and this function will only look for such combinations.
     *
     * N.B. This function only searches for extension callables qualified by receiver types!
     * It does not try to resolve fully qualified or member functions, because they are dealt
     * with by the other parts of [KDocReferenceResolver].
     */
    private fun KaSession.getTypeQualifiedExtensions(fqName: FqName, contextElement: KtElement): Collection<ResolveResult> {
        if (fqName.isRoot) return emptyList()
        val extensionName = fqName.shortName()

        val receiverTypeName = fqName.parent()
        if (receiverTypeName.isRoot) return emptyList()

        val possibleReceiversLayers = getReceiverTypeCandidates(receiverTypeName, contextElement).toList()
        val possibleExtensionsLayers = getExtensionCallableSymbolsByShortName(extensionName, contextElement).toList()

        if (!possibleExtensionsLayers.any() || !possibleReceiversLayers.any()) return emptyList()

        return possibleReceiversLayers.first().flatMap { receiverClassSymbol ->
            val receiverType = receiverClassSymbol.defaultType
            possibleExtensionsLayers.map { extensionSymbolsLayer ->
                extensionSymbolsLayer.filter { canBeReferencedAsExtensionOn(it, receiverType) }
                    .map { it.toResolveResult(receiverClassReference = receiverClassSymbol) }
            }.firstOrNull {
                it.isNotEmpty()
            } ?: emptyList()
        }
    }

    private fun KaSession.getExtensionCallableSymbolsByShortName(
        name: Name, contextElement: KtElement
    ): Sequence<Collection<KaCallableSymbol>> =
        getSymbolsFromScopes(FqName.topLevel(name), contextElement).mapNotNull {
            it.filterIsInstance<KaCallableSymbol>().filter { symbol -> symbol.isExtension }.ifEmpty { null }
        }


    private fun KaSession.getReceiverTypeCandidates(
        receiverTypeName: FqName,
        contextElement: KtElement
    ): Sequence<Collection<KaClassLikeSymbol>> =
        getSymbolsFromScopes(receiverTypeName, contextElement).ifEmpty {
            sequence {
                getNonImportedSymbolsByFullyQualifiedName(receiverTypeName).ifNotEmpty { yield(this) }
            }
        }.mapNotNull {
            it.filterIsInstance<KaClassLikeSymbol>().ifEmpty { null }
        }


    /**
     * Returns true if we consider that [this] extension function prefixed with [actualReceiverType] in
     * a KDoc reference should be considered as legal and resolved, and false otherwise.
     */
    private fun KaSession.canBeReferencedAsExtensionOn(symbol: KaCallableSymbol, actualReceiverType: KaType): Boolean {
        val extensionReceiverType = symbol.receiverParameter?.returnType ?: return false
        return isPossiblySubTypeOf(actualReceiverType, extensionReceiverType, hashSetOf())
    }

    private fun KaSession.isPossiblySubTypeOf(
        actualReceiverType: KaType,
        expectedReceiverType: KaType,
        visited: HashSet<Pair<KaType, KaType>>
    ): Boolean {
        val actualToExpectedPair = actualReceiverType to expectedReceiverType

        if (!visited.add(actualToExpectedPair)) {
            return true
        }

        if (expectedReceiverType is KaTypeParameterType) {
            return expectedReceiverType.symbol.upperBounds.all { isPossiblySubTypeOf(actualReceiverType, it, visited) }.also {
                visited.remove(actualToExpectedPair)
            }
        }

        return (actualReceiverType.isSubtypeOf(expectedReceiverType) || isSubtypeOfWithTypeParams(
            actualReceiverType,
            expectedReceiverType,
            visited
        )).also {
            visited.remove(actualToExpectedPair)
        }
    }


    /**
     * Performs a subtyping check of two types with respect to their type parameters.
     *
     * ```kotlin
     * interface T<A_0, A_1, ..., A_TN>
     *
     * class S<B_0, B_1, ..., B_SN> : T<...>
     *
     * fun T<E_0, E_1, ..., E_I>.extension() { }
     *
     * /** [S.extension] */
     * fun documented() { }
     * ```
     *
     * We check that `S.extension` is a valid reference with the following algorithm:
     *
     * - From `S<B_0, B_1, ..., B_M>`, derive the supertype instantiation `T<Z_0, Z_1, ..., Z_K>` according to the inheritance relationship.
     *      * Any `Z_x` will either be a type parameter type from `S<B_0, B_1, ..., B_M>`,
     *      or a concrete type from a regular type argument along the way to the supertype.
     *
     * - Compare `T<Z_0, Z_1, ..., Z_K>` against `T<E_0, E_1, ..., E_I>`,
     * finding whether each type argument `E_x` matches with the type `Z_x`:
     *      * If both `Z_x` and `E_x` are concrete types,
     *      check that `Z_x` is a subtype of `E_x` according to the variance of the type parameter at the position.
     *      It's important to note that if the variance in the base type is INVARIANT,
     *      the extension can provide its own variance for the corresponding type parameter.
     *      ```kotlin
     *      interface A<T> // Check variance here first
     *
     *      class B: A<Int>
     *
     *      fun A<in Nothing>.foo() {} // If the original variance is INVARIANT, check variance here
     *
     *      fun main() {
     *          B().foo() // Wouldn't be possible without the variance from the extension
     *      }
     *      ```
     *
     *      * If only `E_x` is a concrete type (and vice versa), check that `E_x` fits into the bounds of `Z_x`.
     *      If `E_x` does not fit the bounds of `Z_x`, we cannot find an instantiation of `Z_x` which satisfies `E_x`.
     *
     *      * If both types are type parameter types, check that the bounds of `Z_x` and `E_x` overlap.
     *      That is, there must be at least one type which can be an instantiation of both `Z_x` and `E_x`.
     *
     *      * Unless we are dealing with concrete types on both sides, covariance and contravariance do not need to be taken into account.
     *      Variance is concerned with subtyping of two types when their type arguments have concrete instantiations
     *      (e.g. `Type<Cat>` <: `Type<Animal>`),
     *      but here we are concerned with finding a common instantiation of two type parameters (e.g. `Type<X>` and `Type<Y>`).
     *      As long as the bounds overlap, we can instantiate both type parameters to the same type argument,
     *      making the variance unimportant (all type parameters could be invariant).
     */
    private fun KaSession.isSubtypeOfWithTypeParams(
        actualReceiverType: KaType,
        expectedExtensionType: KaType,
        visited: HashSet<Pair<KaType, KaType>>
    ): Boolean {
        if (expectedExtensionType !is KaClassType || expectedExtensionType.typeArguments.none()) {
            return false
        }

        val actualSupertypesWithSelf = actualReceiverType.allSupertypes(shouldApproximate = true) + actualReceiverType
        // Search for supertypes instantiations, which correspond to the expected receiver type
        val compatibleSupertypesOfActualReceiverType =
            actualSupertypesWithSelf
                .filter { it.symbol == expectedExtensionType.symbol }
                .filterIsInstance<KaClassType>()
                .toList().ifEmpty { return false }

        return compatibleSupertypesOfActualReceiverType.all { compatibleSupertype ->
            // Default type parameters of the supertype
            val compatibleTypeSymbolTypeParameters = compatibleSupertype.symbol.typeParameters

            // Matching default type parameters with pairs of arguments from the actual type and from the receiver type
            val typeArgumentsToMatch =
                compatibleTypeSymbolTypeParameters.zip(compatibleSupertype.typeArguments.zip(expectedExtensionType.typeArguments))

            typeArgumentsToMatch.all { (originalTypeParameter, actualToExpectedTypeArguments) ->
                val (actualTypeArgument, extensionTypeArgument) = actualToExpectedTypeArguments

                // It implies that the current extension type argument is `KaStarTypeProjection`,
                // so it can be substituted with any actual type argument
                if (extensionTypeArgument is KaStarTypeProjection) {
                    return true
                }

                // Star projections can't be used in the argument lists of receiver types
                if (actualTypeArgument is KaStarTypeProjection) {
                    return false
                }

                if (extensionTypeArgument !is KaTypeArgumentWithVariance || actualTypeArgument !is KaTypeArgumentWithVariance) {
                    return false
                }

                val actualTypeArgumentType = actualTypeArgument.type
                val extensionTypeArgumentType = extensionTypeArgument.type

                when (actualTypeArgumentType) {
                    // Both are concrete types
                    !is KaTypeParameterType if extensionTypeArgumentType !is KaTypeParameterType ->
                        // Checking the default variance of the corresponding supertype parameter
                        when (originalTypeParameter.variance) {
                            Variance.INVARIANT -> when (extensionTypeArgument.variance) {
                                // Checking if there is any variance provided by the extension
                                Variance.INVARIANT -> actualTypeArgumentType == extensionTypeArgumentType
                                Variance.OUT_VARIANCE -> isPossiblySubTypeOf(actualTypeArgumentType, extensionTypeArgumentType, visited)
                                Variance.IN_VARIANCE -> isPossiblySubTypeOf(extensionTypeArgumentType, actualTypeArgumentType, visited)
                            }
                            Variance.OUT_VARIANCE -> isPossiblySubTypeOf(actualTypeArgumentType, extensionTypeArgumentType, visited)
                            Variance.IN_VARIANCE -> isPossiblySubTypeOf(extensionTypeArgumentType, actualTypeArgumentType, visited)
                        }
                    // Both are type parameters -> bound checking
                    is KaTypeParameterType if extensionTypeArgumentType is KaTypeParameterType -> actualTypeArgumentType.hasCommonSubtypeWith(
                        extensionTypeArgumentType
                    )
                    // Actual argument is a type parameter, expected extension argument is concrete
                    is KaTypeParameterType -> isPossiblySubTypeOf(
                        extensionTypeArgumentType,
                        actualTypeArgumentType,
                        visited
                    )
                    // Actual type is concrete, expected extension argument is a type parameter
                    else -> isPossiblySubTypeOf(
                        actualTypeArgumentType,
                        extensionTypeArgumentType,
                        visited
                    )
                }
            }
        }
    }

    private fun KaSession.getNonImportedSymbolsByFullyQualifiedName(fqName: FqName): Collection<KaSymbol> = buildSet {
        generateNameInterpretations(fqName).forEach { interpretation ->
            collectSymbolsByFqNameInterpretation(interpretation, this@buildSet)
        }
    }

    private fun KaSession.collectSymbolsByFqNameInterpretation(
        interpretation: FqNameInterpretation,
        consumer: MutableCollection<KaSymbol>,
    ) {
        when (interpretation) {
            is FqNameInterpretation.FqNameInterpretationAsCallableId -> {
                collectSymbolsByFqNameInterpretationAsCallableId(interpretation.callableId, consumer)
            }

            is FqNameInterpretation.FqNameInterpretationAsClassId -> {
                collectSymbolsByClassId(interpretation.classId, consumer)
            }

            is FqNameInterpretation.FqNameInterpretationAsPackage -> {
                collectSymbolsByPackage(interpretation.packageFqName, consumer)
            }
        }
    }

    private fun KaSession.collectSymbolsByPackage(packageFqName: FqName, consumer: MutableCollection<KaSymbol>) {
        val symbol = findPackage(packageFqName)
        consumer.addIfNotNull(symbol)
    }

    private fun KaSession.collectSymbolsByClassId(classId: ClassId, consumer: MutableCollection<KaSymbol>) {
        val symbol = findClassLike(classId)
        consumer.addIfNotNull(symbol)
    }

    private fun KaSession.collectSymbolsByFqNameInterpretationAsCallableId(
        callableId: CallableId,
        consumer: MutableCollection<KaSymbol>,
    ) {
        when (val classId = callableId.classId) {
            null -> {
                consumer.addAll(findTopLevelCallables(callableId.packageName, callableId.callableName))
            }

            else -> {
                val symbol = findClass(classId)
                if (symbol != null) {
                    val scope = getCompositeCombinedMemberAndCompanionObjectScope(symbol)
                    consumer.addAll(scope.callables(callableId.callableName))
                }
            }
        }
    }


    private fun generateNameInterpretations(fqName: FqName): Sequence<FqNameInterpretation> = sequence {
        val parts = fqName.pathSegments()
        if (parts.isEmpty()) {
            yield(FqNameInterpretation.create(packageParts = emptyList(), classParts = emptyList(), callable = null))
            return@sequence
        }
        for (lastPackagePartIndexExclusive in 0..parts.size) {
            yield(
                FqNameInterpretation.create(
                    packageParts = parts.subList(0, lastPackagePartIndexExclusive),
                    classParts = parts.subList(lastPackagePartIndexExclusive, parts.size),
                    callable = null,
                )
            )

            if (lastPackagePartIndexExclusive <= parts.size - 1) {
                yield(
                    FqNameInterpretation.create(
                        packageParts = parts.subList(0, lastPackagePartIndexExclusive),
                        classParts = parts.subList(lastPackagePartIndexExclusive, parts.size - 1),
                        callable = parts.last(),
                    )
                )
            }
        }
    }
}

private sealed class FqNameInterpretation {

    data class FqNameInterpretationAsPackage(val packageFqName: FqName) : FqNameInterpretation()
    data class FqNameInterpretationAsClassId(val classId: ClassId) : FqNameInterpretation()
    data class FqNameInterpretationAsCallableId(val callableId: CallableId) : FqNameInterpretation()

    companion object {
        fun create(packageParts: List<Name>, classParts: List<Name>, callable: Name?): FqNameInterpretation {
            val packageName = FqName.fromSegments(packageParts.map { it.asString() })
            val relativeClassName = FqName.fromSegments(classParts.map { it.asString() })

            return when {
                classParts.isEmpty() && callable == null -> FqNameInterpretationAsPackage(packageName)
                callable == null -> FqNameInterpretationAsClassId(ClassId(packageName, relativeClassName, isLocal = false))
                else -> FqNameInterpretationAsCallableId(CallableId(packageName, relativeClassName.takeUnless { it.isRoot }, callable))
            }
        }
    }
}
