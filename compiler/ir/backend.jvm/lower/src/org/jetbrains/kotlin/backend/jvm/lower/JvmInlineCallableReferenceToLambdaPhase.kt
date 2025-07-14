/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.phaser.PhaseDescription
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext

/**
 * Transforms callable references to inline lambdas, marks inline lambdas for later passes.
 */
@PhaseDescription(name = "JvmInlineCallableReferenceToLambdaPhase")
internal class JvmInlineCallableReferenceToLambdaPhase(
    context: JvmBackendContext,
) : InlineCallableReferenceToLambdaPhase(
    context
)
