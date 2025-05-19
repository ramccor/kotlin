/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.components

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiver
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.Variance

public interface KaTypeCreator : KaSessionComponent {
    /**
     * Builds a class type with the given class ID.
     *
     * A generic class type can be built by providing type arguments using the [init] block.
     * The caller is supposed to provide the correct number of type arguments for the class.
     *
     * For Kotlin built-in types, consider using the overload that accepts a [KaClassLikeSymbol] instead:
     * `buildClassType(builtinTypes.string)`.
     *
     *  #### Example
     *
     * ```kotlin
     * buildClassType(ClassId.fromString("kotlin/collections/List")) {
     *     argument(buildClassType(ClassId.fromString("kotlin/String")))
     * }
     * ```
     */
    public fun buildClassType(classId: ClassId, init: KaClassTypeBuilder.() -> Unit = {}): KaType

    /**
     * Builds a class type with the given class symbol.
     *
     * A generic class type can be built by providing type arguments using the [init] block.
     * The caller is supposed to provide the correct number of type arguments for the class.
     *
     * #### Example
     *
     * ```kotlin
     * buildClassType(builtinTypes.string)
     * ```
     */
    public fun buildClassType(symbol: KaClassLikeSymbol, init: KaClassTypeBuilder.() -> Unit = {}): KaType

    /**
     * Builds a [KaTypeParameterType] with the given type parameter symbol.
     */
    public fun buildTypeParameterType(symbol: KaTypeParameterSymbol, init: KaTypeParameterTypeBuilder.() -> Unit = {}): KaTypeParameterType

    /**
     * Builds a [KaCapturedType] based on the given [type].
     */
    public fun buildCapturedType(type: KaCapturedType, init: KaCapturedTypeBuilder.() -> Unit = {}): KaCapturedType

    /**
     * Builds a [KaCapturedType] with the given [projection].
     */
    public fun buildCapturedType(projection: KaTypeProjection, init: KaCapturedTypeBuilder.() -> Unit = {}): KaCapturedType

    /**
     * Builds a [KaDefinitelyNotNullType] wrapping the given [type].
     */
    public fun buildDefinitelyNotNullType(
        type: KaType,
        init: KaDefinitelyNotNullTypeBuilder.() -> Unit = {}
    ): KaDefinitelyNotNullType

    /**
     * Builds a [KaFlexibleType] based on the given [type].
     */
    public fun buildFlexibleType(type: KaFlexibleType, init: KaFlexibleTypeBuilder.() -> Unit = {}): KaFlexibleType

    /**
     * Builds a [KaFlexibleType] with the given [lowerBound] and [upperBound] bounds.
     *
     * The caller is supposed to provide a correct pair of bounds, i.e., the lower bound must be a subtype of the upper bound.
     */
    public fun buildFlexibleType(lowerBound: KaType, upperBound: KaType, init: KaFlexibleTypeBuilder.() -> Unit = {}): KaFlexibleType

    /**
     * Builds an [KaIntersectionType] based on the given [type].
     */
    public fun buildIntersectionType(type: KaIntersectionType, init: KaIntersectionTypeBuilder.() -> Unit = {}): KaIntersectionType

    /**
     * Builds an [KaIntersectionType] with the provided [conjuncts].
     */
    public fun buildIntersectionType(conjuncts: List<KaType>, init: KaIntersectionTypeBuilder.() -> Unit = {}): KaIntersectionType

    /**
     * Builds a [KaDynamicType].
     */
    public fun buildDynamicType(): KaDynamicType

    /**
     * Builds a [KaFunctionType] based on the given [classId].
     *
     * Note that the given [classId] must not contain the number of parameters.
     * The number of parameters is inferred from parameter properties in [KaFunctionTypeBuilder].
     *
     * I.e. for constructing `kotlin.Function3` the [classId] should be `kotlin.Function`,
     * then the caller must provide 3 type parameters.
     */
    public fun buildFunctionType(classId: ClassId, init: KaFunctionTypeBuilder.() -> Unit): KaType

