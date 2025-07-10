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
import kotlin.io.path.absolute
import kotlin.streams.asSequence
import kotlin.streams.asStream
import kotlin.test.assertTrue
import kotlin.test.fail

class GradleMetadataTest {

    @TestFactory
    fun generateArtifactTests(): Stream<DynamicTest> {
        return findActualGradleMetadata().map { actual ->
            val expectedGradleMetadataPath = actual.toExpectedPath(fileExtension = ".module").absolute()
            DynamicTest.dynamicTest(expectedGradleMetadataPath.fileName.toString()) {
                if ("${expectedGradleMetadataPath.parent.fileName}" !in excludedProjects) {
                    if ("${expectedGradleMetadataPath.parent.fileName}" !in nativeBundles) {
                        val expectedMetadataString =
                            expectedGradleMetadataPath.toFile().readText().replace("ArtifactsTest.version", kotlinVersion)
                        val expectedMetadata = Json.decodeFromString<GradleMetadata>(expectedMetadataString)
                        val actualString = actual.toFile().readText()
                        val actualMetadata = Json.decodeFromString<GradleMetadata>(actualString)
                        assertTrue(
                            expectedMetadata equalsWithoutFingerprint actualMetadata,
                            "Gradle metadata at $actual is not equal to expected gradle metadata at $expectedGradleMetadataPath"
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
            .map { it.toExpectedPath(fileExtension = ".module") }
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
}