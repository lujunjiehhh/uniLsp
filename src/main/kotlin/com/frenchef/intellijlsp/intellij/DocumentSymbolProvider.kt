package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.*
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*

/** T035: 从 IntelliJ PSI/结构视图生成 DocumentSymbol */
class DocumentSymbolProvider(private val project: Project) {
    private val log = logger<DocumentSymbolProvider>()

    /** 获取文件的文档符号列表 */
    fun getDocumentSymbols(psiFile: PsiFile): List<DocumentSymbol> {
        return try {
            ReadAction.compute<List<DocumentSymbol>, Exception> {
                val structureSymbols = getSymbolsFromStructureView(psiFile)
                if (structureSymbols.isNotEmpty()) {
                    return@compute structureSymbols
                }
                getSymbolsFromPsi(psiFile)
            }
        } catch (e: Exception) {
            log.error("Error getting document symbols", e)
            emptyList()
        }
    }

    private fun getSymbolsFromStructureView(psiFile: PsiFile): List<DocumentSymbol> {
        val builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(psiFile)
        if (builder !is TreeBasedStructureViewBuilder) {
            return emptyList()
        }

        val model = builder.createStructureViewModel(null)
        val root = model.root

        return try {
            convertStructureElement(root, psiFile).children ?: emptyList()
        } finally {
            model.dispose()
        }
    }

    private fun convertStructureElement(
            element: StructureViewTreeElement,
            psiFile: PsiFile
    ): DocumentSymbol {
        val psiElement = element.value as? PsiElement
        val name = element.presentation.presentableText ?: "<unknown>"
        val detail = element.presentation.locationString

        val range: Range =
                if (psiElement != null) {
                    PsiMapper.elementToRange(psiElement, psiFile)
                } else {
                    Range(Position(0, 0), Position(0, 0))
                }

        // 获取 selection range - 使用显式类型
        val selectionRange: Range = getSelectionRange(psiElement, psiFile, range)

        val kind = determineSymbolKind(psiElement)

        val children =
                element.children
                        .mapNotNull { child ->
                            if (child is StructureViewTreeElement) {
                                convertStructureElement(child, psiFile)
                            } else null
                        }
                        .takeIf { it.isNotEmpty() }

        return DocumentSymbol(
                name = name,
                detail = detail,
                kind = kind,
                tags = null,
                deprecated = null,
                range = range,
                selectionRange = selectionRange,
                children = children
        )
    }

    /** 获取符号的选择范围（名称标识符的范围） */
    private fun getSelectionRange(
            psiElement: PsiElement?,
            psiFile: PsiFile,
            fallback: Range
    ): Range {
        if (psiElement == null) return fallback

        // 尝试获取名称标识符
        val nameIdentifier: PsiElement? =
                when (psiElement) {
                    is PsiNameIdentifierOwner -> psiElement.nameIdentifier
                    else -> null
                }

        return if (nameIdentifier != null) {
            PsiMapper.elementToRange(nameIdentifier, psiFile)
        } else {
            fallback
        }
    }

    private fun getSymbolsFromPsi(psiFile: PsiFile): List<DocumentSymbol> {
        val symbols = mutableListOf<DocumentSymbol>()

        psiFile.accept(
                object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (element is PsiNamedElement && shouldIncludeElement(element)) {
                            val symbol = createSymbolFromElement(element, psiFile)
                            if (symbol != null) {
                                symbols.add(symbol)
                            }
                        }
                        if (element == psiFile) {
                            super.visitElement(element)
                        }
                    }
                }
        )

        return symbols
    }

    private fun shouldIncludeElement(element: PsiNamedElement): Boolean {
        val name = element.name ?: return false
        if (name.isEmpty() || name.startsWith("<")) return false

        val className = element.javaClass.simpleName.lowercase()
        return className.contains("class") ||
                className.contains("method") ||
                className.contains("function") ||
                className.contains("field") ||
                className.contains("property") ||
                className.contains("variable") ||
                className.contains("interface") ||
                className.contains("enum")
    }

    private fun createSymbolFromElement(
            element: PsiNamedElement,
            psiFile: PsiFile
    ): DocumentSymbol? {
        val name = element.name ?: return null
        val range: Range = PsiMapper.elementToRange(element, psiFile)
        val selectionRange: Range = getSelectionRange(element, psiFile, range)

        return DocumentSymbol(
                name = name,
                detail = null,
                kind = determineSymbolKind(element),
                tags = null,
                deprecated = null,
                range = range,
                selectionRange = selectionRange,
                children = null
        )
    }

    private fun determineSymbolKind(element: PsiElement?): SymbolKind {
        if (element == null) return SymbolKind.FILE

        val className = element.javaClass.simpleName.lowercase()

        return when {
            className.contains("class") && !className.contains("enum") -> SymbolKind.CLASS
            className.contains("interface") -> SymbolKind.INTERFACE
            className.contains("enum") -> SymbolKind.ENUM
            className.contains("method") || className.contains("function") -> SymbolKind.METHOD
            className.contains("constructor") -> SymbolKind.CONSTRUCTOR
            className.contains("field") -> SymbolKind.FIELD
            className.contains("property") -> SymbolKind.PROPERTY
            className.contains("variable") || className.contains("parameter") -> SymbolKind.VARIABLE
            className.contains("constant") -> SymbolKind.CONSTANT
            className.contains("package") || className.contains("namespace") -> SymbolKind.NAMESPACE
            className.contains("module") -> SymbolKind.MODULE
            else -> SymbolKind.VARIABLE
        }
    }
}
