/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.trackers

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi

/**
 * A tracker that will be informed whenever the compiler makes lookups for references.
 */
@ExperimentalBuildToolsApi
public interface CompilerLookupTracker {
    /**
     * Position inside a source file.
     */
    public class Position(public val line: Int, public val column: Int)

    public enum class ScopeKind {
        PACKAGE,
        CLASSIFIER
    }

    public val requiresPosition: Boolean

    public fun recordLookup(
        filePath: String,
        position: Position,
        scopeFqName: String,
        scopeKind: ScopeKind,
        name: String,
    )

    public fun clear()
}