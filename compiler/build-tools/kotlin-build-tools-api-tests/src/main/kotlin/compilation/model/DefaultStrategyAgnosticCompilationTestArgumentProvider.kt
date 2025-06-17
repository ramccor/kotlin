/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.tests.compilation.model

import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.tests.compilation.BaseCompilationTest
import org.jetbrains.kotlin.buildtools.api.v2.KotlinToolchain
import org.jetbrains.kotlin.buildtools.api.v2.internal.compat.KotlinToolchainV1Adapter
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import java.util.stream.Stream

class DefaultStrategyAgnosticCompilationTestArgumentProvider : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
        return namedStrategyArguments().map { Arguments.of(it) }.stream()
    }

    companion object {
        fun namedStrategyArguments(): List<Arguments> {
            val kotlinToolchain = KotlinToolchain.loadImplementation(BaseCompilationTest::class.java.classLoader)
            val kotlinToolchainV1Adapter =
                KotlinToolchainV1Adapter(CompilationService.loadImplementation(BaseCompilationTest::class.java.classLoader))
            val v1Args = listOf(
                named("[v1]", kotlinToolchainV1Adapter) to named("in-process", kotlinToolchainV1Adapter.createInProcessExecutionPolicy()),
                named("[v1]", kotlinToolchainV1Adapter) to named("within daemon", kotlinToolchainV1Adapter.createDaemonExecutionPolicy())
            )
            val v2Args = if (kotlinToolchainV1Adapter::class == kotlinToolchain::class) {
                emptyList()
            } else {
                listOf(
                    named("[v2]", kotlinToolchain) to named("in-process", kotlinToolchain.createInProcessExecutionPolicy()),
                    named("[v2]", kotlinToolchain) to named("within daemon", kotlinToolchain.createDaemonExecutionPolicy())
                )
            }

            return (v1Args + v2Args).map { Arguments.of(it.first, it.second) }
        }
    }
}