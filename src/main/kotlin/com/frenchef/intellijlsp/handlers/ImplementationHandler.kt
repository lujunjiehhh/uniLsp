package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.LspDecompiledUriResolver
import com.frenchef.intellijlsp.intellij.PsiMapper
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
import com.intellij.psi.*
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/** Handles textDocument/implementation requests. Provides "Go to Implementation" functionality. */
class ImplementationHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
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

            // 使用统一的 URI 解析器：缓存文件映射回 jar PSI，但使用缓存文件文本算 offset
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

        // Find the target element (class, interface, method)
        val target = findTargetElement(element) ?: return emptyList()

        val implementations = mutableListOf<Location>()

        try {
            // Use DefinitionsScopedSearch to find implementations
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

    /** Find the target element for implementation search - using UAST for Java/Kotlin support */
    private fun findTargetElement(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            val uElement = current.toUElement()
            // 检查是否是类/接口
            if (uElement is UClass) {
                return uElement.javaPsi
            }
            // 检查是否是方法
            if (uElement is UMethod) {
                return uElement.javaPsi
            }
            // 回退检查 Java PSI
            if (current is PsiClass) return current
            if (current is PsiMethod) return current
            current = current.parent
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
            range =
                Range(
                    start = Position(line = startLine, character = startChar),
                    end = Position(line = endLine, character = endChar)
                )
        )
    }
}
