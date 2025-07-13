/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.tests.compilation.model

import org.jetbrains.kotlin.buildtools.api.ProjectId
import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshotGranularity
import org.jetbrains.kotlin.buildtools.api.tests.compilation.BaseCompilationTest
import org.jetbrains.kotlin.buildtools.api.v2.ExecutionPolicy
import org.jetbrains.kotlin.buildtools.api.v2.JvmCompilerArguments
import org.jetbrains.kotlin.buildtools.api.v2.KotlinToolchain
import org.jetbrains.kotlin.buildtools.api.v2.jvm.operations.JvmCompilationOperation
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

class Project(
    val kotlinToolchain: KotlinToolchain,
    val defaultStrategyConfig: ExecutionPolicy,
    val projectDirectory: Path,
) {
    val projectId = ProjectId.RandomProjectUUID()
    private val invalidModuleNameCharactersRegex = """[\\/\r\n\t]""".toRegex()

    fun module(
        moduleName: String,
        dependencies: List<Module> = emptyList(),
        snapshotConfig: SnapshotConfig = SnapshotConfig(ClassSnapshotGranularity.CLASS_MEMBER_LEVEL, true),
        compilationOperationConfig: (JvmCompilationOperation) -> Unit = {},
    ): Module {
        val moduleDirectory = projectDirectory.resolve(moduleName)
        val sanitizedModuleName = moduleName.replace(invalidModuleNameCharactersRegex, "_")
        val module = JvmModule(
            kotlinToolchain = kotlinToolchain,
            project = this,
            moduleName = sanitizedModuleName,
            moduleDirectory = moduleDirectory,
            dependencies = dependencies,
            defaultStrategyConfig = defaultStrategyConfig,
            snapshotConfig = snapshotConfig,
            compilationOperationConfig = compilationOperationConfig
        )
        module.sourcesDirectory.createDirectories()
        val templatePath = Paths.get("src/main/resources/modules/$moduleName")
        assert(templatePath.isDirectory()) {
            "Template for $moduleName not found. Expected template directory path is $templatePath"
        }
        templatePath.copyToRecursively(module.sourcesDirectory, followLinks = false)
        return module
    }

    fun endCompilationRound() {
        kotlinToolchain.finishBuild(projectId)
    }
}

fun BaseCompilationTest.project(kotlinToolchain: KotlinToolchain, strategyConfig: ExecutionPolicy, action: Project.() -> Unit) {
    Project(kotlinToolchain, strategyConfig, workingDirectory).apply {
        action()
        endCompilationRound()
    }
}