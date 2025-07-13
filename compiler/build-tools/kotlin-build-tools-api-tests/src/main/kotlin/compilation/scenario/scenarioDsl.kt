/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.tests.compilation.scenario

import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import org.jetbrains.kotlin.buildtools.api.tests.compilation.BaseCompilationTest
import org.jetbrains.kotlin.buildtools.api.tests.compilation.model.*
import org.jetbrains.kotlin.buildtools.api.v2.ExecutionPolicy
import org.jetbrains.kotlin.buildtools.api.v2.JvmCompilerArguments
import org.jetbrains.kotlin.buildtools.api.v2.KotlinToolchain
import org.jetbrains.kotlin.buildtools.api.v2.jvm.JvmIncrementalCompilationConfiguration
import org.jetbrains.kotlin.buildtools.api.v2.jvm.operations.JvmCompilationOperation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal abstract class BaseScenarioModule(
    internal val module: Module,
    internal val outputs: MutableSet<String>,
    private val strategyConfig: ExecutionPolicy,
    private val compilationOptionsModifier: ((JvmCompilationOperation) -> Unit)?,
    private val incrementalCompilationOptionsModifier: ((JvmIncrementalCompilationConfiguration) -> Unit)?,
) : ScenarioModule {
    override fun changeFile(
        fileName: String,
        transform: (String) -> String,
    ) {
        val file = module.sourcesDirectory.resolve(fileName)
        writeFile(fileName, transform(file.readText()))
    }

    override fun replaceFileWithVersion(fileName: String, version: String) {
        val file = module.sourcesDirectory.resolve(fileName)
        val chosenRevision = module.sourcesDirectory.resolve("$fileName.$version")
        Files.delete(file)
        Files.copy(chosenRevision, file)
    }

    override fun deleteFile(fileName: String) {
        val file = module.sourcesDirectory.resolve(fileName)
        file.deleteExisting()
    }

    override fun createFile(fileName: String, content: String) {
        writeFile(fileName, content)
    }

    override fun createPredefinedFile(fileName: String, version: String) {
        val file = module.sourcesDirectory.resolve(fileName)
        val chosenRevision = module.sourcesDirectory.resolve("$fileName.$version")
        Files.copy(chosenRevision, file)
    }

    protected open fun writeFile(fileName: String, newContent: String) {
        val file = module.sourcesDirectory.resolve(fileName)
        file.writeText(newContent)
    }

    protected abstract fun getSourcesChanges(): SourcesChanges

    override fun compile(
        forceOutput: LogLevel?,
        assertions: CompilationOutcome.(Module, ScenarioModule) -> Unit,
    ) {
        module.compileIncrementally(
            getSourcesChanges(),
            strategyConfig,
            forceOutput,
            compilationConfigAction = { compilationOptionsModifier?.invoke(it) },
            incrementalCompilationConfigAction = { incrementalCompilationOptionsModifier?.invoke(it) },
            assertions = {
                assertions(this, module, this@BaseScenarioModule)
            })
    }

    override fun executeCompiledCode(
        mainClassFqn: String,
        assertions: ExecutionOutcome.() -> Unit
    ) {
        module.executeCompiledClass(
            mainClassFqn,
            assertions
        )
    }
}

