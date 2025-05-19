/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.descriptors.KaFe10Session
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.KaFe10SessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.getSymbolDescriptor
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtType
import org.jetbrains.kotlin.analysis.api.descriptors.types.KaFe10ClassErrorType
import org.jetbrains.kotlin.analysis.api.descriptors.types.KaFe10UsualClassType
import org.jetbrains.kotlin.analysis.api.descriptors.types.base.KaFe10Type
import org.jetbrains.kotlin.analysis.api.impl.base.components.*
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.annotations.BuiltInAnnotationDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.resolve.calls.inference.CapturedType
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.error.ErrorTypeKind
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.typeUtil.replaceAnnotations
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal class KaFe10TypeCreator(
    override val analysisSessionProvider: () -> KaFe10Session
) : KaBaseTypeCreator<KaFe10Session>(), KaFe10SessionComponent {
    override fun buildClassType(classId: ClassId, init: KaClassTypeBuilder.() -> Unit): KaType = withValidityAssertion {
        return buildClassType(KaBaseClassTypeBuilder.ByClassId(classId, token).apply(init))
    }

    override fun buildClassType(symbol: KaClassLikeSymbol, init: KaClassTypeBuilder.() -> Unit): KaType = withValidityAssertion {
        return buildClassType(KaBaseClassTypeBuilder.BySymbol(symbol, token).apply(init))
    }

    private fun buildClassType(builder: KaBaseClassTypeBuilder): KaType {
        val descriptor: ClassDescriptor? = when (builder) {
            is KaBaseClassTypeBuilder.ByClassId -> {
                val fqName = builder.classId.asSingleFqName()
                analysisContext.resolveSession
                    .getTopLevelClassifierDescriptors(fqName, NoLookupLocation.FROM_IDE)
                    .firstIsInstanceOrNull()
            }
            is KaBaseClassTypeBuilder.BySymbol -> {
                getSymbolDescriptor(builder.symbol) as? ClassDescriptor
            }
        }

        if (descriptor == null) {
            val name = when (builder) {
                is KaBaseClassTypeBuilder.ByClassId -> builder.classId.asString()
                is KaBaseClassTypeBuilder.BySymbol ->
                    builder.symbol.classId?.asString()
                        ?: builder.symbol.name?.asString()
                        ?: SpecialNames.ANONYMOUS_STRING
            }
            val kotlinType = ErrorUtils.createErrorType(ErrorTypeKind.UNRESOLVED_CLASS_TYPE, name)
            return KaFe10ClassErrorType(kotlinType, analysisContext)
        }

        val typeParameters = descriptor.typeConstructor.parameters
        val type = if (typeParameters.size == builder.arguments.size) {
            val projections = builder.arguments.mapIndexed { index, arg ->
                when (arg) {
                    is KaStarTypeProjection -> StarProjectionImpl(typeParameters[index])
                    is KaTypeArgumentWithVariance -> TypeProjectionImpl(arg.variance, (arg.type as KaFe10Type).fe10Type)
                }
            }

            TypeUtils.substituteProjectionsForParameters(descriptor, projections)
        } else {
            descriptor.defaultType
        }

        val typeWithNullability = TypeUtils.makeNullableAsSpecified(type, builder.nullability == KaTypeNullability.NULLABLE)
        return KaFe10UsualClassType(typeWithNullability as SimpleType, descriptor, analysisContext)
    }

    override fun buildTypeParameterType(symbol: KaTypeParameterSymbol, init: KaTypeParameterTypeBuilder.() -> Unit): KaTypeParameterType {
        withValidityAssertion {
            val builder = KaBaseTypeParameterTypeBuilder.BySymbol(symbol, token).apply(init)
            val descriptor = getSymbolDescriptor(builder.symbol) as? TypeParameterDescriptor
            val kotlinType = descriptor?.defaultType
                ?: ErrorUtils.createErrorType(ErrorTypeKind.NOT_FOUND_DESCRIPTOR_FOR_TYPE_PARAMETER, builder.toString())
            val typeWithNullability = TypeUtils.makeNullableAsSpecified(kotlinType, builder.nullability.isNullable)
            return typeWithNullability.toKtType(analysisContext) as KaTypeParameterType
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
            val projections = when (val builderProjection = builder.projection) {
                is KaStarTypeProjection -> StarProjectionForAbsentTypeParameter(analysisContext.builtIns)
                is KaTypeArgumentWithVariance -> TypeProjectionImpl(
                    builderProjection.variance,
                    (builderProjection.type as KaFe10Type).fe10Type
                )
            }

            val kotlinType = CapturedType(typeProjection = projections, isMarkedNullable = builder.nullability.isNullable)
            return kotlinType.toKtType(analysisContext) as KaCapturedType
        }
    }

    override fun buildDefinitelyNotNullType(type: KaType, init: KaDefinitelyNotNullTypeBuilder.() -> Unit): KaDefinitelyNotNullType {
        withValidityAssertion {
            val builder = KaBaseDefinitelyNotNullTypeBuilder.ByType(type, token).apply(init)
            val originalType = builder.original as KaFe10Type
            return DefinitelyNotNullType.makeDefinitelyNotNull(originalType.fe10Type)?.toKtType(analysisContext) as KaDefinitelyNotNullType
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
            val lowerBound = builder.lowerBound
            require(lowerBound is KaFe10Type)
            val upperBound = builder.upperBound
            require(upperBound is KaFe10Type)

            val kotlinType = FlexibleTypeImpl(lowerBound.fe10Type.asSimpleType(), upperBound.fe10Type.asSimpleType())
            return kotlinType.toKtType(analysisContext) as KaFlexibleType
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
            val conjuncts = builder.conjuncts.map { conjunctType ->
                (conjunctType as KaFe10Type).fe10Type
            }

            val intersectionConstructor = IntersectionTypeConstructor(conjuncts)
            val simpleType = KotlinTypeFactory.simpleType(
                attributes = TypeAttributes.Empty,
                constructor = intersectionConstructor,
                arguments = emptyList(),
                nullable = false
            )

            return simpleType.toKtType(analysisContext) as KaIntersectionType
        }
    }

    override fun buildDynamicType(): KaDynamicType {
        withValidityAssertion {
            val kotlinType = createDynamicType(analysisContext.builtIns)
            return kotlinType.toKtType(analysisContext) as KaDynamicType
        }
    }

    override fun buildFunctionType(
        classId: ClassId,
        init: KaFunctionTypeBuilder.() -> Unit,
    ): KaType = withValidityAssertion {
        buildFunctionType(KaBaseFunctionTypeBuilder.ByClassId(classId, token, analysisSession).apply(init))
    }

    private fun buildFunctionType(builder: KaBaseFunctionTypeBuilder): KaType {
        val descriptor = when (builder) {
            is KaBaseFunctionTypeBuilder.ByClassId -> {
                val builderClassId = builder.classId
                val numberOfParameters =
                    builder.contextParameters.size + (if (builder.receiverType != null) 1 else 0) + builder.parameters.size
                val functionNameWithParameterNumber = builderClassId.shortClassName.asString() + numberOfParameters
                val updatedClassId = ClassId(builderClassId.packageFqName, Name.identifier(functionNameWithParameterNumber))
                val fqName = updatedClassId.asSingleFqName()

                val descriptor: ClassDescriptor? = analysisContext.resolveSession
                    .getTopLevelClassifierDescriptors(fqName, NoLookupLocation.FROM_IDE)
                    .firstIsInstanceOrNull()

                if (descriptor == null) {
                    val kotlinType = ErrorUtils.createErrorType(ErrorTypeKind.UNRESOLVED_CLASS_TYPE, updatedClassId.asString())
                    return KaFe10ClassErrorType(kotlinType, analysisContext)
                }

                descriptor
            }
        }

        val typeParameters = descriptor.typeConstructor.parameters

        val typeArguments = buildList {
            addAll(builder.contextParameters.map { (it.type as KaFe10Type).fe10Type })
            addIfNotNull((builder.receiverType as KaFe10Type).fe10Type)
            addAll(builder.parameters.map { valueParameter ->
                val type = (valueParameter.type as KaFe10Type).fe10Type
                val name = valueParameter.name
                if (name != null) {
                    val parameterNameAnnotation = BuiltInAnnotationDescriptor(
                        analysisSession.analysisContext.builtIns,
                        StandardNames.FqNames.parameterName,
                        mapOf(StandardNames.NAME to StringValue(name.asString()))
                    )
                    type.replaceAnnotations(Annotations.create(type.annotations + parameterNameAnnotation))
                } else {
                    type
                }
            })
            add((builder.returnType as KaFe10Type).fe10Type)
        }.map { TypeProjectionImpl(it) }

        val type = if (typeParameters.size == typeArguments.size) {
            TypeUtils.substituteProjectionsForParameters(descriptor, typeArguments)
        } else {
            descriptor.defaultType
        }

        val typeWithNullability = TypeUtils.makeNullableAsSpecified(type, builder.nullability == KaTypeNullability.NULLABLE)
        return KaFe10UsualClassType(typeWithNullability as SimpleType, descriptor, analysisContext)
    }
}
