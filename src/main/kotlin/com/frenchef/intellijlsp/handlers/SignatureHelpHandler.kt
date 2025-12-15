package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.intellij.SignatureHelpProvider
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.SignatureHelpParams
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Handles textDocument/signatureHelp requests. Provides function signature information when cursor
 * is inside function call arguments.
 *
 * Supports both Kotlin and Java function calls.
 */
class SignatureHelpHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<SignatureHelpHandler>()
    private val gson = Gson()
    private val signatureHelpProvider = SignatureHelpProvider(project)

    /** Register the handler for textDocument/signatureHelp requests. */
    fun register() {
        jsonRpcHandler.registerRequestHandler(
            "textDocument/signatureHelp",
            this::handleSignatureHelp
        )
        log.info("SignatureHelpHandler registered for textDocument/signatureHelp")
    }

    /**
     * Handle textDocument/signatureHelp request.
     *
     * @param params The request parameters as JSON
     * @return SignatureHelp response or null if no signature help is available
     */
    private fun handleSignatureHelp(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("SignatureHelp: null params received")
            return null
        }

        return try {
            val signatureParams = gson.fromJson(params, SignatureHelpParams::class.java)
            val uri = signatureParams.textDocument.uri
            val position = signatureParams.position

            log.info(
                "SignatureHelp requested for $uri at line ${position.line}, char ${position.character}"
            )

            // Get the virtual file and document
            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.warn("SignatureHelp: Virtual file not found for $uri")
                        return null
                    }

            val document =
                documentManager.getIntellijDocument(uri)
                    ?: run {
                        log.warn("SignatureHelp: Document not found for $uri")
                        return null
                    }

            // Get the PSI file
            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.warn("SignatureHelp: PSI file not found")
                        return null
                    }

            // Calculate the offset
            val offset = PsiMapper.positionToOffset(document, position)
            log.debug("SignatureHelp: Looking for signatures at offset $offset")

            // Get signature help from the provider
            val signatureHelp = signatureHelpProvider.getSignatureHelp(psiFile, document, offset)

            if (signatureHelp == null) {
                log.info("SignatureHelp: No signature help available at position")
                return null
            }

            log.info(
                "SignatureHelp: Found ${signatureHelp.signatures.size} signature(s), active param: ${signatureHelp.activeParameter}"
            )

            // Convert to JSON and return
            gson.toJsonTree(signatureHelp)
        } catch (e: Exception) {
            log.error("Error handling signatureHelp request", e)
            null
        }
    }
}
