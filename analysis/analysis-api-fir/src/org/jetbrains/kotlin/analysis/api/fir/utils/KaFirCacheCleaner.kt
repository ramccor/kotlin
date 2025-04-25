/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.platform.KaCachedService
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionInvalidationService
import org.jetbrains.kotlin.analysis.low.level.api.fir.statistics.LLStatisticsService
import org.jetbrains.kotlin.analysis.low.level.api.fir.statistics.domains.LLAnalysisSessionStatistics
import org.jetbrains.kotlin.utils.exceptions.rethrowIntellijPlatformExceptionIfNeeded
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * A facility that cleans up all low-level resolution caches per request.
 */
internal interface KaFirCacheCleaner {
    /**
     * This method must be called before the [KaSession] is obtained (to be used later in an [analyze] block).
     * If the method is called, [exitAnalysis] must also be called from the same thread right after the block finishes executing, or
     * if analysis fails with some error (even if the block didn't start executing yet).
     */
    fun enterAnalysis()

    /**
     * This method must be called right after an [analyze] block finishes executing.
     * It is a counterpart for [enterAnalysis].
     */
    fun exitAnalysis()

    /**
     * Schedule analysis cache cleanup.
     * The actual cleanup may happen immediately (if it's possible to do so), or some time later.
     *
     * Consequent calls to [scheduleCleanup] are permitted (and ignored if a cleanup is already scheduled).
     */
    fun scheduleCleanup()
}

/**
 * An empty implementation of a cache cleaner – no additional cleanup is performed.
 * Can be used as a drop-in substitution for [KaFirStopWorldCacheCleaner] if forceful cache cleanup is disabled.
 */
internal object KaFirNoOpCacheCleaner : KaFirCacheCleaner {
    override fun enterAnalysis() {}
    override fun exitAnalysis() {}
    override fun scheduleCleanup() {}
}

/**
 * A deferred implementation of a cache cleaner.
 *
 * The implementation schedules a write action to perform the cleanup as soon as possible.
 */
internal class KaFirDeferredCacheCleaner(private val project: Project) : KaFirCacheCleaner {
    companion object {
        private val LOG = logger<KaFirDeferredCacheCleaner>()
    }

    @KaCachedService
    private val analysisSessionStatistics: LLAnalysisSessionStatistics? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LLStatisticsService.getInstance(project)?.analysisSessions
    }

    @KaCachedService
    private val invalidationService: LLFirSessionInvalidationService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LLFirSessionInvalidationService.getInstance(project)
    }

    override fun enterAnalysis() {}

    override fun exitAnalysis() {}

    override fun scheduleCleanup() {
        LOG.trace("K2 cache cleanup is scheduled")
        val cleanupScheduleMs = System.currentTimeMillis()

        /**
         * [ModalityState.any] is used here to invalidate caches no matter what.
         * Otherwise, we might end up waiting for closing all modal dialogs,
         * which effectively disables the cleaner.
         * For instance, we will wait for find usages to be completed,
         * but we need the cleaner to be called during the find usages operation.
         *
         * [performCleanup] guaranties that no model-changing activity will be called inside.
         */
        ApplicationManager.getApplication().invokeLater(
            /* runnable = */ { performCleanup(cleanupScheduleMs) },
            /* state = */ ModalityState.any(),
            /* expired = */ project.disposed,
        )
    }

    private fun performCleanup(cleanupScheduleMs: Long) {
        LOG.trace("K2 cache cleanup requests write action")

        ApplicationManager.getApplication().runWriteAction {
            performCleanup(cleanupScheduleMs, invalidationService, analysisSessionStatistics, LOG)
        }
    }
}

/**
 * A stop-the-world implementation of a cache cleaner.
 *
 * It's impossible to clean up caches at a random point, as ongoing analyses will fail because their use-site sessions will be invalidated.
 * So [KaFirStopWorldCacheCleaner] registers cleanup events and waits until all analysis blocks finish executing.
 * Once a cleanup is requested, the class also prevents all new analysis blocks from running until it's complete (see the [cleanupLatch]).
 * If there is no ongoing analysis, though, caches can be cleaned up immediately.
 */
internal class KaFirStopWorldCacheCleaner(private val project: Project) : KaFirCacheCleaner {
    private companion object {
        private val LOG = logger<KaFirStopWorldCacheCleaner>()

        private const val CACHE_CLEANER_LOCK_TIMEOUT_MS = 50L
    }

    private val lock = Any()

