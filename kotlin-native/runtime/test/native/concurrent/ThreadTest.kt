/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.native.concurrent

import kotlin.concurrent.AtomicLong
import kotlin.concurrent.atomics.*
import kotlin.native.concurrent.*
import kotlin.test.*

@OptIn(ExperimentalAtomicApi::class)
class ThreadTest {

    @Test
    fun concurrentExecution() {
        val otherTid = AtomicLong(0L)
        val threadReady = AtomicBoolean(false)

        startThread {
            otherTid.value = currentThreadId().toLong()
            threadReady.store(true)
        }

        while (!threadReady.load()) {
        }

        assertNotEquals(currentThreadId().toLong(), otherTid.value)
    }
}
