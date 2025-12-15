package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType

/**
 * Phase 10: Call Hierarchy Provider (T015)
 *
 * 提供 LSP Call Hierarchy 功能的 IntelliJ 集成实现。 使用 ReferencesSearch 查找调用者，遍历方法体查找被调用者。
 */
class CallHierarchyProvider(private val project: Project) {
    private val log = logger<CallHierarchyProvider>()

    private companion object {
        const val MAX_RESULTS = 100
    }

    /** 准备调用层次 - 返回位置处的方法作为 CallHierarchyItem */
    fun prepareCallHierarchy(file: PsiFile, position: Position): List<CallHierarchyItem>? =
        ReadAction.compute<List<CallHierarchyItem>?, RuntimeException> {
            try {
                val document =
                    PsiDocumentManager.getInstance(project).getDocument(file)
                        ?: return@compute null
                val offset =
                    document.getLineStartOffset(position.line) +
                            position.character

                // 查找位置处的元素
                val element = file.findElementAt(offset) ?: return@compute null
                val method = findMethod(element) ?: return@compute null

                val item = methodToItem(method, document) ?: return@compute null
                log.debug("prepareCallHierarchy: found method '${item.name}'")
                listOf(item)
            } catch (e: Exception) {
                log.warn("Error in prepareCallHierarchy", e)
                null
            }
        }

    /** 获取调用者 (incoming calls) - 谁调用了这个方法 使用 visited set 防止循环 */
    fun getIncomingCalls(item: CallHierarchyItem): List<CallHierarchyIncomingCall> =
        ReadAction.compute<List<CallHierarchyIncomingCall>, RuntimeException> {
            try {
                val method = findMethodByItem(item) ?: return@compute emptyList()
                val results = mutableListOf<CallHierarchyIncomingCall>()
                val visited = mutableSetOf<String>()

                val scope = GlobalSearchScope.projectScope(project)
                val references = ReferencesSearch.search(method, scope).findAll()

                log.debug(
                    "getIncomingCalls: found ${references.size} references for method ${method.name}"
                )

                for (reference in references) {
                    if (results.size >= MAX_RESULTS) break

                    val refElement = reference.element

                    // 使用 UAST 向上查找父方法 (支持 Java/Kotlin)
                    val callerMethod = findContainingMethod(refElement)

                    if (callerMethod != null && callerMethod != method) {
                        // 防止重复
                        val key =
                            "${callerMethod.containingClass?.qualifiedName}.${callerMethod.name}"
                        if (visited.contains(key)) continue
                        visited.add(key)

                        val callerDoc =
                            callerMethod.containingFile?.let {
                                PsiDocumentManager.getInstance(
                                    project
                                )
                                    .getDocument(it)
                            }
                                ?: continue

                        val callerItem =
                            methodToItem(callerMethod, callerDoc)
                                ?: continue
                        val refRange =
                            getElementRange(refElement, callerDoc)
                                ?: continue

                        results.add(
                            CallHierarchyIncomingCall(
                                from = callerItem,
                                fromRanges = listOf(refRange)
                            )
                        )
                    }
                }

                log.debug("getIncomingCalls: found ${results.size} callers")
                results
            } catch (e: Exception) {
                log.warn("Error in getIncomingCalls", e)
                emptyList()
            }
        }

