/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.code

import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEqualsToFile
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

class ArtifactsTest {
    @TestFactory
    fun generateArtifactTests(): Stream<DynamicTest> {
        return findActualPoms().map { actual ->
            val expectedPomPath = actual.toExpectedPath(fileExtension = ".pom")
            DynamicTest.dynamicTest(expectedPomPath.fileName.toString()) {
                if ("${expectedPomPath.parent.fileName}" !in excludedProjects) {
                    if ("${expectedPomPath.parent.fileName}" !in nativeBundles) {
                        val regex = Regex(
                            """(<groupId>org.jetbrains.kotlin\S*</groupId>\s*\S*\s*)<version>$kotlinVersion</version>"""
                        )
                        val actualString = actual.toFile().readText().replace(regex, "\$1<version>ArtifactsTest.version</version>")
                        assertEqualsToFile(expectedPomPath, actualString)
                    }
                } else {
                    if (isTeamCityBuild) fail("Excluded project in actual artifacts: $actual")
                }
            }
        }.asStream()
    }

    @TestFactory
    fun allExpectedPomsPresentInActual(): Stream<DynamicTest> {
        val publishedPoms = findActualPoms()
            .map { it.toExpectedPath(fileExtension = ".pom") }
            .filter { "${it.parent.fileName}" !in excludedProjects }.toSet()

        return findExpectedPoms().map { expected ->
            DynamicTest.dynamicTest(expected.fileName.toString()) {
                assertTrue(expected in publishedPoms, "Missing actual pom for expected pom: $expected")
            }
        }.asStream()
    }

    private fun findActualPoms() = Files.find(
        localRepoPath,
        Integer.MAX_VALUE,
        { path: Path, fileAttributes: BasicFileAttributes ->
            fileAttributes.isRegularFile
                    && "${path.fileName}".endsWith(".pom", ignoreCase = true)
                    && path.contains(Paths.get(kotlinVersion))
        }).asSequence()

    private fun findExpectedPoms() = Files.find(
        expectedRepoPath,
        Integer.MAX_VALUE,
        { path: Path, fileAttributes: BasicFileAttributes ->
            fileAttributes.isRegularFile
                    && "${path.fileName}".endsWith(".pom", ignoreCase = true)
        }).asSequence()
}