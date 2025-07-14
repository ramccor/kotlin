/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.jvm

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmClasspathSnapshottingOperation
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation
import java.nio.file.Path

@ExperimentalBuildToolsApi
public interface JvmPlatformToolchain {
    /**
     * Creates a build operation for compiling Kotlin sources into class files.
     *
     * Note that [sources] should include .java files,
     * so that Kotlin compiler can properly resolve references to Java code and track changes in them.
     * However, Kotlin compiler will not compile the .java files.
     *
     * @param sources all sources of the compilation unit. This includes Java source files.
     * @param destinationDirectory where to put the output of the compilation
     * @see org.jetbrains.kotlin.buildtools.api.KotlinToolchain.executeOperation
     */
    public fun createJvmCompilationOperation(sources: List<Path>, destinationDirectory: Path): JvmCompilationOperation

    /**
     * Creates a build operation for calculating classpath snapshots used for detecting changes in incremental compilation.
     *
     * @param classpathEntry path to existing classpath entry
     * @see org.jetbrains.kotlin.buildtools.api.KotlinToolchain.executeOperation
     */
    public fun createClasspathSnapshottingOperation(classpathEntry: Path): JvmClasspathSnapshottingOperation
}