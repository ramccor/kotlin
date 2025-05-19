/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.projectStructure

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.caches.getOrPut
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
     */
    private val virtualFileContainsCache = Caffeine.newBuilder()
        .maximumSize(100)
        .build<VirtualFile, Boolean>()

    override fun getProject(): Project? = searchScope.project

    override fun isSearchInModuleContent(aModule: Module): Boolean = searchScope.isSearchInModuleContent(aModule)

    override fun isSearchInLibraries(): Boolean = searchScope.isSearchInLibraries

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

    private fun cachedSearchScopeContains(virtualFile: VirtualFile): Boolean =
        virtualFileContainsCache.getOrPut(virtualFile) { searchScope.contains(virtualFile) }

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
}
