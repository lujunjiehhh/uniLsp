package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.InlayHintsProvider
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.InlayHint
import com.frenchef.intellijlsp.protocol.models.InlayHintParams
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Handles textDocument/inlayHint requests. Provides inlay hints for type inference and parameter
 * names.
 */
class InlayHintsHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<InlayHintsHandler>()
    private val gson = LspGson.instance
    private val inlayHintsProvider = InlayHintsProvider(project)

    /** Register the handler for textDocument/inlayHint requests. */
    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/inlayHint", this::handleInlayHint)
        log.info("InlayHintsHandler registered for textDocument/inlayHint")
    }

    /** Handle textDocument/inlayHint request. */
    private fun handleInlayHint(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("InlayHint: null params received")
            return gson.toJsonTree(emptyList<InlayHint>())
        }

        return try {
            val hintParams = gson.fromJson(params, InlayHintParams::class.java)
            val uri = hintParams.textDocument.uri
            val range = hintParams.range

            log.info("InlayHint requested for $uri, range: $range")

            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.warn("InlayHint: Virtual file not found for $uri")
                        return gson.toJsonTree(emptyList<InlayHint>())
                    }

            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.warn("InlayHint: PSI file not found")
                        return gson.toJsonTree(emptyList<InlayHint>())
                    }

            val hints = inlayHintsProvider.getInlayHints(psiFile, range)

            log.info("InlayHint: Returning ${hints.size} hint(s)")

            gson.toJsonTree(hints)
        } catch (e: Exception) {
            log.error("Error handling inlayHint request", e)
            gson.toJsonTree(emptyList<InlayHint>())
        }
    }
}