    /** 使用 UAST 查找包含元素的方法 (支持 Java/Kotlin) */
    private fun findContainingMethod(element: PsiElement): PsiMethod? {
        var current: PsiElement? = element
        while (current != null) {
            val uElement = current.toUElement()
            if (uElement is UMethod) {
                return uElement.javaPsi
            }
            if (current is PsiMethod) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /** 获取被调用者 (outgoing calls) - 这个方法调用了谁 使用 visited set 防止循环 */
    fun getOutgoingCalls(item: CallHierarchyItem): List<CallHierarchyOutgoingCall> =
        ReadAction.compute<List<CallHierarchyOutgoingCall>, RuntimeException> {
            try {
                val method = findMethodByItem(item) ?: return@compute emptyList()
                val results = mutableListOf<CallHierarchyOutgoingCall>()
                val visited = mutableSetOf<String>()

                val document =
                    method.containingFile?.let {
                        PsiDocumentManager.getInstance(project)
                            .getDocument(it)
                    }
                        ?: return@compute emptyList()

                // 使用 UAST 遍历方法体中的所有调用 (支持 Java/Kotlin)
                val uMethod =
                    method.toUElement() as? UMethod
                        ?: return@compute emptyList()

                uMethod.accept(
                    object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                        override fun visitCallExpression(
                            node: UCallExpression
                        ): Boolean {
                            if (results.size >= MAX_RESULTS) return true

                            val callee = node.resolve()
                            if (callee != null && callee != method) {
                                // 防止重复
                                val key =
                                    "${callee.containingClass?.qualifiedName}.${callee.name}"
                                if (visited.contains(key)) {
                                    return false
                                }
                                visited.add(key)

                                val calleeDoc =
                                    callee.containingFile?.let {
                                        PsiDocumentManager
                                            .getInstance(
                                                project
                                            )
                                            .getDocument(
                                                it
                                            )
                                    }

                                if (calleeDoc != null) {
                                    val calleeItem =
                                        methodToItem(
                                            callee,
                                            calleeDoc
                                        )
                                    val callPsi = node.sourcePsi
                                    val callRange =
                                        if (callPsi != null)
                                            getElementRange(
                                                callPsi,
                                                document
                                            )
                                        else null

                                    if (calleeItem != null &&
                                        callRange !=
                                        null
                                    ) {
                                        results.add(
                                            CallHierarchyOutgoingCall(
                                                to =
                                                    calleeItem,
                                                fromRanges =
                                                    listOf(
                                                        callRange
                                                    )
                                            )
                                        )
                                    }
                                }
                            }
                            return false
                        }
                    }
                )

                log.debug("getOutgoingCalls: found ${results.size} callees")
                results
            } catch (e: Exception) {
                log.warn("Error in getOutgoingCalls", e)
                emptyList()
            }
        }

    /** 查找父级方法 - 使用 UAST 支持 Java/Kotlin */
    private fun findMethod(element: PsiElement): PsiMethod? {
        // 先尝试使用 UAST 查找 (支持 Kotlin)
        val uMethod = element.toUElementOfType<UMethod>()
        if (uMethod != null) {
            return uMethod.javaPsi
        }

        // 向上查找 UMethod
        var current: PsiElement? = element
        while (current != null) {
            val uElement = current.toUElement()
            if (uElement is UMethod) {
                return uElement.javaPsi
            }
            if (current is PsiMethod) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /** 根据 CallHierarchyItem 查找方法 */
    private fun findMethodByItem(item: CallHierarchyItem): PsiMethod? {
        val virtualFile =
            com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                .findFileByUrl(item.uri.replace("file:///", "file://"))
                ?: return null

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        val document =
            PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null

        val offset =
            document.getLineStartOffset(item.selectionRange.start.line) +
                    item.selectionRange.start.character
        val element = psiFile.findElementAt(offset) ?: return null

        return findMethod(element)
    }

    /** 将 PsiMethod 转换为 CallHierarchyItem */
    private fun methodToItem(
        method: PsiMethod,
        document: com.intellij.openapi.editor.Document
    ): CallHierarchyItem? {
        val file = method.containingFile?.virtualFile ?: return null
        val textRange = method.textRange ?: return null
        val nameIdentifier = method.nameIdentifier ?: return null
        val nameRange = nameIdentifier.textRange ?: return null

        val startLine = document.getLineNumber(textRange.startOffset)
        val startChar = textRange.startOffset - document.getLineStartOffset(startLine)
        val endLine = document.getLineNumber(textRange.endOffset)
        val endChar = textRange.endOffset - document.getLineStartOffset(endLine)

        val selStartLine = document.getLineNumber(nameRange.startOffset)
        val selStartChar = nameRange.startOffset - document.getLineStartOffset(selStartLine)
        val selEndLine = document.getLineNumber(nameRange.endOffset)
        val selEndChar = nameRange.endOffset - document.getLineStartOffset(selEndLine)

        val uri = file.url.replace("file://", "file:///")

        return CallHierarchyItem(
            name = method.name,
            kind =
                if (method.isConstructor) SymbolKind.CONSTRUCTOR
                else SymbolKind.METHOD,
            detail = method.containingClass?.qualifiedName,
            uri = uri,
            range =
                Range(
                    start = Position(line = startLine, character = startChar),
                    end = Position(line = endLine, character = endChar)
                ),
            selectionRange =
                Range(
                    start =
                        Position(
                            line = selStartLine,
                            character = selStartChar
                        ),
                    end = Position(line = selEndLine, character = selEndChar)
                )
        )
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
