/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.internal.jvm


import org.jetbrains.kotlin.buildtools.api.internal.BaseOption
import org.jetbrains.kotlin.buildtools.api.jvm.JvmSnapshotBasedIncrementalCompilationOptions
import org.jetbrains.kotlin.buildtools.internal.OptionsDelegate
import org.jetbrains.kotlin.buildtools.internal.UseFromImplModuleRestricted
import java.nio.file.Path

class JvmSnapshotBasedIncrementalCompilationOptionsImpl() : JvmSnapshotBasedIncrementalCompilationOptions {
    private val optionsDelegate = OptionsDelegate()

    init {
        this[PRECISE_JAVA_TRACKING] = true
        this[BACKUP_CLASSES] = false
        this[KEEP_IC_CACHES_IN_MEMORY] = false
        this[FORCE_RECOMPILATION] = false
        this[OUTPUT_DIRS] = null
        this[USE_FIR_RUNNER] = false
        this[ASSURED_NO_CLASSPATH_SNAPSHOT_CHANGES] = false
    }

    @OptIn(UseFromImplModuleRestricted::class)
    operator fun <V> get(key: Option<V>): V = optionsDelegate[key]

    @OptIn(UseFromImplModuleRestricted::class)
    private operator fun <V> set(key: Option<V>, value: V) {
        optionsDelegate[key] = value
    }

    @UseFromImplModuleRestricted
    override fun <V> get(key: JvmSnapshotBasedIncrementalCompilationOptions.Option<V>): V = optionsDelegate[key]

    @UseFromImplModuleRestricted
    override fun <V> set(key: JvmSnapshotBasedIncrementalCompilationOptions.Option<V>, value: V) {
        optionsDelegate[key] = value
    }

    class Option<V>(id: String) : BaseOption<V>(id)

    companion object {
        val ROOT_PROJECT_DIR: Option<Path> = Option("ROOT_PROJECT_DIR")

        val MODULE_BUILD_DIR: Option<Path> = Option("MODULE_BUILD_DIR")

        val PRECISE_JAVA_TRACKING: Option<Boolean> =
            Option("PRECISE_JAVA_TRACKING")

        val BACKUP_CLASSES: Option<Boolean> = Option("BACKUP_CLASSES")

        val KEEP_IC_CACHES_IN_MEMORY: Option<Boolean> = Option("KEEP_IC_CACHES_IN_MEMORY")

        val FORCE_RECOMPILATION: Option<Boolean> = Option("FORCE_RECOMPILATION")

        val RECOMPILATION_CLEANUP_DIRS: Option<Path> = Option("REBUILD_CLEANUP_DIRS")

        val OUTPUT_DIRS: Option<Set<Path>?> = Option("OUTPUT_DIRS")

        val ASSURED_NO_CLASSPATH_SNAPSHOT_CHANGES: Option<Boolean> =
            Option("ASSURED_NO_CLASSPATH_SNAPSHOT_CHANGES")

        val USE_FIR_RUNNER: Option<Boolean> = Option("USE_FIR_RUNNER")
    }
}
