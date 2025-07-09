/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jvm.compiler.javac

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.jvm.compiler.AbstractLoadJava8Test
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractLoadJava8UsingJavacTest : AbstractLoadJava8Test() {
    override fun registerJavacIfNeeded(environment: KotlinCoreEnvironment) {
        environment.registerJavac()
        environment.configuration.put(JVMConfigurationKeys.USE_JAVAC, true)
    }

    override fun useJavacWrapper() = true

    override fun getExpectedFile(expectedFileName: String): File {
        val differentResultFile = KotlinTestUtils.replaceExtension(File(expectedFileName), "javac.txt")
        if (differentResultFile.exists()) return differentResultFile
        return super.getExpectedFile(expectedFileName)
    }

}
