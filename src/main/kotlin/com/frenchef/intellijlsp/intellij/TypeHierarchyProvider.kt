package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.frenchef.intellijlsp.protocol.models.SymbolKind
import com.frenchef.intellijlsp.protocol.models.TypeHierarchyItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType

/**
 * Phase 10: Type Hierarchy Provider (T020)
 *
 * 提供 LSP Type Hierarchy 功能的 IntelliJ 集成实现。 使用 PsiClass API 获取父类型，使用 ClassInheritorsSearch 获取子类型。 包含
 * visited set 防止循环继承导致无限递归。
 */
class TypeHierarchyProvider(private val project: Project) {
    private val log = logger<TypeHierarchyProvider>()

    private companion object {
        const val MAX_RESULTS = 100
    }

    /** 准备类型层次 - 返回位置处的类作为 TypeHierarchyItem */
    fun prepareTypeHierarchy(file: PsiFile, position: Position): List<TypeHierarchyItem>? =
        ReadAction.compute<List<TypeHierarchyItem>?, RuntimeException> {
            try {
                val document =
                    PsiDocumentManager.getInstance(project).getDocument(file)
                        ?: return@compute null
                val offset = document.getLineStartOffset(position.line) + position.character

                // 查找位置处的元素
                val element = file.findElementAt(offset) ?: return@compute null
                val psiClass = findClass(element) ?: return@compute null

                val item = classToItem(psiClass, document) ?: return@compute null
                log.debug("prepareTypeHierarchy: found class '${item.name}'")
                listOf(item)
            } catch (e: Exception) {
                log.warn("Error in prepareTypeHierarchy", e)
                null
            }
        }

    /** 获取父类型 (supertypes) - 父类和接口 使用 visited set 防止循环 */
    fun getSupertypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        ReadAction.compute<List<TypeHierarchyItem>, RuntimeException> {
            try {
                val psiClass = findClassByItem(item) ?: return@compute emptyList()
                val results = mutableListOf<TypeHierarchyItem>()
                val visited = mutableSetOf<String>()

                // 添加当前类到 visited 防止循环
                psiClass.qualifiedName?.let { visited.add(it) }

                // 添加父类
                psiClass.superClass?.let { superClass ->
                    val key = superClass.qualifiedName
                    if (key != null && !visited.contains(key) && results.size < MAX_RESULTS) {
                        visited.add(key)
                        val doc =
                            superClass.containingFile?.let {
                                PsiDocumentManager.getInstance(project).getDocument(it)
                            }
                        if (doc != null) {
                            classToItem(superClass, doc)?.let { results.add(it) }
                        }
                    }
                }

                // 添加接口
                for (iface in psiClass.interfaces) {
                    if (results.size >= MAX_RESULTS) break
                    val key = iface.qualifiedName
                    if (key != null && !visited.contains(key)) {
                        visited.add(key)
                        val doc =
                            iface.containingFile?.let {
                                PsiDocumentManager.getInstance(project).getDocument(it)
                            }
                        if (doc != null) {
                            classToItem(iface, doc)?.let { results.add(it) }
                        }
                    }
                }

                log.debug("getSupertypes: found ${results.size} supertypes")
                results
            } catch (e: Exception) {
                log.warn("Error in getSupertypes", e)
                emptyList()
            }
        }

    /** 获取子类型 (subtypes) - 子类和实现类 使用 visited set 防止循环 */
    fun getSubtypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        ReadAction.compute<List<TypeHierarchyItem>, RuntimeException> {
            try {
                val psiClass = findClassByItem(item) ?: return@compute emptyList()
                val results = mutableListOf<TypeHierarchyItem>()
                val visited = mutableSetOf<String>()

                // 添加当前类到 visited 防止循环
                psiClass.qualifiedName?.let { visited.add(it) }

                val scope = GlobalSearchScope.projectScope(project)
                // 只获取直接子类 (deep = false)
                val inheritors = ClassInheritorsSearch.search(psiClass, scope, false).findAll()

                for (inheritor in inheritors) {
                    if (results.size >= MAX_RESULTS) break

                    val key = inheritor.qualifiedName
                    if (key != null && !visited.contains(key)) {
                        visited.add(key)
                        val doc =
                            inheritor.containingFile?.let {
                                PsiDocumentManager.getInstance(project).getDocument(it)
                            }
                        if (doc != null) {
                            classToItem(inheritor, doc)?.let { results.add(it) }
                        }
                    }
                }

                log.debug("getSubtypes: found ${results.size} subtypes")
                results
            } catch (e: Exception) {
                log.warn("Error in getSubtypes", e)
                emptyList()
            }
        }

    /** 查找父级类 - 使用 UAST 支持 Java/Kotlin */
    private fun findClass(element: PsiElement): PsiClass? {
        // 先尝试使用 UAST 查找 (支持 Kotlin)
        val uClass = element.toUElementOfType<UClass>()
        if (uClass != null) {
            return uClass.javaPsi
        }

        // 向上查找 UClass
        var current: PsiElement? = element
        while (current != null) {
            val uElement = current.toUElement()
            if (uElement is UClass) {
                return uElement.javaPsi
            }
            if (current is PsiClass) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /** 根据 TypeHierarchyItem 查找类 */
    private fun findClassByItem(item: TypeHierarchyItem): PsiClass? {
        val virtualFile =
            com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                .findFileByUrl(item.uri.replace("file:///", "file://"))
                ?: return null

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null

        val offset =
            document.getLineStartOffset(item.selectionRange.start.line) +
                    item.selectionRange.start.character
        val element = psiFile.findElementAt(offset) ?: return null

        return findClass(element)
    }

    /** 将 PsiClass 转换为 TypeHierarchyItem */
    private fun classToItem(
        psiClass: PsiClass,
        document: com.intellij.openapi.editor.Document
    ): TypeHierarchyItem? {
        val file = psiClass.containingFile?.virtualFile ?: return null
        val textRange = psiClass.textRange ?: return null
        val nameIdentifier = psiClass.nameIdentifier ?: return null
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

        val kind =
            when {
                psiClass.isInterface -> SymbolKind.INTERFACE
                psiClass.isEnum -> SymbolKind.ENUM
                else -> SymbolKind.CLASS
            }

        return TypeHierarchyItem(
            name = psiClass.name ?: return null,
            kind = kind,
            detail = psiClass.qualifiedName,
            uri = uri,
            range =
                Range(
                    start = Position(line = startLine, character = startChar),
                    end = Position(line = endLine, character = endChar)
                ),
            selectionRange =
                Range(
                    start = Position(line = selStartLine, character = selStartChar),
                    end = Position(line = selEndLine, character = selEndChar)
                )
        )
    }
}
