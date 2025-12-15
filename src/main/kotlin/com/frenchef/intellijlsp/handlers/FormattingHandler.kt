package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.FormattingProvider
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.DocumentFormattingParams
import com.frenchef.intellijlsp.protocol.models.DocumentRangeFormattingParams
import com.frenchef.intellijlsp.protocol.models.TextEdit
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Handles textDocument/formatting and textDocument/rangeFormatting requests. Provides code
 * formatting functionality.
 */
class FormattingHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<FormattingHandler>()
    private val gson = Gson()
    private val formattingProvider = FormattingProvider(project)

    /** Register the handlers for formatting requests. */
    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/formatting", this::handleFormatting)
        jsonRpcHandler.registerRequestHandler(
            "textDocument/rangeFormatting",
            this::handleRangeFormatting
        )
        log.info(
            "FormattingHandler registered for textDocument/formatting and textDocument/rangeFormatting"
        )
    }

    /** Handle textDocument/formatting request. */
    private fun handleFormatting(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("Formatting: null params received")
            return gson.toJsonTree(emptyList<TextEdit>())
        }

        return try {
            val formattingParams = gson.fromJson(params, DocumentFormattingParams::class.java)
            val uri = formattingParams.textDocument.uri
            val options = formattingParams.options

            log.info("Formatting requested for $uri")

            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.warn("Formatting: Virtual file not found for $uri")
                        return gson.toJsonTree(emptyList<TextEdit>())
                    }

            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.warn("Formatting: PSI file not found")
                        return gson.toJsonTree(emptyList<TextEdit>())
                    }

            val edits = formattingProvider.formatDocument(psiFile, options)

            log.info("Formatting: Returning ${edits.size} edit(s)")

            gson.toJsonTree(edits)
        } catch (e: Exception) {
            log.error("Error handling formatting request", e)
            gson.toJsonTree(emptyList<TextEdit>())
        }
    }

    /** Handle textDocument/rangeFormatting request. */
    private fun handleRangeFormatting(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("RangeFormatting: null params received")
            return gson.toJsonTree(emptyList<TextEdit>())
        }

        return try {
            val formattingParams = gson.fromJson(params, DocumentRangeFormattingParams::class.java)
            val uri = formattingParams.textDocument.uri
            val range = formattingParams.range
            val options = formattingParams.options

            log.info("RangeFormatting requested for $uri, range: $range")

            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.warn("RangeFormatting: Virtual file not found for $uri")
                        return gson.toJsonTree(emptyList<TextEdit>())
                    }

            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.warn("RangeFormatting: PSI file not found")
                        return gson.toJsonTree(emptyList<TextEdit>())
                    }

            val edits = formattingProvider.formatRange(psiFile, range, options)

            log.info("RangeFormatting: Returning ${edits.size} edit(s)")

            gson.toJsonTree(edits)
        } catch (e: Exception) {
            log.error("Error handling rangeFormatting request", e)
            gson.toJsonTree(emptyList<TextEdit>())
        }
    }
}
