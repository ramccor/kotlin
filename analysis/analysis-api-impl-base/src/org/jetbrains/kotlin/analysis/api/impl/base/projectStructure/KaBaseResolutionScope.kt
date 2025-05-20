/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.projectStructure

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.ConcurrentStatsCounter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.caches.withStatsCounter
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaResolutionScope
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.psi.KtFile

/**
 * [KaBaseResolutionScope] is not intended to be created manually. It's the responsibility of [KaResolutionScopeProvider][org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaResolutionScopeProvider].
 * Please use [Companion.forModule] instead.
 *
 * @param analyzableModules The set of modules whose declarations can be analyzed from the [useSiteModule], including the use-site module
 *  itself.
 */
internal class KaBaseResolutionScope(
    private val useSiteModule: KaModule,
    private val searchScope: GlobalSearchScope,
    private val analyzableModules: Set<KaModule>,
) : KaResolutionScope() {
    /**
     * The cache has a major impact on Code Analysis through [canBeAnalysed][org.jetbrains.kotlin.analysis.api.components.KaAnalysisScopeProvider.canBeAnalysed],
     * with a hit rate of >99% in local experiments. However, the cache has a very low hit rate when the scope is used for index accesses,
     * as indices are likely to throw many different virtual files at the cache. This is compared to a much more limited set of virtual
     * files in Code Analysis. As such, the cache should only be used in [contains] functions which aren't used by indices.
     *
     * ONLY CACHES HAPPY VIRTUAL FILES
     */
    private val virtualFileContainsCache = Caffeine.newBuilder()
        .maximumSize(100)
//        .withStatsCounter(statsCounter)
        .build<VirtualFile, Boolean>()

    private class LastVirtualFileData(val virtualFile: VirtualFile, val isFirstEncounter: Boolean)

    private val lastVirtualFileCache = ThreadLocal<LastVirtualFileData>()

    override fun getProject(): Project? = searchScope.project

    override fun isSearchInModuleContent(aModule: Module): Boolean = searchScope.isSearchInModuleContent(aModule)

    override fun isSearchInLibraries(): Boolean = searchScope.isSearchInLibraries

    override fun contains(file: VirtualFile): Boolean {
        // As noted above, we don't want to use the virtual file cache for index accesses.
        return searchScope.contains(file) || isAccessibleDanglingFile(file)
//        return cachedSearchScopeContains(file) || isAccessibleDanglingFile(file)
    }

    override fun contains(element: PsiElement): Boolean {
        /**
         * We check the *virtual file* here instead of calling [org.jetbrains.kotlin.psi.psiUtil.contains] on the search scope directly.
         * This is because `psiUtil.contains` queries the search scope with the element's *original file*, so search scope membership of any
         * dangling file element is checked based on the dangling file's original file. But this is incorrect for resolution scope checks:
         * The Analysis API separates dangling files and original files into separate modules. A dangling file element should not be
         * analyzable in its context module's session.
         */
        val psiFile = element.containingFile
        val virtualFile = psiFile.virtualFile
        return virtualFile != null && cachedSearchScopeContains(virtualFile) || isAccessibleDanglingFile(psiFile)
    }

    private fun cachedSearchScopeContains(virtualFile: VirtualFile): Boolean {
//        return virtualFileContainsCache.getOrPut(virtualFile) { searchScope.contains(virtualFile) }
//        return virtualFileContainsCache.get(virtualFile) { searchScope.contains(virtualFile) } == true

//        val lastVirtualFileData = lastVirtualFileCache.get()?.get()
//        if (lastVirtualFileData != null && lastVirtualFileData.virtualFile == virtualFile) {
//            hits += 1
//            return lastVirtualFileData.isContained
//        }
//
//        misses += 1
//
//        // Problem: This adds a thread local access and multi-object allocation to every cache miss. :/
//        val isContained = searchScope.contains(virtualFile)
//        lastVirtualFileCache.set(WeakReference(LastVirtualFileData(virtualFile, isContained)))
//        return isContained

        val lastVirtualFileData = lastVirtualFileCache.get()
        if (lastVirtualFileData != null && lastVirtualFileData.virtualFile == virtualFile) {
            // I.e., including this hit, we've seen this thing 2 times now.
            if (lastVirtualFileData.isFirstEncounter) {
                virtualFileContainsCache.put(virtualFile, true)
                lastVirtualFileCache.set(LastVirtualFileData(virtualFile, isFirstEncounter = false))
            }
//            // We don't want to update the cache needlessly, as we only need to know that we've seen it 2 times at most. This allows us to
//            // avoid putting a value in the cache every time (timesSeen == 1).
//            if (lastVirtualFileData.timesSeen < 2) {
//
//            }
//            hits += 1
//            threadLocalHits += 1
            return true
        } else if (virtualFileContainsCache.getIfPresent(virtualFile) == true) {
            // TODO (marco): Big disadvantage here: We hit the virtual file cache every time, even in completion, where it's likely just
            //  wasted time. Can we avoid this in any way?
            // We can set this to `isFirstEncounter = false` immediately since the virtual file has already been added to the contains
            // cache.
            lastVirtualFileCache.set(LastVirtualFileData(virtualFile, isFirstEncounter = false))
//            hits += 1
            return true
        }

//        misses += 1

        val isContained = searchScope.contains(virtualFile)
        if (isContained) {
            lastVirtualFileCache.set(LastVirtualFileData(virtualFile, isFirstEncounter = true))
        }
        return isContained
    }

    private fun isAccessibleDanglingFile(psiFile: PsiFile): Boolean {
        val ktFile = psiFile as? KtFile ?: return false
        if (!ktFile.isDangling) {
            return false
        }
        val module = ktFile.contextModule ?: KaModuleProvider.getModule(useSiteModule.project, ktFile, useSiteModule)
        return module.isAccessibleFromUseSiteModule()
    }

    private fun isAccessibleDanglingFile(virtualFile: VirtualFile): Boolean {
        return virtualFile.analysisContextModule?.isAccessibleFromUseSiteModule() == true
    }

    private fun KaModule.isAccessibleFromUseSiteModule(): Boolean = this in analyzableModules

    override val underlyingSearchScope: GlobalSearchScope
        get() = searchScope

    override fun toString(): String = "Resolution scope for '$useSiteModule'. Underlying search scope: '$searchScope'"

//    companion object {
//        var threadLocalHits: Long = 0
//        var hits: Long = 0
//        var misses: Long = 0
//
//        val statsCounter = ConcurrentStatsCounter()
//    }
}
