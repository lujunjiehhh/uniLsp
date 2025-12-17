package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.language.ClassInfo
import com.frenchef.intellijlsp.language.LanguageHandlerRegistry
import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.frenchef.intellijlsp.protocol.models.SymbolKind
import com.frenchef.intellijlsp.protocol.models.TypeHierarchyItem
import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Phase 10: Type Hierarchy Provider (T020)
 *
 * 提供 LSP Type Hierarchy 功能的 IntelliJ 集成实现。
 * 使用 LanguageHandler 抽象层支持多语言。
 */
class TypeHierarchyProvider(private val project: Project) {
    private val log = logger<TypeHierarchyProvider>()

    private companion object {
        const val TAG = "TypeHierarchy"
        const val MAX_RESULTS = 100
    }

    /** 准备类型层次 - 返回位置处的类作为 TypeHierarchyItem */
    fun prepareTypeHierarchy(file: PsiFile, position: Position): List<TypeHierarchyItem>? =
        ReadAction.compute<List<TypeHierarchyItem>?, RuntimeException> {
            try {
                val document = PsiDocumentManager.getInstance(project).getDocument(file)
                    ?: return@compute null
                val offset = document.getLineStartOffset(position.line) + position.character

                val element = file.findElementAt(offset) ?: return@compute null

                // 使用 LanguageHandler 查找类
                val handler = LanguageHandlerRegistry.getHandler(file)
                val classInfo = handler.findContainingClass(element) ?: return@compute null

                val item = classInfoToItem(classInfo, document) ?: return@compute null
                log.debug("prepareTypeHierarchy: found class '${item.name}'")
                listOf(item)
            } catch (e: Exception) {
                log.warn("Error in prepareTypeHierarchy", e)
                null
            }
        }

    /** 获取父类型 (supertypes) - 父类和接口 */
    fun getSupertypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        ReadAction.compute<List<TypeHierarchyItem>, RuntimeException> {
            try {
                val classInfo = findClassByItem(item) ?: return@compute emptyList()
                val results = mutableListOf<TypeHierarchyItem>()
                val visited = mutableSetOf<String>()

                // 添加当前类到 visited 防止循环
                classInfo.qualifiedName?.let { visited.add(it) }

                val handler = LanguageHandlerRegistry.getHandler(classInfo.psiElement.containingFile!!)
                val superTypes = handler.getSuperTypes(classInfo)

                for (superType in superTypes) {
                    if (results.size >= MAX_RESULTS) break

                    val key = superType.qualifiedName
                    if (key != null && !visited.contains(key)) {
                        visited.add(key)
                        val doc = superType.psiElement.containingFile?.let {
                            PsiDocumentManager.getInstance(project).getDocument(it)
                        }
                        if (doc != null) {
                            classInfoToItem(superType, doc)?.let { results.add(it) }
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

    /** 获取子类型 (subtypes) - 子类和实现类 */
    fun getSubtypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        ReadAction.compute<List<TypeHierarchyItem>, RuntimeException> {
            try {
                val classInfo = findClassByItem(item) ?: return@compute emptyList()
                val results = mutableListOf<TypeHierarchyItem>()
                val visited = mutableSetOf<String>()

                // 添加当前类到 visited 防止循环
                classInfo.qualifiedName?.let { visited.add(it) }

                val handler = LanguageHandlerRegistry.getHandler(classInfo.psiElement.containingFile!!)
                val subTypes = handler.getSubTypes(classInfo)

                for (subType in subTypes) {
                    if (results.size >= MAX_RESULTS) break

                    val key = subType.qualifiedName
                    if (key != null && !visited.contains(key)) {
                        visited.add(key)
                        val doc = subType.psiElement.containingFile?.let {
                            PsiDocumentManager.getInstance(project).getDocument(it)
                        }
                        if (doc != null) {
                            classInfoToItem(subType, doc)?.let { results.add(it) }
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

    /** 根据 TypeHierarchyItem 查找类 */
    private fun findClassByItem(item: TypeHierarchyItem): ClassInfo? {
        LspLogger.debug(TAG, "findClassByItem: uri=${item.uri}, name=${item.name}")

        val url = item.uri
        val virtualFile = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
            .findFileByUrl(url)
        if (virtualFile == null) {
            LspLogger.warn(TAG, "findClassByItem: virtualFile is null for url=$url")
            return null
        }

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile == null) {
            LspLogger.warn(TAG, "findClassByItem: psiFile is null")
            return null
        }

        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        if (document == null) {
            LspLogger.warn(TAG, "findClassByItem: document is null")
            return null
        }

        val offset = document.getLineStartOffset(item.selectionRange.start.line) +
                item.selectionRange.start.character
        val element = psiFile.findElementAt(offset)
        if (element == null) {
            LspLogger.warn(TAG, "findClassByItem: element is null at offset $offset")
            return null
        }

        val handler = LanguageHandlerRegistry.getHandler(psiFile)
        val classInfo = handler.findContainingClass(element)
        if (classInfo == null) {
            LspLogger.warn(TAG, "findClassByItem: class not found")
        } else {
            LspLogger.debug(TAG, "findClassByItem: found class ${classInfo.name}")
        }
        return classInfo
    }

    /** 将 ClassInfo 转换为 TypeHierarchyItem */
    private fun classInfoToItem(
        classInfo: ClassInfo,
        document: com.intellij.openapi.editor.Document
    ): TypeHierarchyItem? {
        val file = classInfo.psiElement.containingFile?.virtualFile ?: return null
        val textRange = classInfo.psiElement.textRange ?: return null
        val nameIdentifier = classInfo.nameIdentifier ?: return null
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

        // 确定符号类型
        val kind = determineSymbolKind(classInfo)

        return TypeHierarchyItem(
            name = classInfo.name,
            kind = kind,
            detail = classInfo.qualifiedName,
            uri = uri,
            range = Range(
                start = Position(line = startLine, character = startChar),
                end = Position(line = endLine, character = endChar)
            ),
            selectionRange = Range(
                start = Position(line = selStartLine, character = selStartChar),
                end = Position(line = selEndLine, character = selEndChar)
            )
        )
    }

    /** 根据 ClassInfo 确定符号类型 */
    private fun determineSymbolKind(classInfo: ClassInfo): SymbolKind {
        val psiElement = classInfo.psiElement
        return when {
            psiElement is PsiClass && psiElement.isInterface -> SymbolKind.INTERFACE
            psiElement is PsiClass && psiElement.isEnum -> SymbolKind.ENUM
            else -> SymbolKind.CLASS
        }
    }
}
