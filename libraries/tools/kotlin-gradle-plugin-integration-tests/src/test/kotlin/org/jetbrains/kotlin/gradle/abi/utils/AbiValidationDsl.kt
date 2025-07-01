/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.abi.utils

import org.gradle.api.plugins.ExtensionAware
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationExtension
import org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationMultiplatformExtension
import org.jetbrains.kotlin.gradle.testbase.GradleProject
import org.jetbrains.kotlin.gradle.testbase.GradleProjectBuildScriptInjectionContext
import java.io.File
import kotlin.io.path.writeText

/**
 * Gets Kotlin extension for Kotlin Android Gradle plugin.
 */
internal val GradleProjectBuildScriptInjectionContext.kotlinAndroid
    get(): KotlinAndroidExtension = project.extensions.getByName("kotlin") as KotlinAndroidExtension

/**
 * Configures ABI validation in a Kotlin Android project.
 */
internal fun KotlinAndroidExtension.abiValidation(
    configure: AbiValidationExtension.() -> Unit,
) {
    ((this as ExtensionAware).extensions.getByName("abiValidation") as AbiValidationExtension).configure()
}

/**
 * Configures ABI validation in a Kotlin Multiplatform project.
 */
internal fun KotlinMultiplatformExtension.abiValidation(
    configure: AbiValidationMultiplatformExtension.() -> Unit,
) {
    (extensions.getByName("abiValidation") as AbiValidationMultiplatformExtension).configure()
}

/**
 * Adds a source file with the given [content] to the specified [sourceSet] at the given [filePath].
 */
internal fun GradleProject.sourceFile(@Language("kotlin") content: String, filePath: String = "Sources.kt", sourceSet: String = "main") {
    val sourceDir = kotlinSourcesDir(sourceSet)
    sourceDir.toFile().mkdirs()
    sourceDir.resolve(filePath).writeText(content)
}

/**
 * Gets the reference dump file for the specified [variant] in a Kotlin JVM project or a Kotlin Multiplatform project without any Android target.
 */
internal fun GradleProject.referenceJvmDumpFile(variant: String = "main"): File {
    val dumpDir = if (variant == "main") "api" else "api-$projectName"
    return projectPath.resolve(dumpDir).resolve("$projectName.api").toFile()
}

/**
 * Gets the reference dump file for the JVM target of the specified [variant] in a Kotlin Multiplatform project with a mix of JVM and Android targets.
 */
internal fun GradleProject.referenceMixedJvmDumpFile(variant: String = "main"): File {
    val dumpDir = if (variant == "main") "api" else "api-$projectName"
    return projectPath.resolve(dumpDir).resolve("jvm").resolve("$projectName.api").toFile()
}

/**
 * Gets the reference dump file for the Android target of the specified [variant] in a Kotlin Multiplatform project with a mix of JVM and Android targets.
 */
internal fun GradleProject.referenceMixedAndroidDumpFile(variant: String = "main"): File {
    val dumpDir = if (variant == "main") "api" else "api-$projectName"
    return projectPath.resolve(dumpDir).resolve("android").resolve("$projectName.api").toFile()
}