    @KaCachedService
    private val analysisSessionStatistics: LLAnalysisSessionStatistics? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LLStatisticsService.getInstance(project)?.analysisSessions
    }

    @KaCachedService
    private val invalidationService: LLFirSessionInvalidationService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LLFirSessionInvalidationService.getInstance(project)
    }

    /**
     * The number of non-nested analyses running in parallel. In other words, the number of analyses in all threads.
     * Non-nested means that `analyze {}` blocks inside outer `analyze {}` blocks (both directly or indirectly) are not counted.
     *
     * [KaFirStopWorldCacheCleaner] counts ongoing sessions, and cleans caches up as soon as the last analysis block finishes execution.
     */
    @Volatile
    private var analyzerCount: Int = 0

    /**
     * The number of ongoing analyses in the current thread.
     * '0' means there is no ongoing analysis. '2' means there are two 'analyze {}' blocks in the stack.
     */
    private val analyzerDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    private val activeThreads = ConcurrentHashMap.newKeySet<Thread>()

    /**
     * A latch preventing newly created analyses from running until the cache cleanup is complete.
     * [cleanupLatch] is `null` when there is no scheduled (postponed) cleanup.
     */
    @Volatile
    private var cleanupLatch: CountDownLatch? = null

    /**
     * A timestamp when the currently postponed cleanup is scheduled, or an arbitrary value if no cleanup is scheduled.
     */
    @Volatile
    private var cleanupScheduleMs: Long = 0

    /**
     * `true` if there is an ongoing analysis in the current thread.
     * It means that a newly registered analysis block will be a nested one.
     */
    private val hasOngoingAnalysis: Boolean
        get() = analyzerDepth.get() > 0

    override fun enterAnalysis() {
        // Avoid blocking nested analyses. This would break the logic, as the outer analysis will never complete
        // (because it will wait for the nested, this new one, to complete first). So we will never get to the point when all analyses
        // are done and clean the caches.
        if (hasOngoingAnalysis) {
            incAnalysisDepth()
            return
        }

        val existingLatch = cleanupLatch
        if (existingLatch != null) {
            // If there's an ongoing cleanup request, wait until it's complete
            do {
                ProgressManager.checkCanceled()
                if (activeThreads.none { it.state == Thread.State.RUNNABLE }) break
            } while (!existingLatch.await(CACHE_CLEANER_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        }

        synchronized(lock) {
            // Register a top-level analysis block
            analyzerCount += 1
            activeThreads.add(Thread.currentThread())
        }

        incAnalysisDepth()
    }

    override fun exitAnalysis() {
        decAnalysisDepth()

        // Ignore nested analyses (as in 'enterAnalysis')
        if (hasOngoingAnalysis) {
            return
        }

        synchronized(lock) {
            // Unregister a top-level analysis block
            analyzerCount -= 1
            activeThreads.remove(Thread.currentThread())

            require(analyzerCount >= 0) { "Inconsistency in analyzer block counter" }

            if (cleanupLatch != null) {
                LOG.trace { "Analysis complete in ${Thread.currentThread()}, $analyzerCount left before the K2 cache cleanup" }
            }

            // Clean up the caches if there's a postponed cleanup, and we have no more analyses
            if (analyzerCount == 0) {
                val existingLatch = cleanupLatch
                if (existingLatch != null) {
                    try {
                        performCleanup()
                    } finally {
                        // Unpause all waiting analyses.
                        // Even if some new block comes before the 'cleanupLatch' is set to `null`, the old latch will be already open.
                        existingLatch.countDown()
                        cleanupLatch = null
                    }
                }
            }
        }
    }

    private fun incAnalysisDepth() {
        analyzerDepth.set(analyzerDepth.get() + 1)
    }

    private fun decAnalysisDepth() {
        val oldValue = analyzerDepth.get()
        assert(oldValue > 0) { "Inconsistency in analysis depth counter" }
        analyzerDepth.set(oldValue - 1)
    }

    override fun scheduleCleanup() {
        synchronized(lock) {
            val existingLatch = cleanupLatch

            // Cleans the caches right away if there is no ongoing analysis or schedules a cleanup for later
            if (analyzerCount == 0) {
                // Here we perform cache invalidation without a read/write action wrapping guarantee.
                // We cannot start a new read/write action here as there might be already a pending write action waiting for 'this' monitor.
                // However, we are still sure no threads can get a session until the cleanup is complete.
                cleanupScheduleMs = System.currentTimeMillis()

                try {
                    performCleanup()
                } finally {
                    if (existingLatch != null) {
                        // Error recovery in case if things went really bad.
                        // Should never happen unless there is some flaw in the algorithm
                        existingLatch.countDown()
                        cleanupLatch = null
                        LOG.error("K2 cache cleanup was expected to happen right after the last analysis block completion")
                    }
                }
            } else if (existingLatch == null) {
                LOG.trace { "K2 cache cleanup scheduled from ${Thread.currentThread()}, $analyzerCount analyses left" }
                cleanupScheduleMs = System.currentTimeMillis()
                cleanupLatch = CountDownLatch(1)
            }
        }
    }

    /**
     * Must be synchronized outside to prevent concurrent cleanups.
     */
    private fun performCleanup() {
        performCleanup(cleanupScheduleMs, invalidationService, analysisSessionStatistics, LOG)
    }
}

/**
 * Cleans all K2 resolution caches.
 *
 * Must be synchronized outside to prevent concurrent cleanups.
 * Must not call any [model-changing][com.intellij.openapi.application.TransactionGuard] activity inside.
 *
 * N.B.: May re-throw exceptions from IJ Platform [rethrowIntellijPlatformExceptionIfNeeded].
 *
 * @param cleanupScheduleMs a timestamp when the currently postponed cleanup is scheduled, or an arbitrary value if no cleanup is scheduled.
 *
 * @see com.intellij.openapi.application.TransactionGuard
 */
private fun performCleanup(
    cleanupScheduleMs: Long,
    invalidationService: LLFirSessionInvalidationService,
    analysisSessionStatistics: LLAnalysisSessionStatistics?,
    logger: Logger,
): Unit = try {
    analysisSessionStatistics?.lowMemoryCacheCleanupInvocationCounter?.add(1)

    val cleanupMs = measureTimeMillis {
        invalidationService.invalidateAll(includeLibraryModules = true)
    }

    val totalMs = System.currentTimeMillis() - cleanupScheduleMs
    logger.trace { "K2 cache cleanup complete from ${Thread.currentThread()} in $cleanupMs ms ($totalMs ms after the request)" }
} catch (e: Throwable) {
    rethrowIntellijPlatformExceptionIfNeeded(e)

    logger.error("Could not clean up K2 caches", e)
}