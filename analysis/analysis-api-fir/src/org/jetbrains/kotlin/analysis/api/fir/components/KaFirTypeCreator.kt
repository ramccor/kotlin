/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components

import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.fir.KaFirSession
import org.jetbrains.kotlin.analysis.api.fir.utils.firSymbol
import org.jetbrains.kotlin.analysis.api.impl.base.components.*
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.fir.resolve.diagnostics.ConeUnresolvedSymbolError
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.withParameterNameAnnotation
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.utils.exceptions.withConeTypeEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.model.CaptureStatus
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

internal class KaFirTypeCreator(
    override val analysisSessionProvider: () -> KaFirSession,
) : KaBaseTypeCreator<KaFirSession>(), KaFirSessionComponent {
    override fun buildClassType(classId: ClassId, init: KaClassTypeBuilder.() -> Unit): KaType = withValidityAssertion {
        return buildClassType(KaBaseClassTypeBuilder.ByClassId(classId, token).apply(init))
    }

    override fun buildClassType(symbol: KaClassLikeSymbol, init: KaClassTypeBuilder.() -> Unit): KaType = withValidityAssertion {
        return buildClassType(KaBaseClassTypeBuilder.BySymbol(symbol, token).apply(init))
    }

    private fun buildClassType(builder: KaBaseClassTypeBuilder): KaType {
        val lookupTag = when (builder) {
            is KaBaseClassTypeBuilder.ByClassId -> {
                val classSymbol = rootModuleSession.symbolProvider.getClassLikeSymbolByClassId(builder.classId)
                    ?: return ConeErrorType(ConeUnresolvedSymbolError(builder.classId)).asKtType()
                classSymbol.toLookupTag()
            }
            is KaBaseClassTypeBuilder.BySymbol -> {
                val symbol = builder.symbol
                symbol.classId?.toLookupTag() ?: symbol.firSymbol.toLookupTag()
            }
        }

        val typeContext = rootModuleSession.typeContext
        val coneType = typeContext.createSimpleType(
            lookupTag,
            builder.arguments.map { it.coneTypeProjection },
            builder.nullability.isNullable
        ) as ConeClassLikeType

        return coneType.asKtType()
    }

    override fun buildTypeParameterType(symbol: KaTypeParameterSymbol, init: KaTypeParameterTypeBuilder.() -> Unit): KaTypeParameterType {
        withValidityAssertion {
            val builder = KaBaseTypeParameterTypeBuilder.BySymbol(symbol, token).apply(init)
            val firSymbol = builder.symbol.firSymbol
            val coneType = ConeTypeParameterTypeImpl(
                lookupTag = firSymbol.toConeType().lookupTag,
                isMarkedNullable = builder.nullability.isNullable
            )
            return coneType.asKtType() as KaTypeParameterType
        }
    }

    override fun buildCapturedType(
        type: KaCapturedType,
        init: KaCapturedTypeBuilder.() -> Unit,
    ): KaCapturedType {
        withValidityAssertion {
            val builder = KaBaseCapturedTypeBuilder.ByType(type, token).apply(init)
            return buildCapturedType(builder)
        }
    }

    override fun buildCapturedType(
        projection: KaTypeProjection,
        init: KaCapturedTypeBuilder.() -> Unit,
    ): KaCapturedType {
        withValidityAssertion {
            val builder = KaBaseCapturedTypeBuilder.ByProjection(projection, token).apply(init)
            return buildCapturedType(builder)
        }
    }

    private fun buildCapturedType(builder: KaCapturedTypeBuilder): KaCapturedType {
        withValidityAssertion {
            return ConeCapturedType(
                captureStatus = CaptureStatus.FROM_EXPRESSION,
                lowerType = null,
                isMarkedNullable = builder.nullability.isNullable,
                constructor = ConeCapturedTypeConstructor(
                    builder.projection.coneTypeProjection,
                )
            ).asKtType() as KaCapturedType
        }
    }

    override fun buildDefinitelyNotNullType(
        type: KaType,
        init: KaDefinitelyNotNullTypeBuilder.() -> Unit,
    ): KaDefinitelyNotNullType {
        withValidityAssertion {
            val builder = KaBaseDefinitelyNotNullTypeBuilder.ByType(type, token).apply(init)
            val type = builder.original

            return when (type) {
                is KaDefinitelyNotNullType -> type
                is KaFlexibleType -> errorWithAttachment("Flexible types cannot not be wrapped into `KaDefinitelyNotNullType`") {
                    withEntry("Flexible type", type.toString())
                }
                else -> {
                    val coneType = type.coneType as ConeSimpleKotlinType
                    val definitelyNotNullConeType = ConeDefinitelyNotNullType(coneType)
                    definitelyNotNullConeType.asKtType() as KaDefinitelyNotNullType
                }
            }
        }
    }

    override fun buildFlexibleType(
        type: KaFlexibleType,
        init: KaFlexibleTypeBuilder.() -> Unit,
    ): KaFlexibleType {
        withValidityAssertion {
            val builder = KaBaseFlexibleTypeBuilder.ByFlexibleType(type, token).apply(init)
            return buildFlexibleType(builder)
        }
    }

    override fun buildFlexibleType(
        lowerBound: KaType,
        upperBound: KaType,
        init: KaFlexibleTypeBuilder.() -> Unit,
    ): KaFlexibleType {
        withValidityAssertion {
            val builder = KaBaseFlexibleTypeBuilder.ByBounds(lowerBound, upperBound, token).apply(init)
            return buildFlexibleType(builder)
        }
    }

    private fun buildFlexibleType(builder: KaFlexibleTypeBuilder): KaFlexibleType {
        withValidityAssertion {
            val lowerBound = builder.lowerBound.coneType.lowerBoundIfFlexible()
            val upperBound = builder.upperBound.coneType.upperBoundIfFlexible()

            if (!lowerBound.isSubtypeOf(upperBound, rootModuleSession, true)) {
                errorWithAttachment("Lower bound must be a subtype of upper bound") {
                    withConeTypeEntry("lowerBound", lowerBound)
                    withConeTypeEntry("upperBound", upperBound)
                }
            }

            val coneType = typeContext.createFlexibleType(
                builder.lowerBound.coneType.lowerBoundIfFlexible(),
                builder.upperBound.coneType.upperBoundIfFlexible(),
            ) as ConeKotlinType
            return coneType.asKtType() as KaFlexibleType
        }
    }

    override fun buildIntersectionType(
        type: KaIntersectionType,
        init: KaIntersectionTypeBuilder.() -> Unit,
    ): KaIntersectionType {
        withValidityAssertion {
            val builder = KaBaseIntersectionTypeBuilder.ByIntersectionType(type, token).apply(init)
            return buildIntersectionType(builder)
        }
    }

    override fun buildIntersectionType(
        conjuncts: List<KaType>,
        init: KaIntersectionTypeBuilder.() -> Unit,
    ): KaIntersectionType {
        withValidityAssertion {
            val builder = KaBaseIntersectionTypeBuilder.ByConjuncts(conjuncts, token).apply(init)
            return buildIntersectionType(builder)
        }
    }

    private fun buildIntersectionType(builder: KaIntersectionTypeBuilder): KaIntersectionType {
        withValidityAssertion {
            val coneType = ConeIntersectionType(builder.conjuncts.map { it.coneType })
            return coneType.asKtType() as KaIntersectionType
        }
    }

    override fun buildDynamicType(): KaDynamicType {
        withValidityAssertion {
            val coneType = ConeDynamicType.create(rootModuleSession)
            return coneType.asKtType() as KaDynamicType
        }
    }

    override fun buildFunctionType(
        classId: ClassId,
        init: KaFunctionTypeBuilder.() -> Unit,
    ): KaType {
        withValidityAssertion {
            return buildFunctionType(KaBaseFunctionTypeBuilder.ByClassId(classId, token, analysisSession).apply(init))
        }
    }

    private fun buildFunctionType(builder: KaBaseFunctionTypeBuilder): KaType {
        val lookupTag = when (builder) {
            is KaBaseFunctionTypeBuilder.ByClassId -> {
                val builderClassId = builder.classId
                val numberOfParameters =
                    builder.contextParameters.size + (if (builder.receiverType != null) 1 else 0) + builder.parameters.size
                val functionNameWithParameterNumber = builderClassId.shortClassName.asString() + numberOfParameters
                val updatedClassId = ClassId(builderClassId.packageFqName, Name.identifier(functionNameWithParameterNumber))
                val classSymbol = rootModuleSession.symbolProvider.getClassLikeSymbolByClassId(updatedClassId)
                    ?: return ConeErrorType(ConeUnresolvedSymbolError(updatedClassId)).asKtType()
                classSymbol.toLookupTag()
            }
        }

        val typeArguments = buildList {
            addAll(builder.contextParameters.map { it.type.coneType })
            addIfNotNull(builder.receiverType?.coneType)
            addAll(builder.parameters.map { valueParameter ->
                val parameterConeType = valueParameter.type.coneType
                valueParameter.name?.let { name ->
                    parameterConeType.withParameterNameAnnotation(
                        name = name,
                        element = null
                    )
                } ?: parameterConeType
            })
            add(builder.returnType.coneType)
        }


        val typeContext = rootModuleSession.typeContext
        val coneType = typeContext.createSimpleType(
            constructor = lookupTag,
            arguments = typeArguments,
            nullable = builder.nullability.isNullable,
            isExtensionFunction = builder.receiverType != null,
            attributes = listOf(CompilerConeAttributes.ContextFunctionTypeParams(builder.contextParameters.size))
        ) as ConeClassLikeType

        return coneType.asKtType()
    }
}