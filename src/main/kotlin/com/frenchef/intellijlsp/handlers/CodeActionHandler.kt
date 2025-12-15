package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.CodeActionProvider
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.CodeAction
import com.frenchef.intellijlsp.protocol.models.CodeActionParams
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Handles textDocument/codeAction requests. Provides code actions (QuickFixes) at a given location.
 */
class CodeActionHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<CodeActionHandler>()
    private val gson = Gson()
    private val codeActionProvider = CodeActionProvider(project)

    /** Register the handler for textDocument/codeAction requests. */
    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/codeAction", this::handleCodeAction)
        log.info("CodeActionHandler registered for textDocument/codeAction")
    }

    /** Handle textDocument/codeAction request. */
    private fun handleCodeAction(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("CodeAction: null params received")
            return gson.toJsonTree(emptyList<CodeAction>())
        }

        return try {
            val codeActionParams = gson.fromJson(params, CodeActionParams::class.java)
            val uri = codeActionParams.textDocument.uri
            val range = codeActionParams.range
            val context = codeActionParams.context

            log.info("CodeAction requested for $uri at range $range")

            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.warn("CodeAction: Virtual file not found for $uri")
                        return gson.toJsonTree(emptyList<CodeAction>())
                    }

            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.warn("CodeAction: PSI file not found")
                        return gson.toJsonTree(emptyList<CodeAction>())
                    }

            val actions = codeActionProvider.getCodeActions(psiFile, range, context, uri)

            log.info("CodeAction: Returning ${actions.size} action(s)")

            gson.toJsonTree(actions)
        } catch (e: Exception) {
            log.error("Error handling codeAction request", e)
            gson.toJsonTree(emptyList<CodeAction>())
        }
    }
}
