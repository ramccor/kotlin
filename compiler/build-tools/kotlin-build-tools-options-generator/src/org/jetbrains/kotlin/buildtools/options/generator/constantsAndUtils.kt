/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.options.generator

import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.arguments.description.CompilerArgumentsLevelNames
import org.jetbrains.kotlin.arguments.dsl.base.KotlinCompilerArgument
import org.jetbrains.kotlin.arguments.dsl.types.ExplicitApiMode
import org.jetbrains.kotlin.arguments.dsl.types.JvmTarget
import org.jetbrains.kotlin.arguments.dsl.types.KotlinVersion
import org.jetbrains.kotlin.arguments.dsl.types.ReturnValueCheckerMode
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal const val IMPL_PACKAGE = "org.jetbrains.kotlin.buildtools.internal.v2"
internal const val API_PACKAGE = "org.jetbrains.kotlin.buildtools.api.v2"
internal val ANNOTATION_EXPERIMENTAL = ClassName(API_PACKAGE, "ExperimentalCompilerArgument")

internal val experimentalLevelNames = listOf(
    CompilerArgumentsLevelNames.commonKlibBasedArguments,
    CompilerArgumentsLevelNames.jsArguments,
    CompilerArgumentsLevelNames.nativeArguments,
    CompilerArgumentsLevelNames.wasmArguments,
)

internal fun KotlinCompilerArgument.extractName(): String = name.uppercase().replace("-", "_").let {
    when {
        it.startsWith("XX") && it != "XX" -> it.replaceFirst("XX", "XX_")
        it.startsWith("X") && it != "X" -> it.replaceFirst("X", "X_")
        else -> it
    }
}

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
