/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api

import org.jetbrains.kotlin.buildtools.api.js.JsPlatformToolchain
import org.jetbrains.kotlin.buildtools.api.js.WasmPlatformToolchain
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain
import org.jetbrains.kotlin.buildtools.api.knative.NativePlatformToolchain

@ExperimentalBuildToolsApi
public interface KotlinToolchain {
    public val jvm: JvmPlatformToolchain
    public val js: JsPlatformToolchain
    public val native: NativePlatformToolchain
    public val wasm: WasmPlatformToolchain

    public fun createInProcessExecutionPolicy(): ExecutionPolicy.InProcess
    public fun createDaemonExecutionPolicy(): ExecutionPolicy.WithDaemon

    /**
     * Returns the version of the Kotlin compiler used to run compilation.
     *
     * @return A string representing the version of the Kotlin compiler, for example `2.0.0-Beta4`.
     */
    public fun getCompilerVersion(): String

    /**
     * Execute the given [operation] using in-process execution policy.
     *
     * @param operation the [BuildOperation] to execute.
     * Operations can be obtained from platform toolchains, e.g. [JvmPlatformToolchain.createJvmCompilationOperation]
     */
    public suspend fun <R> executeOperation(
        operation: BuildOperation<R>,
    ): R

    /**
     * Execute the given [operation] using the given [executionPolicy].
     *
     * @param operation the [BuildOperation] to execute.
     * Operations can be obtained from platform toolchains, e.g. [JvmPlatformToolchain.createJvmCompilationOperation]
     * @param executionPolicy an [ExecutionPolicy] obtained from [createInProcessExecutionPolicy] or [createDaemonExecutionPolicy]
     * @param logger an optional [KotlinLogger]
     */
    public suspend fun <R> executeOperation(
        operation: BuildOperation<R>,
        executionPolicy: ExecutionPolicy = createInProcessExecutionPolicy(),
        logger: KotlinLogger? = null,
    ): R

    /**
     * This must be called at the end of the project build (i.e., all build operations scoped to the project are finished)
     * iff [projectId] is configured via [BuildOperation.PROJECT_ID]
     */
    public fun finishBuild(projectId: ProjectId)

    public companion object {
        @JvmStatic
        public fun loadImplementation(classLoader: ClassLoader): KotlinToolchain =
            loadImplementation(KotlinToolchain::class, classLoader)
    }
}