internal class ExternallyTrackedScenarioModuleImpl(
    module: Module,
    outputs: MutableSet<String>,
    strategyConfig: ExecutionPolicy,
    compilationOptionsModifier: ((JvmCompilationOperation) -> Unit)?,
    incrementalCompilationOptionsModifier: ((JvmIncrementalCompilationConfiguration) -> Unit)?,
) : BaseScenarioModule(module, outputs, strategyConfig, compilationOptionsModifier, incrementalCompilationOptionsModifier) {
    private var sourcesChanges = SourcesChanges.Known(emptyList(), emptyList())

    override fun replaceFileWithVersion(fileName: String, version: String) {
        super.replaceFileWithVersion(fileName, version)
        val file = module.sourcesDirectory.resolve(fileName)
        addToModifiedFiles(file)
    }

    override fun deleteFile(fileName: String) {
        super.deleteFile(fileName)
        val file = module.sourcesDirectory.resolve(fileName)
        addToRemovedFiles(file)
    }

    override fun createPredefinedFile(fileName: String, version: String) {
        super.createPredefinedFile(fileName, version)
        val file = module.sourcesDirectory.resolve(fileName)
        addToModifiedFiles(file)
    }

    override fun writeFile(fileName: String, newContent: String) {
        super.writeFile(fileName, newContent)
        val file = module.sourcesDirectory.resolve(fileName)
        addToModifiedFiles(file)
    }

    override fun getSourcesChanges() = sourcesChanges

    override fun compile(forceOutput: LogLevel?, assertions: CompilationOutcome.(Module, ScenarioModule) -> Unit) {
        super.compile(forceOutput) { module, scenarioModule ->
            assertions(module, scenarioModule)

            if (actualResult == CompilationResult.COMPILATION_SUCCESS) {
                sourcesChanges = SourcesChanges.Known(emptyList(), emptyList())
            }
        }
    }

    private fun addToModifiedFiles(file: Path) {
        sourcesChanges = SourcesChanges.Known(
            modifiedFiles = sourcesChanges.modifiedFiles + file.toFile(),
            removedFiles = sourcesChanges.removedFiles,
        )
    }

    private fun addToRemovedFiles(file: Path) {
        sourcesChanges = SourcesChanges.Known(
            modifiedFiles = sourcesChanges.modifiedFiles,
            removedFiles = sourcesChanges.removedFiles + file.toFile(),
        )
    }
}

internal class AutoTrackedScenarioModuleImpl(
    module: Module,
    outputs: MutableSet<String>,
    strategyConfig: ExecutionPolicy,
    compilationOptionsModifier: ((JvmCompilationOperation) -> Unit)?,
    incrementalCompilationOptionsModifier: ((JvmIncrementalCompilationConfiguration) -> Unit)?,
) : BaseScenarioModule(module, outputs, strategyConfig, compilationOptionsModifier, incrementalCompilationOptionsModifier) {
    override fun getSourcesChanges() = SourcesChanges.ToBeCalculated
}

private class ScenarioDsl(
    private val project: Project,
    private val strategyConfig: ExecutionPolicy,
) : Scenario {
    @Synchronized
    override fun module(
        moduleName: String,
        dependencies: List<ScenarioModule>,
        snapshotConfig: SnapshotConfig,
        compilationOperationConfig: (JvmCompilationOperation) -> Unit,
        compilationOptionsModifier: ((JvmCompilationOperation) -> Unit)?,
        incrementalCompilationOptionsModifier: ((JvmIncrementalCompilationConfiguration) -> Unit)?,
    ): ScenarioModule {
        val transformedDependencies = dependencies.map { (it as BaseScenarioModule).module }
        val module =
            project.module(moduleName, transformedDependencies, snapshotConfig, compilationOperationConfig)
        return GlobalCompiledProjectsCache.getProjectFromCache(module, strategyConfig, snapshotConfig, compilationOptionsModifier, incrementalCompilationOptionsModifier, false)
            ?: GlobalCompiledProjectsCache.putProjectIntoCache(module, strategyConfig, snapshotConfig, compilationOptionsModifier, incrementalCompilationOptionsModifier, false)
    }

    @Synchronized
    override fun trackedModule(
        moduleName: String,
        dependencies: List<ScenarioModule>,
        snapshotConfig: SnapshotConfig,
        compilationOperationConfig: (JvmCompilationOperation) -> Unit,
        compilationOptionsModifier: ((JvmCompilationOperation) -> Unit)?,
        incrementalCompilationOptionsModifier: ((JvmIncrementalCompilationConfiguration) -> Unit)?,
    ): ScenarioModule {
        val transformedDependencies = dependencies.map { (it as BaseScenarioModule).module }
        val module =
            project.module(moduleName, transformedDependencies, snapshotConfig, compilationOperationConfig)
        return GlobalCompiledProjectsCache.getProjectFromCache(module, strategyConfig, snapshotConfig, compilationOptionsModifier, incrementalCompilationOptionsModifier, true)
            ?: GlobalCompiledProjectsCache.putProjectIntoCache(module, strategyConfig, snapshotConfig, compilationOptionsModifier, incrementalCompilationOptionsModifier, true)
    }
}

fun BaseCompilationTest.scenario(kotlinToolchain: KotlinToolchain, strategyConfig: ExecutionPolicy, action: Scenario.() -> Unit) {
    action(ScenarioDsl(Project(kotlinToolchain, strategyConfig, workingDirectory), strategyConfig))
}
