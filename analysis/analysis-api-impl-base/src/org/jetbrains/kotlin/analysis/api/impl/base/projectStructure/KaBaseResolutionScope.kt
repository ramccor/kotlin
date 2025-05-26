/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
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
    // TODO (marco): Can we avoid volatile here?
    //  - A volatile is expensive to write to (not read from) and we have to write frequently, especially in workloads where the cache isn't
    //    as effective.
    //  - It's not a problem if different threads see different versions of the variable.
    //  - Non-volatile kinda simulates a thread local on a microscale, where only the local thread sees the new value and other threads
    //    still see the old value.
    //  In summary: `volatile` ensures that writes are immediately made visible to other threads, but (1) it's OK if different threads see
    //  different values of the cache (all virtual files ever cached here are valid values) and (2) we'd actually like writes not to be made
    //  visible to other threads immediately, since each thread might have its own file to analyze. A non-volatile variable might better
    //  utilize the L1 cache of a CPU core..
    /**
     * TODO (marco): Rewrite comment. Note that the cache is optimized for a high hit rate in Code Analysis while having a low passthrough
     *  overhead for other workloads where the hit rate is low.
     *
     * The cache has a major impact on Code Analysis through [canBeAnalysed][org.jetbrains.kotlin.analysis.api.components.KaAnalysisScopeProvider.canBeAnalysed],
     * with a hit rate of >99% in local experiments. However, the cache has a very low hit rate when the scope is used for index accesses,
     * as indices are likely to throw many different virtual files at the cache. This is compared to a much more limited set of virtual
     * files in Code Analysis. As such, the cache should only be used in [contains] functions which aren't used by indices.
     *
     * A negative value means that the cache is empty at the specific index. The cache also only stores virtual files that are contained in
     * the resolution scope, as this applies to the vast majority of PSI elements check via `canBeAnalysed`.
     */
    private val virtualFileCache = IntArray(32) { -1 }

    override fun contains(file: VirtualFile): Boolean {
        // As noted above, we don't want to use the virtual file cache for index accesses.
        return searchScope.contains(file) || isAccessibleDanglingFile(file)
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
        // The cache depends on virtual file IDs. It can also only store *positive* virtual file IDs. "Real" virtual files are guaranteed to
        // have positive IDs.
        // TODO (marco): Instead of `< 0`, we can also choose one specific `Int` value for "no value".
        val id = (virtualFile as? VirtualFileWithId)?.id
        if (id == null || id < 0) {
            return searchScope.contains(virtualFile)
        }

        // Based on the ID, each virtual file is cached in a predetermined slot. This can lead to collisions if we're unlucky, but it also
        // means that checking the cache and writing to it barely has any overhead. A smarter caching strategy would impose a larger
        // overhead as well as the need for synchronization.
        val index = id % virtualFileCache.size
        if (virtualFileCache[index] == id) {
            return true
        } else {
            val isContained = searchScope.contains(virtualFile)
            if (isContained) {
                virtualFileCache[index] = id
            }
            return isContained
        }
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

    override fun getProject(): Project? = searchScope.project

    override fun isSearchInModuleContent(aModule: Module): Boolean = searchScope.isSearchInModuleContent(aModule)

    override fun isSearchInLibraries(): Boolean = searchScope.isSearchInLibraries

    override fun toString(): String = "Resolution scope for '$useSiteModule'. Underlying search scope: '$searchScope'"
}
