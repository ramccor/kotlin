/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(ExperimentalAbiValidation::class)

package org.jetbrains.kotlin.gradle.abi.legacy

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.abi.utils.*
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.testbase.*
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue

@AndroidGradlePluginTests
class AbiValidationAndroidIT : KGPBaseTest() {
    @DisplayName("KT-78525 (Android)")
    @GradleAndroidTest
    fun testAndroidCompatibilityWithBcv(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        val project = androidProject(gradleVersion, agpVersion, jdkVersion, applyBcvPlugin = true) {
            abiValidation {
                enabled.set(true)
            }
        }

        project.build("updateLegacyAbi")
        assertFileExists(project.referenceJvmDumpFile())
        assertTrue(project.referenceJvmDumpFile().length() > 0)

        project.build("checkLegacyAbi", buildOptions = project.buildOptions.copy(warningMode = WarningMode.Fail))
        project.build("apiCheck", buildOptions = project.buildOptions.copy(warningMode = WarningMode.Fail))
    }

    @DisplayName("KT-78525 (KMP)")
    @GradleAndroidTest
    fun testKmpCompatibilityWithBcv(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        val project = kmpWithAndroidProject(gradleVersion, agpVersion, jdkVersion, applyBcvPlugin = true) {
            abiValidation {
                enabled.set(true)
            }
        }

        project.sourceFile("""class SimpleClass { fun method(): String = "Hello, world!" }""", sourceSet = "commonMain")

        project.build("updateLegacyAbi") {
            println(output)
        }

        assertFileExists(project.referenceMixedJvmDumpFile())
        assertTrue(project.referenceMixedJvmDumpFile().length() > 0)
        assertFileExists(project.referenceMixedAndroidDumpFile())
        assertTrue(project.referenceMixedAndroidDumpFile().length() > 0)

        project.build("checkLegacyAbi", buildOptions = project.buildOptions.copy(warningMode = WarningMode.Fail))
        project.build("apiCheck", buildOptions = project.buildOptions.copy(warningMode = WarningMode.Fail))
    }
}