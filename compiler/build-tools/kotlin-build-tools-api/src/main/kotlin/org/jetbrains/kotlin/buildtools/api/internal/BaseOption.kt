/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.internal

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi

/**
 * Base class for options used by build operations and arguments.
 *
 * @since 2.3.0
 */
@ExperimentalBuildToolsApi
public abstract class BaseOption<V>(public val id: String) {
    override fun toString(): String = id
}

