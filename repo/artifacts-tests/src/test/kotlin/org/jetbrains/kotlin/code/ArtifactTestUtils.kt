/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.code

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.relativeTo

val kotlinVersion: String = System.getProperty("kotlin.version")
val mavenLocal: String = System.getProperty("maven.repo.local")
val localRepoPath: Path = Paths.get(mavenLocal, "org/jetbrains/kotlin")
val expectedRepoPath: Path = Paths.get("repo/artifacts-tests/src/test/resources/org/jetbrains/kotlin")

/**
 * Kotlin native bundles are present in TC artifacts but should not be checked until kotlin native enabled project-wide
 */
val nativeBundles = setOf(
    "kotlin-native",
    "kotlin-native-compiler-embeddable",
    "kotlin-native-prebuilt",
)

val excludedProjects = setOf(
    "android-test-fixes",
    "annotation-processor-example",
    "gradle-warnings-detector",
    "kotlin-compiler-args-properties",
    "kotlin-gradle-plugin-tcs-android",
    "kotlin-gradle-subplugin-example",
    "kotlin-java-example",
    "kotlin-maven-plugin-test",
    "org.jetbrains.kotlin.gradle-subplugin-example.gradle.plugin",
    "org.jetbrains.kotlin.test.fixes.android.gradle.plugin",
    "org.jetbrains.kotlin.test.gradle-warnings-detector.gradle.plugin",
    "org.jetbrains.kotlin.test.kotlin-compiler-args-properties.gradle.plugin",
)

/**
 * convert:
 * ${mavenLocal}/org/jetbrains/kotlin/artifact/version/artifact-version.${extension}
 * to:
 * ${expectedRepository}/org/jetbrains/kotlin/artifact/artifact.${extension}
 */
fun Path.toExpectedPath(fileExtension: String): Path {
    val artifactDirPath = localRepoPath.relativize(this).parent.parent
    val expectedFileName = "${artifactDirPath.fileName}$fileExtension"
    return expectedRepoPath.resolve(artifactDirPath.resolve(expectedFileName))
}
