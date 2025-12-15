package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.Location
import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.frenchef.intellijlsp.protocol.models.TextDocumentPositionParams
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
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
    private val log = logger<ImplementationHandler>()
    private val gson = Gson()

    /** Register the handler for textDocument/implementation requests. */
    fun register() {
        jsonRpcHandler.registerRequestHandler(
            "textDocument/implementation",
            this::handleImplementation
        )
        log.info("ImplementationHandler registered for textDocument/implementation")
    }

    /** Handle textDocument/implementation request. */
    private fun handleImplementation(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("Implementation: null params received")
            return gson.toJsonTree(emptyList<Location>())
        }

        return try {
            val implParams = gson.fromJson(params, TextDocumentPositionParams::class.java)
            val uri = implParams.textDocument.uri
            val position = implParams.position

            log.info(
                "Implementation requested for $uri at line ${position.line}, char ${position.character}"
            )

            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.warn("Implementation: Virtual file not found for $uri")
                        return gson.toJsonTree(emptyList<Location>())
                    }

            val document =
                documentManager.getIntellijDocument(uri)
                    ?: run {
                        log.warn("Implementation: Document not found for $uri")
                        return gson.toJsonTree(emptyList<Location>())
                    }

            val psiFile =
                ReadAction.compute<PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.warn("Implementation: PSI file not found")
                        return gson.toJsonTree(emptyList<Location>())
                    }

            val implementations =
                ReadAction.compute<List<Location>, RuntimeException> {
                    findImplementations(psiFile, document, position)
                }

            log.info("Implementation: Found ${implementations.size} implementation(s)")

            gson.toJsonTree(implementations)
        } catch (e: Exception) {
            log.error("Error handling implementation request", e)
            gson.toJsonTree(emptyList<Location>())
        }
    }

    /** Find implementations of the element at the given position. */
    private fun findImplementations(
        psiFile: PsiFile,
        document: com.intellij.openapi.editor.Document,
        position: Position
    ): List<Location> {
        val offset = PsiMapper.positionToOffset(document, position)
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
            log.warn("Error searching for implementations: ${e.message}")
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
