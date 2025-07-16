/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.decompiler.psi.file

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.StubTreeLoader
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinDecompiledFileViewProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.text.DecompiledText
import org.jetbrains.kotlin.analysis.decompiler.psi.text.buildDecompiledText
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsClassFinder
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.stubs.KotlinFileStub
import org.jetbrains.kotlin.utils.concurrent.block.LockedClearableLazyValue

open class KtDecompiledFile(
    private val provider: KotlinDecompiledFileViewProvider,
    buildDecompiledText: (VirtualFile) -> DecompiledText
) : KtFile(provider, true) {

    private val decompiledText = LockedClearableLazyValue(Any()) {
        val stubTree = ClsClassFinder.allowMultifileClassPart {
            StubTreeLoader.getInstance().readOrBuild(
                /* project = */ project,
                /* vFile = */ provider.virtualFile,
                /* psiFile = */ null,
            )
        }

        val fileStub = stubTree?.root as? KotlinFileStub
        if (fileStub != null) {
            buildDecompiledText(fileStub)
        } else {
            val cause = if (stubTree == null) {
                "stub tree is not found"
            } else {
                "non-Kotlin stub tree (${stubTree::class.simpleName})"
            }

            """
                // Could not decompile the file: $cause
                // Please report an issue: https://kotl.in/issue
            """.trimIndent()
        }
    }

    override fun getText(): String? {
        return decompiledText.get()
    }

    override fun onContentReload() {
        super.onContentReload()

        provider.content.drop()
        decompiledText.drop()
    }

}
