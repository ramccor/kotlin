/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.tests.compilation.util

import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshotGranularity
import org.jetbrains.kotlin.buildtools.api.tests.compilation.model.SnapshotConfig
import org.jetbrains.kotlin.buildtools.api.tests.compilation.scenario.Scenario
import org.jetbrains.kotlin.buildtools.api.tests.compilation.scenario.ScenarioModule
import org.jetbrains.kotlin.buildtools.api.v2.CommonCompilerArguments
import org.jetbrains.kotlin.buildtools.api.v2.ExperimentalCompilerArgument
import org.jetbrains.kotlin.buildtools.api.v2.JvmCompilerArguments
import org.jetbrains.kotlin.buildtools.api.v2.jvm.JvmSnapshotBasedIncrementalCompilationConfiguration
import org.jetbrains.kotlin.buildtools.api.v2.jvm.JvmSnapshotBasedIncrementalCompilationOptions
import org.jetbrains.kotlin.buildtools.api.v2.jvm.JvmSnapshotBasedIncrementalCompilationOptions.Companion.USE_FIR_RUNNER
import org.jetbrains.kotlin.buildtools.api.v2.jvm.operations.JvmCompilationOperation
import org.jetbrains.kotlin.buildtools.api.v2.jvm.operations.JvmCompilationOperation.Companion.INCREMENTAL_COMPILATION

fun Scenario.moduleWithoutInlineSnapshotting(
    moduleName: String,
    dependencies: List<ScenarioModule>,
) = module(
    moduleName = moduleName,
    dependencies = dependencies,
    snapshotConfig = SnapshotConfig(ClassSnapshotGranularity.CLASS_MEMBER_LEVEL, false),
)

@OptIn(ExperimentalCompilerArgument::class)
fun Scenario.moduleWithFir(
    moduleName: String,
    compilationOperationConfig: (JvmCompilationOperation) -> Unit = {},
) = module(
    moduleName = moduleName,
    compilationOperationConfig = {
        compilationOperationConfig(it)
        it.compilerArguments[CommonCompilerArguments.Companion.X_USE_FIR_IC] = true
        (it[INCREMENTAL_COMPILATION] as? JvmSnapshotBasedIncrementalCompilationConfiguration)?.options[USE_FIR_RUNNER] = true
    }
)
