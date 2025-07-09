/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.code

import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.test.services.JUnit5Assertions.isTeamCityBuild
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream
import kotlin.test.assertTrue
import kotlin.test.fail

class GradleMetadataTest {

    private val kotlinVersion = System.getProperty("kotlin.version")
    private val mavenLocal = System.getProperty("maven.repo.local")
    private val localRepoPath = Paths.get(mavenLocal, "org/jetbrains/kotlin")
    private val expectedRepoPath = Paths.get("repo/artifacts-tests/src/test/resources/org/jetbrains/kotlin")

    /**
     * Kotlin native bundles are present in TC artifacts but should not be checked until kotlin native enabled project-wide
     */
    private val nativeBundles = setOf(
        "kotlin-native",
        "kotlin-native-compiler-embeddable",
        "kotlin-native-prebuilt",
    )

    private val excludedProjects = setOf(
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

    @TestFactory
    fun generateArtifactTests(): Stream<DynamicTest> {
        return findActualGradleMetadata().map { actual ->
            val expectedGradleMetadataPath = actual.toExpectedPath()
            DynamicTest.dynamicTest(expectedGradleMetadataPath.fileName.toString()) {
                if ("${expectedGradleMetadataPath.parent.fileName}" !in excludedProjects) {
                    if ("${expectedGradleMetadataPath.parent.fileName}" !in nativeBundles) {
                        val expectedMetadata = Json.decodeFromString<GradleMetadata>(expectedGradleMetadataPath.toFile().readText())
                        val actualString = actual.toFile().readText().replace(kotlinVersion, "ArtifactsTest.version")
                        val actualMetadata = Json.decodeFromString<GradleMetadata>(actualString)
                        assertTrue(
                            expectedMetadata equalsWithoutFingerprint actualMetadata,
                            "Metadata at $actual is not equal to expected metadata at $expectedGradleMetadataPath"
                        )
                    }
                } else {
                    if (isTeamCityBuild) fail("Excluded project in actual artifacts: $actual")
                }
            }
        }.asStream()
    }

    @TestFactory
    fun allExpectedGradleMetadataPresentInActual(): Stream<DynamicTest> {
        val publishedGradleMetadata = findActualGradleMetadata()
            .map { it.toExpectedPath() }
            .filter { "${it.parent.fileName}" !in excludedProjects }.toSet()

        return findExpectedGradleMetadata().map { expected ->
            DynamicTest.dynamicTest(expected.fileName.toString()) {
                assertTrue(expected in publishedGradleMetadata, "Missing actual gradle metadata for expected gradle metadata: $expected")
            }
        }.asStream()
    }

    private fun findActualGradleMetadata() = Files.find(
        localRepoPath,
        Integer.MAX_VALUE,
        { path: Path, fileAttributes: BasicFileAttributes ->
            fileAttributes.isRegularFile
                    && "${path.fileName}".endsWith(".module", ignoreCase = true)
                    && path.contains(Paths.get(kotlinVersion))
        }).asSequence()

    private fun findExpectedGradleMetadata() = Files.find(
        expectedRepoPath,
        Integer.MAX_VALUE,
        { path: Path, fileAttributes: BasicFileAttributes ->
            fileAttributes.isRegularFile
                    && "${path.fileName}".endsWith(".module", ignoreCase = true)
        }).asSequence()

    /**
     * convert:
     * ${mavenLocal}/org/jetbrains/kotlin/artifact/version/artifact-version.module
     * to:
     * ${expectedRepository}/org/jetbrains/kotlin/artifact/artifact.module
     */
    private fun Path.toExpectedPath(): Path {
        val artifactDirPath = localRepoPath.relativize(this).parent.parent
        val expectedFileName = "${artifactDirPath.fileName}.module"
        return expectedRepoPath.resolve(artifactDirPath.resolve(expectedFileName))
    }
}