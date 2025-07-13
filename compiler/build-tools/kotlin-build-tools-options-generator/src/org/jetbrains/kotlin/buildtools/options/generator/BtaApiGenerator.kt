/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.options.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.kotlin.arguments.dsl.base.KotlinCompilerArgument
import org.jetbrains.kotlin.arguments.dsl.base.KotlinCompilerArgumentsLevel
import org.jetbrains.kotlin.arguments.dsl.types.*
import org.jetbrains.kotlin.generators.kotlinpoet.annotation
import org.jetbrains.kotlin.generators.kotlinpoet.function
import org.jetbrains.kotlin.generators.kotlinpoet.property
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf

// TODO: workaround for now, but we should expose these in the arguments module in a way that doesn't need listing enums and their accessors explicitly here
internal val enumNameAccessors = mutableMapOf(
    JvmTarget::class to JvmTarget::targetName,
    ExplicitApiMode::class to ExplicitApiMode::modeName,
    KotlinVersion::class to KotlinVersion::versionName,
    ReturnValueCheckerMode::class to ReturnValueCheckerMode::modeState
)
@Suppress("UNCHECKED_CAST")
internal fun KClass<*>.accessor(): KProperty1<Any, String> = enumNameAccessors[this] as? KProperty1<Any, String>
    ?: error("Unknown enum in compiler arguments. Must be one of: ${enumNameAccessors.keys.joinToString()}.")

class BtaApiGenerator(val genDir: Path) : BtaGenerator {
    override fun generateArgumentsForLevel(level: KotlinCompilerArgumentsLevel, parentClass: TypeName?, skipXX: Boolean): TypeName {
        val className = level.name.capitalizeAsciiOnly()
        FileSpec.Companion.builder(API_PACKAGE, className).apply {
            addType(
                TypeSpec.Companion.interfaceBuilder(className).apply {
                    parentClass?.let { addSuperinterface(it) }
                    val argument = generateArgumentType(className)
                    val argumentTypeName = ClassName(API_PACKAGE, className, argument)
                    generateGetPutFunctions(argumentTypeName)
                    addType(TypeSpec.Companion.companionObjectBuilder().apply {
                        generateOptions(level.filterOutDroppedArguments(), argumentTypeName, skipXX)
                    }.build())
                }.build()
            )
        }.build().writeTo(genDir)
        return ClassName(API_PACKAGE, className)
    }

    private fun TypeSpec.Builder.generateOptions(
        arguments: Collection<KotlinCompilerArgument>,
        argumentTypeName: ClassName,
        skipXX: Boolean,
    ) {
        val enumsToGenerate = mutableMapOf<KClass<*>, TypeSpec.Builder>()
        val enumsExperimental = mutableMapOf<KClass<*>, Boolean>()

        arguments.forEach { argument ->
            val name = argument.extractName()
            if (skipXX && name.startsWith("XX_")) return@forEach
            val experimental = name.startsWith("XX_") || name.startsWith("X_")

            val argumentTypeParameter = argument.valueType::class
                .supertypes.single { it.classifier == KotlinArgumentValueType::class }
                .arguments.first().type!!.let {
                    when (val type = it.classifier) {
                        is KClass<*> if type.isSubclassOf(Enum::class) && type in enumNameAccessors -> {
                            val enumConstants = type.java.enumConstants.filterIsInstance<Enum<*>>()
                            enumsToGenerate[type] = generateEnumTypeBuilder(enumConstants, type.accessor())
                            if (type !in enumsExperimental && experimental) {
                                enumsExperimental[type] = true
                            } else if (type in enumsExperimental && !experimental) {
                                // if at least one option that is NOT experimental uses the enum
                                // then the enum is not experimental itself
                                enumsExperimental[type] = false
                            }
                            ClassName("$API_PACKAGE.enums", type.simpleName!!)
                        }
                        else -> {
                            it.asTypeName()
                        }
                    }
                }
                .copy(nullable = argument.valueType.isNullable.current)
            property(name, argumentTypeName.parameterizedBy(argumentTypeParameter)) {
                annotation<JvmField>()
                // KT-28979 Need a way to escape /* in kdoc comments
                // inserting a zero-width space is not ideal, but we do actually have one compiler argument that breaks the KDoc without it
                addKdoc(argument.description.current.replace("/*", "/\u200B*").replace("*/", "*\u200B/"))
                if (experimental) {
                    addAnnotation(ANNOTATION_EXPERIMENTAL)
                    addKdoc("\n\nWARNING: this option is EXPERIMENTAL and it may be changed in the future without notice or may be removed entirely.")
                }
                initializer("%T(%S)", argumentTypeName, name)
            }
        }

        enumsToGenerate.forEach { (type, typeSpecBuilder) ->
            if (enumsExperimental.getOrDefault(type, false)) {
                typeSpecBuilder.addAnnotation(ANNOTATION_EXPERIMENTAL)
            }
            writeEnumFile(typeSpecBuilder.build(), type)
        }
    }

    fun generateEnumTypeBuilder(
        sourceEnum: Collection<Enum<*>>,
        nameAccessor: KProperty1<Any, String>,
    ): TypeSpec.Builder {
        val className = ClassName("$API_PACKAGE.enums", sourceEnum.first()::class.simpleName!!)
        return TypeSpec.Companion.enumBuilder(className).apply {
            property<String>("stringValue") {
                initializer("stringValue")
            }
            primaryConstructor(FunSpec.Companion.constructorBuilder().addParameter("stringValue", String::class).build())
            sourceEnum.forEach {
                addEnumConstant(
                    it.name.uppercase(),
                    TypeSpec.Companion.anonymousClassBuilder()
                        .addSuperclassConstructorParameter("%S", nameAccessor.get(it))
                        .build()
                )
            }
        }
    }

    fun writeEnumFile(typeSpec: TypeSpec, sourceEnum: KClass<*>) {
        val className = ClassName("$API_PACKAGE.enums", sourceEnum.simpleName!!)
        FileSpec.Companion.builder(className).apply {
            addType(typeSpec)
        }.build().writeTo(genDir)
    }


    fun TypeSpec.Builder.generateArgumentType(argumentsClassName: String): String {
        require(argumentsClassName.endsWith("Arguments"))
        val argumentTypeName = argumentsClassName.removeSuffix("s")
        val typeSpec =
            TypeSpec.Companion.classBuilder(argumentTypeName).apply {
                addTypeVariable(TypeVariableName.Companion("V"))
                property<String>("id") {
                    initializer("id")
                }
                primaryConstructor(FunSpec.Companion.constructorBuilder().addParameter("id", String::class).build())
            }.build()
        addType(typeSpec)
        return argumentTypeName
    }

    fun TypeSpec.Builder.generateGetPutFunctions(parameter: ClassName) {
        function("get") {
            addModifiers(KModifier.ABSTRACT)
            val typeParameter = TypeVariableName.Companion("V")
            annotation<Suppress> {
                addMember("%S", "UNCHECKED_CAST")
            }
            returns(typeParameter)
            addModifiers(KModifier.OPERATOR)
            addTypeVariable(typeParameter)
            addParameter("key", parameter.parameterizedBy(typeParameter))
        }
        function("set") {
            addModifiers(KModifier.ABSTRACT)
            val typeParameter = TypeVariableName.Companion("V")
            addModifiers(KModifier.OPERATOR)
            addTypeVariable(typeParameter)
            addParameter("key", parameter.parameterizedBy(typeParameter))
            addParameter("value", typeParameter)
        }
    }
}