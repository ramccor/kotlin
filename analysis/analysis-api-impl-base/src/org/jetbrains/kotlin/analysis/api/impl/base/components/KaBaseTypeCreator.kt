/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.components

import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.impl.base.types.KaBaseStarTypeProjection
import org.jetbrains.kotlin.analysis.api.impl.base.types.KaBaseTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.validityAsserted
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.Variance

@KaImplementationDetail
abstract class KaBaseTypeCreator<T : KaSession> : KaBaseSessionComponent<T>(), KaTypeCreator {
    override fun buildStarTypeProjection(): KaStarTypeProjection = KaBaseStarTypeProjection(token)
}

@KaImplementationDetail
sealed class KaBaseClassTypeBuilder : KaClassTypeBuilder {
    private val backingArguments = mutableListOf<KaTypeProjection>()

    override var nullability: KaTypeNullability = KaTypeNullability.NON_NULLABLE
        get() = withValidityAssertion { field }
        set(value) {
            withValidityAssertion { field = value }
        }

    override val arguments: List<KaTypeProjection> get() = withValidityAssertion { backingArguments }

    override fun argument(argument: KaTypeProjection): Unit = withValidityAssertion {
        backingArguments += argument
    }

    override fun argument(type: KaType, variance: Variance): Unit = withValidityAssertion {
        backingArguments += KaBaseTypeArgumentWithVariance(type, variance, type.token)
    }

    class ByClassId(classId: ClassId, override val token: KaLifetimeToken) : KaBaseClassTypeBuilder() {
        val classId: ClassId by validityAsserted(classId)
    }

    class BySymbol(symbol: KaClassLikeSymbol, override val token: KaLifetimeToken) : KaBaseClassTypeBuilder() {
        val symbol: KaClassLikeSymbol by validityAsserted(symbol)
    }
}

@KaImplementationDetail
sealed class KaBaseTypeParameterTypeBuilder : KaTypeParameterTypeBuilder {
    override var nullability: KaTypeNullability = KaTypeNullability.NULLABLE
        get() = withValidityAssertion { field }
        set(value) {
            withValidityAssertion { field = value }
        }

    class BySymbol(symbol: KaTypeParameterSymbol, override val token: KaLifetimeToken) : KaBaseTypeParameterTypeBuilder() {
        val symbol: KaTypeParameterSymbol by validityAsserted(symbol)
    }
}

@KaImplementationDetail
sealed class KaBaseCapturedTypeBuilder(
    baseProjection: KaTypeProjection,
    baseNullability: KaTypeNullability = KaTypeNullability.NON_NULLABLE
) : KaCapturedTypeBuilder {
    override var nullability: KaTypeNullability = baseNullability
        get() = withValidityAssertion { field }
        set(value) {
            withValidityAssertion { field = value }
        }

    override var projection: KaTypeProjection = baseProjection
        get() = withValidityAssertion { field }
        set(value) {
            withValidityAssertion { field = value }
        }

    class ByType(type: KaCapturedType, override val token: KaLifetimeToken) : KaBaseCapturedTypeBuilder(type.projection, type.nullability)
    class ByProjection(projection: KaTypeProjection, override val token: KaLifetimeToken) : KaBaseCapturedTypeBuilder(projection)
}

@KaImplementationDetail
sealed class KaBaseDefinitelyNotNullTypeBuilder : KaDefinitelyNotNullTypeBuilder {
    class ByType(type: KaType, override val token: KaLifetimeToken) : KaBaseDefinitelyNotNullTypeBuilder() {
        override var original = type
            get() = withValidityAssertion { field }
            set(value) {
                withValidityAssertion { field = value }
            }
    }
}

@KaImplementationDetail
sealed class KaBaseFlexibleTypeBuilder(lowerBound: KaType, upperBound: KaType) : KaFlexibleTypeBuilder {
    override var nullability: KaTypeNullability = KaTypeNullability.NON_NULLABLE
        get() = withValidityAssertion { field }
        set(value) {
            withValidityAssertion { field = value }
        }

    override var lowerBound: KaType = lowerBound
        get() = withValidityAssertion { field }
        set(value) {
            withValidityAssertion { field = value }
        }

    override var upperBound: KaType = upperBound
        get() = withValidityAssertion { field }
        set(value) {
            withValidityAssertion { field = value }
        }

    class ByFlexibleType(type: KaFlexibleType, override val token: KaLifetimeToken) :
        KaBaseFlexibleTypeBuilder(type.lowerBound, type.upperBound)

    class ByBounds(lowerBound: KaType, upperBound: KaType, override val token: KaLifetimeToken) :
        KaBaseFlexibleTypeBuilder(lowerBound, upperBound)
}

@KaImplementationDetail
sealed class KaBaseIntersectionTypeBuilder(private val backingConjuncts: MutableList<KaType> = mutableListOf()) :
    KaIntersectionTypeBuilder {

    override val conjuncts: List<KaType> get() = withValidityAssertion { backingConjuncts }

    override fun conjunct(conjunct: KaType): Unit = withValidityAssertion {
        backingConjuncts += conjunct
    }

    class ByIntersectionType(type: KaIntersectionType, override val token: KaLifetimeToken) :
        KaBaseIntersectionTypeBuilder(type.conjuncts.toMutableList())

    class ByConjuncts(conjuncts: List<KaType>, override val token: KaLifetimeToken) :
        KaBaseIntersectionTypeBuilder(conjuncts.toMutableList())
}
