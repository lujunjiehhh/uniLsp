package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.toUElement

/**
 * Phase 10: Rename Refactoring Provider (T010)
 *
 * 提供 LSP rename 功能的 IntelliJ 集成实现。 使用 ReferencesSearch 查找引用并生成 WorkspaceEdit。
 */
class RenameProvider(private val project: Project) {
    private val log = logger<RenameProvider>()

    /**
     * 预检重命名 - 验证符号可重命名性
     *
     * @return PrepareRenameResult 包含 range 和 placeholder，若不可重命名返回 null
     */
    fun prepareRename(file: PsiFile, position: Position): PrepareRenameResult? =
        ReadAction.compute<PrepareRenameResult?, RuntimeException> {
            try {
                val document =
                    PsiDocumentManager.getInstance(project).getDocument(file)
                        ?: return@compute null
                val offset = document.getLineStartOffset(position.line) + position.character

                // 查找位置处的元素
                val element = file.findElementAt(offset) ?: return@compute null
                val namedElement = findNamedElement(element) ?: return@compute null

                // 检查是否可重命名
                if (!isRenameable(namedElement)) {
                    log.debug("Element is not renameable: ${namedElement.javaClass.simpleName}")
                    return@compute null
                }

                // 获取名称和范围
                val name = getElementName(namedElement) ?: return@compute null
                val nameRange = getNameRange(namedElement, document) ?: return@compute null

                log.debug("prepareRename: found renameable element '$name'")
                PrepareRenameResult(range = nameRange, placeholder = name)
            } catch (e: Exception) {
                log.warn("Error in prepareRename", e)
                null
            }
        }

    /**
     * 执行重命名 - 生成 WorkspaceEdit
     *
     * @return WorkspaceEdit 包含所有需要修改的位置，若失败返回 null
     */
    fun rename(file: PsiFile, position: Position, newName: String): RenameResult? =
        ReadAction.compute<RenameResult?, RuntimeException> {
            try {
                val document =
                    PsiDocumentManager.getInstance(project).getDocument(file)
                        ?: return@compute null
                val offset = document.getLineStartOffset(position.line) + position.character

                // 查找位置处的元素
                val element = file.findElementAt(offset) ?: return@compute null
                val namedElement = findNamedElement(element) ?: return@compute null

                // 验证可重命名
                if (!isRenameable(namedElement)) {
                    return@compute RenameResult.Error("Element is not renameable")
                }

                // 检查冲突
                val conflict = checkConflicts(namedElement, newName)
                if (conflict != null) {
                    return@compute RenameResult.Error(conflict)
                }

                // 收集所有引用位置
                val edits = mutableMapOf<String, MutableList<TextEdit>>()

                // 1. 添加声明位置的修改
                val declRange = getNameRange(namedElement, document)
                if (declRange != null) {
                    val uri =
                        file.virtualFile?.url?.replace("file://", "file:///")
                            ?: return@compute null
                    edits.getOrPut(uri) { mutableListOf() }.add(TextEdit(declRange, newName))
                }

                // 2. 查找所有引用并添加修改
                val scope = GlobalSearchScope.projectScope(project)
                val references = ReferencesSearch.search(namedElement, scope).findAll()

                for (reference in references) {
                    val refElement = reference.element
                    val refFile = refElement.containingFile ?: continue
                    val refDoc =
                        PsiDocumentManager.getInstance(project).getDocument(refFile)
                            ?: continue
                    val refUri =
                        refFile.virtualFile?.url?.replace("file://", "file:///") ?: continue

                    val refRange = getElementRange(refElement, refDoc)
                    if (refRange != null) {
                        edits
                            .getOrPut(refUri) { mutableListOf() }
                            .add(TextEdit(refRange, newName))
                    }
                }

                log.info(
                    "rename: generated ${edits.values.sumOf { it.size }} edits across ${edits.size} files"
                )
                RenameResult.Success(WorkspaceEdit(changes = edits))
            } catch (e: Exception) {
                log.error("Error in rename", e)
                RenameResult.Error(e.message ?: "Unknown error")
            }
        }

    /** 查找父级命名元素（PsiNamedElement） */
    private fun findNamedElement(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            // 尝试使用 UAST 获取统一表示
            val uElement = current.toUElement()
            if (uElement is UDeclaration) {
                return current
            }
            // 回退到 PsiNamedElement
            if (current is PsiNamedElement && current.name != null) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /** 检查元素是否可重命名 */
    private fun isRenameable(element: PsiElement): Boolean {
        // 必须是命名元素
        if (element !is PsiNamedElement) return false

        // 必须可写（不是外部库）
        if (!element.isWritable) {
            log.debug("Element is not writable (external library)")
            return false
        }

        // 检查是否来自库
        val virtualFile = element.containingFile?.virtualFile
        if (virtualFile != null) {
            val scope = GlobalSearchScope.projectScope(project)
            if (!scope.contains(virtualFile)) {
                log.debug("Element is from external library")
                return false
            }
        }

        return true
    }

    /** 检查重命名冲突 - 使用 UAST 支持 Java/Kotlin */
    private fun checkConflicts(element: PsiElement, newName: String): String? {
        // 使用 UAST 查找包含的类
        val uClass = element.parent?.toUElement() as? UClass
        val psiClass = uClass?.javaPsi ?: (element.parent as? PsiClass)

        if (psiClass != null) {
            // 检查类成员冲突
            for (member in psiClass.allMethods) {
                if (member.name == newName && member != element) {
                    return "A method named '$newName' already exists in this class"
                }
            }
            for (field in psiClass.allFields) {
                if (field.name == newName && field != element) {
                    return "A field named '$newName' already exists in this class"
                }
            }
        }
        return null
    }

    /** 获取元素名称 */
    private fun getElementName(element: PsiElement): String? {
        return when (element) {
            is PsiNamedElement -> element.name
            else -> null
        }
    }

    /** 获取元素名称范围 */
    private fun getNameRange(
        element: PsiElement,
        document: com.intellij.openapi.editor.Document
    ): Range? {
        val nameIdentifier =
            when (element) {
                is PsiNameIdentifierOwner -> element.nameIdentifier
                else -> null
            }

        val targetElement = nameIdentifier ?: element
        return getElementRange(targetElement, document)
    }

    /** 获取元素范围 */
    private fun getElementRange(
        element: PsiElement,
        document: com.intellij.openapi.editor.Document
    ): Range? {
        val textRange = element.textRange ?: return null

        val startLine = document.getLineNumber(textRange.startOffset)
        val startChar = textRange.startOffset - document.getLineStartOffset(startLine)
        val endLine = document.getLineNumber(textRange.endOffset)
        val endChar = textRange.endOffset - document.getLineStartOffset(endLine)

        return Range(
            start = Position(line = startLine, character = startChar),
            end = Position(line = endLine, character = endChar)
        )
    }
}

/** Rename 操作结果 */
sealed class RenameResult {
    data class Success(val edit: WorkspaceEdit) : RenameResult()
    data class Error(val message: String) : RenameResult()
}
