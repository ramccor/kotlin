/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.decompiler.psi.text

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.decompiler.stub.COMPILED_DEFAULT_INITIALIZER
import org.jetbrains.kotlin.analysis.decompiler.stub.COMPILED_DEFAULT_PARAMETER_VALUE
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.stubs.KotlinFileStub
import org.jetbrains.kotlin.psi.stubs.StubUtils
import org.jetbrains.kotlin.psi.stubs.impl.KotlinModifierListStubImpl
import org.jetbrains.kotlin.renderer.render

private const val DECOMPILED_CODE_COMMENT = "/* compiled code */"
private const val DECOMPILED_COMMENT_FOR_PARAMETER = "/* = compiled code */"
private const val FLEXIBLE_TYPE_COMMENT = "/* platform type */"
private const val DECOMPILED_CONTRACT_STUB = "contract { /* compiled contract */ }"

// TODO: change indentation to 2 spaces
@OptIn(IntellijInternalApi::class)
internal fun buildDecompiledText(fileStub: KotlinFileStub): String = PrettyPrinter(indentSize = 4).apply {
    appendLine("// IntelliJ API Decompiler stub source generated from a class file")
    appendLine("// Implementation of methods is not available")
    appendLine()

    val packageFqName = fileStub.getPackageFqName()
    if (!packageFqName.isRoot) {
        append("package ")
        appendLine(packageFqName.render())
        appendLine()
    }

    // The visitor is declared as local to capture the pretty printer as a context
    val visitor = object : KtVisitorVoid() {
        // A workaround to access the object in a nested context
        private inline val explicitThis get() = this

        override fun visitClassOrObject(classOrObject: KtClassOrObject) {
            withSuffix(" ") { classOrObject.modifierList?.accept(this) }
            when (classOrObject) {
                is KtObjectDeclaration -> append("object")
                is KtClass -> when {
                    classOrObject.isInterface() -> append("interface")
                    else -> append("class")
                }
            }

            withPrefix(" ") {
                // TODO: read via stubs to handle the case with explicitly named companion `companion object Companion {}`
                // classOrObject.stub?.name?.let { append(it.quoteIfNeeded()) }
                val name = classOrObject.name?.quoteIfNeeded()
                if (classOrObject !is KtObjectDeclaration || !classOrObject.isCompanion() || name != "Companion") {
                    append(name)
                }
            }

            classOrObject.typeParameterList?.accept(this)
            withPrefix(" ") { classOrObject.primaryConstructor?.accept(this) }
            withPrefix(" : ") {
                classOrObject.getSuperTypeList()?.accept(this)
            }

            withPrefix(" ") { classOrObject.typeConstraintList?.accept(this) }
            appendLine(" {")
            withIndent {
                val (enumEntries, members) = if (classOrObject is KtClass && classOrObject.isEnum()) {
                    classOrObject.declarations.partition { it is KtEnumEntry }
                } else {
                    emptyList<KtDeclaration>() to classOrObject.declarations
                }

                withSuffix("\n") {
                    "\n\n".separated(
                        { printCollectionIfNotEmpty(enumEntries, separator = ",\n\n", postfix = ";") { it.accept(explicitThis) } },
                        { printCollectionIfNotEmpty(members, separator = "\n\n") { it.accept(explicitThis) } },
                    )
                }
            }
            append('}')
        }

        override fun visitEnumEntry(enumEntry: KtEnumEntry) {
            withSuffix(" ") { enumEntry.modifierList?.accept(this) }
            append(enumEntry.name?.quoteIfNeeded())
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            withSuffix(" ") { function.modifierList?.accept(this) }
            append("fun ")
            withSuffix(" ") { function.typeParameterList?.accept(this) }
            withSuffix(".") { function.receiverTypeReference?.accept(this) }

            append(function.name?.quoteIfNeeded())
            function.valueParameterList?.accept(this)
            withPrefix(": ") { function.typeReference?.accept(this) }

            if (function.hasBody()) {
                append(" { ")
                if (function.mayHaveContract()) {
                    append(DECOMPILED_CONTRACT_STUB)
                    append("; ")
                }
                append(DECOMPILED_CODE_COMMENT)
                append(" }")
            }
        }

        override fun visitTypeAlias(typeAlias: KtTypeAlias) {
            withSuffix(" ") { typeAlias.modifierList?.accept(this) }
            append("typealias ")
            append(typeAlias.name?.quoteIfNeeded())
            typeAlias.typeParameterList?.accept(this)
            withPrefix(" = ") { typeAlias.getTypeReference()?.accept(this) }
        }

        override fun visitConstructor(constructor: KtConstructor<*>) {
            withSuffix(" ") { constructor.modifierList?.accept(this) }
            append("constructor")
            constructor.valueParameterList?.accept(this)
        }

        override fun visitTypeParameter(parameter: KtTypeParameter) {
            withSuffix(" ") { parameter.modifierList?.accept(this) }
            append(parameter.name?.quoteIfNeeded())
            withPrefix(" : ") { parameter.extendsBound?.accept(this) }
        }

        override fun visitTypeReference(typeReference: KtTypeReference) {
            withSuffix(" ") { typeReference.modifierList?.accept(this) }
            append(typeReference.getTypeText())
        }

        override fun visitProperty(property: KtProperty) {
            withSuffix(" ") { property.modifierList?.accept(this) }

            if (property.isVar) {
                append("var ")
            } else {
                append("val ")
            }

            withSuffix(" ") { property.typeParameterList?.accept(this) }
            withSuffix(".") { property.receiverTypeReference?.accept(this) }

            append(property.name?.quoteIfNeeded())
            withPrefix(": ") { property.typeReference?.accept(this) }

            val hasInitializerOrDelegate = checkIfPrinted {
                when {
                    property.hasInitializer() -> append(" = ")
                    property.hasDelegate() -> append(" by ")
                }
            }

            if (hasInitializerOrDelegate) {
                append(COMPILED_DEFAULT_INITIALIZER)
            }

            if (!property.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                append(" $DECOMPILED_CODE_COMMENT")
            }

            property.stub?.hasBackingField?.let {
                append(' ')
                append(StubUtils.HAS_BACKING_FIELD_COMMENT_PREFIX)
                append(it.toString())
                append(" */")
            }

            withIndent {
                printCollectionIfNotEmpty(property.accessors, prefix = "\n", separator = "\n") {
                    it.accept(explicitThis)
                }
            }
        }

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
            withSuffix(" ") { accessor.modifierList?.accept(this) }
            if (accessor.isGetter) {
                append("get")
            } else {
                append("set")
            }

            accessor.parameterList?.accept(this)
            withPrefix(": ") { accessor.returnTypeReference?.accept(this) }
            if (accessor.hasBody()) {
                append(" {$DECOMPILED_CODE_COMMENT }") // TODO: add space before body
            }
        }

        override fun visitParameterList(list: KtParameterList) {
            printCollection(list.parameters, prefix = "(", postfix = ")") {
                it.accept(explicitThis)
            }
        }

        override fun visitParameter(parameter: KtParameter) {
            withSuffix(" ") { parameter.modifierList?.accept(this) }
            append(parameter.name?.quoteIfNeeded())
            append(": ")
            parameter.typeReference?.accept(this)
            if (parameter.hasDefaultValue()) {
                append(" = $COMPILED_DEFAULT_PARAMETER_VALUE")
            }
        }

        override fun visitTypeParameterList(list: KtTypeParameterList) {
            printCollection(list.parameters, prefix = "<", postfix = ">") {
                it.accept(explicitThis)
            }
        }

        override fun visitTypeConstraintList(list: KtTypeConstraintList) {
            append("where ")
            printCollection(list.constraints, separator = ", ") {
                it.accept(explicitThis)
            }
        }

        override fun visitTypeConstraint(constraint: KtTypeConstraint) {
            withSuffix(" ") { printAnnotations(constraint) }
            constraint.subjectTypeParameterName?.accept(this)
            append(" : ")
            constraint.boundTypeReference?.accept(this)
        }

        override fun visitModifierList(list: KtModifierList) {
            " ".separated(
                { printAnnotations(list) },
                { printModifiers(list) },
            )
        }

        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            if (expression is KtSimpleNameExpression) {
                append(expression.getReferencedName())
            } else {
                visitElement(expression)
            }
        }

        fun printAnnotations(container: KtAnnotationsContainer) {
            printCollectionIfNotEmpty(container.annotationEntries, separator = " ") {
                it.accept(explicitThis)
            }
        }

        override fun visitSuperTypeList(list: KtSuperTypeList) {
            printCollection(list.entries) {
                it.accept(explicitThis)
            }
        }

        override fun visitSuperTypeListEntry(specifier: KtSuperTypeListEntry) {
            specifier.typeReference?.accept(this)
        }

        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
            append('@')
            annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget()?.let {
                append(it.renderName)
                append(':')
            }

            annotationEntry.typeReference?.accept(this)
        }

        override fun visitElement(element: PsiElement) {
            append("/* !${element::class.simpleName}! */")
            super.visitElement(element)
        }

        fun printModifiers(list: KtModifierList) {
            val stub = list.stub as? KotlinModifierListStubImpl ?: return
            if (!stub.hasAnyModifier()) return

            var hadValue = false
            for (modifier in KtTokens.MODIFIER_KEYWORDS_ARRAY) {
                if (!stub.hasModifier(modifier)) continue
                if (hadValue) {
                    append(" ")
                } else {
                    hadValue = true
                }

                append(modifier.value)
            }
        }
    }

    // Psi for files is not guaranteed to present as it has to be set explicitly (see PsiFileStubImpl)
    // On the other side, declarations build psi on demand, so they can be used directly to simplify the logic
    val declarations = fileStub.getChildrenByType(KtFile.FILE_DECLARATION_TYPES, KtDeclaration.ARRAY_FACTORY).asList()
    printCollectionIfNotEmpty(declarations, separator = "\n\n", postfix = "\n") {
        it.accept(visitor)
    }
}.toString()
    .replace("final const", "const final") // TODO: drop this hack when old and new test data are aligned