    /**
     * Builds a [KaStarTypeProjection] (`*`).
     */
    @KaExperimentalApi
    public fun buildStarTypeProjection(): KaStarTypeProjection
}

public interface KaTypeBuilder : KaLifetimeOwner

/**
 * A builder for class types.
 *
 * @see KaTypeCreator.buildClassType
 */
public interface KaClassTypeBuilder : KaTypeBuilder {
    /**
     * Default value: [KaTypeNullability.NON_NULLABLE].
     */
    public var nullability: KaTypeNullability

    public val arguments: List<KaTypeProjection>

    /**
     * Adds a type projection as an [argument] to the class type.
     */
    public fun argument(argument: KaTypeProjection)

    /**
     * Adds a [type] argument to the class type, with the given [variance].
     */
    public fun argument(type: KaType, variance: Variance = Variance.INVARIANT)
}

/**
 * A builder for type parameter types.
 *
 * @see KaTypeCreator.buildTypeParameterType
 */
public interface KaTypeParameterTypeBuilder : KaTypeBuilder {
    /**
     * Default value: [KaTypeNullability.NULLABLE].
     */
    public var nullability: KaTypeNullability
}

/**
 * A builder for captured types.
 *
 * @see KaTypeCreator.buildCapturedType
 */
public interface KaCapturedTypeBuilder : KaTypeBuilder {
    /**
     * Default value: [KaTypeNullability.NON_NULLABLE].
     */
    public var nullability: KaTypeNullability

    /**
     * The source type argument of the captured type.
     */
    public var projection: KaTypeProjection
}

/**
 * A builder for definitely-not-null types.
 *
 * @see KaTypeCreator.buildDefinitelyNotNullType
 */
public interface KaDefinitelyNotNullTypeBuilder : KaTypeBuilder {
    /**
     * Represents the wrapped type.
     */
    public var original: KaType
}

/**
 * A builder for flexible types.
 *
 * @see KaTypeCreator.buildFlexibleType
 */
public interface KaFlexibleTypeBuilder : KaTypeBuilder {
    /**
     * Default value: [KaTypeNullability.NON_NULLABLE].
     */
    public var nullability: KaTypeNullability

    /**
     * The lower bound, such as `String` in `String!`.
     */
    public var lowerBound: KaType

    /**
     * The upper bound, such as `String?` in `String!`.
     */
    public var upperBound: KaType
}

/**
 * A builder for intersection types.
 *
 * @see KaTypeCreator.buildIntersectionType
 */
public interface KaIntersectionTypeBuilder : KaTypeBuilder {
    /**
     * A list of individual types participating in the intersection.
     */
    public val conjuncts: List<KaType>

    /**
     * Adds a conjunct to the [conjuncts] list.
     */
    public fun conjunct(conjunct: KaType)
}

/**
 * A builder for function types.
 *
 * @see KaTypeCreator.buildFunctionType
 */
public interface KaFunctionTypeBuilder : KaTypeBuilder {
    /**
     * Default value: [KaTypeNullability.NON_NULLABLE].
     */
    public var nullability: KaTypeNullability

    /**
     * List of context receivers for the function type.
     *
     * @see KaFunctionType.contextReceivers
     */
    @OptIn(KaExperimentalApi::class)
    public val contextParameters: List<KaContextReceiver>

    /**
     * Adds the given [contextParameter] to the [contextParameters] list.
     */
    @OptIn(KaExperimentalApi::class)
    public fun contextParameter(contextParameter: KaContextReceiver)

    /**
     * Function receiver type.
     *
     * @see KaFunctionType.receiverType
     */
    public val receiverType: KaType?

    /**
     * Function value parameters.
     *
     * @see KaFunctionType.parameters
     */
    @KaExperimentalApi
    public val parameters: List<KaFunctionValueParameter>

    /**
     * Adds the given [parameter] to the [parameters] list.
     */
    @KaExperimentalApi
    public fun parameter(parameter: KaFunctionValueParameter)

    /**
     * Function return type.
     *
     * @see KaFunctionType.returnType
     */
    public val returnType: KaType
}