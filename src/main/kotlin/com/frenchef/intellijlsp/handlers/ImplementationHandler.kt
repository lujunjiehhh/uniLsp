package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.LspDecompiledUriResolver
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.language.LanguageHandlerRegistry
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.Location
import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.frenchef.intellijlsp.protocol.models.TextDocumentPositionParams
import com.frenchef.intellijlsp.util.LspLogger
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.DefinitionsScopedSearch

/**
 * Handles textDocument/implementation requests.
 * Provides "Go to Implementation" functionality.
 * 使用 LanguageHandler 抽象层支持多语言。
 */
@Suppress("unused")
class ImplementationHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager // 保留参数以保持 API 一致性
) {
    companion object {
        private const val TAG = "Implementation"
    }
    private val gson = LspGson.instance

    /** Register the handler for textDocument/implementation requests. */
    fun register() {
        jsonRpcHandler.registerRequestHandler(
            "textDocument/implementation",
            this::handleImplementation
        )
        LspLogger.info(TAG, "ImplementationHandler registered for textDocument/implementation")
    }

    /** Handle textDocument/implementation request. */
    private fun handleImplementation(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            LspLogger.debug(TAG, "null params received")
            return gson.toJsonTree(emptyList<Location>())
        }

        return try {
            val implParams = gson.fromJson(params, TextDocumentPositionParams::class.java)
            val uri = implParams.textDocument.uri
            val position = implParams.position

            LspLogger.info(
                TAG,
                "requested for $uri at line ${position.line}, char ${position.character}"
            )

            // 使用统一的 URI 解析器
            val resolved = LspDecompiledUriResolver.resolveForRequest(project, uri)
                ?: run {
                    LspLogger.warn(TAG, "URI 解析失败 for $uri")
                    return gson.toJsonTree(emptyList<Location>())
                }

            val psiFile = resolved.analysisPsiFile
            val fileText = resolved.fileText

            val implementations =
                ReadAction.compute<List<Location>, RuntimeException> {
                    findImplementations(psiFile, fileText, position)
                }

            LspLogger.info(TAG, "Found ${implementations.size} implementation(s)")

            gson.toJsonTree(implementations)
        } catch (e: Exception) {
            LspLogger.error(TAG, "Error handling request: ${e.message}")
            gson.toJsonTree(emptyList<Location>())
        }
    }

    /** Find implementations of the element at the given position. */
    private fun findImplementations(
        psiFile: PsiFile,
        fileText: String,
        position: Position
    ): List<Location> {
        val offset = PsiMapper.positionToOffset(fileText, position)
        val element = psiFile.findElementAt(offset)?.parent ?: return emptyList()

        // 使用 LanguageHandler 查找目标元素
        val target = findTargetElement(psiFile, element) ?: return emptyList()

        val implementations = mutableListOf<Location>()

        try {
            // Use DefinitionsScopedSearch to find implementations (通用 PSI API)
            DefinitionsScopedSearch.search(target).forEach { impl ->
                val location = elementToLocation(impl)
                if (location != null) {
                    implementations.add(location)
                }
            }
        } catch (e: Exception) {
            LspLogger.warn(TAG, "Error searching for implementations: ${e.message}")
        }

        return implementations
    }

    /**
     * Find the target element for implementation search.
     * 使用 LanguageHandler 替代直接 UAST 调用。
     */
    private fun findTargetElement(psiFile: PsiFile, element: PsiElement): PsiElement? {
        val handler = LanguageHandlerRegistry.getHandler(psiFile)

        // 尝试查找方法
        val functionInfo = handler.findContainingFunction(element)
        if (functionInfo != null) {
            return functionInfo.psiElement
        }

        // 尝试查找类
        val classInfo = handler.findContainingClass(element)
        if (classInfo != null) {
            return classInfo.psiElement
        }

        // 回退：尝试解析引用
        val resolved = handler.resolveReference(element)
        if (resolved != null) {
            return resolved
        }

        return null
    }

    /** Convert a PSI element to a Location. */
    private fun elementToLocation(element: PsiElement): Location? {
        val file = element.containingFile?.virtualFile ?: return null
        val document =
            PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return null

        val startOffset = element.textRange?.startOffset ?: return null
        val endOffset = element.textRange?.endOffset ?: return null

        val startLine = document.getLineNumber(startOffset)
        val startChar = startOffset - document.getLineStartOffset(startLine)
        val endLine = document.getLineNumber(endOffset)
        val endChar = endOffset - document.getLineStartOffset(endLine)

        return Location(
            uri = file.url.replace("file://", "file:///"),
            range = Range(
                start = Position(line = startLine, character = startChar),
                end = Position(line = endLine, character = endChar)
            )
        )
    }
}
