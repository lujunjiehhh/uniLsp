package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.CompletionProvider
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.CompletionList
import com.frenchef.intellijlsp.protocol.models.CompletionParams
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Handles textDocument/completion requests.
 * Provides code completion suggestions.
 */
class CompletionHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager,
    private val completionProvider: CompletionProvider
) {
    private val log = logger<CompletionHandler>()
    private val gson = LspGson.instance

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/completion", this::handleCompletion)
    }

    /**
     * Handle textDocument/completion request.
     */
    private fun handleCompletion(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val completionParams = gson.fromJson(params, CompletionParams::class.java)
            val uri = completionParams.textDocument.uri
            val position = completionParams.position
            
            log.debug("Completion requested for $uri at line ${position.line}, char ${position.character}")
            
            val virtualFile = documentManager.getVirtualFile(uri) ?: return null
            val document = documentManager.getIntellijDocument(uri) ?: return null
            
            val psiFile = ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                PsiManager.getInstance(project).findFile(virtualFile)
            } ?: return null
            
            val offset = PsiMapper.positionToOffset(document, position)
            
            // Get completions
            val items = completionProvider.getCompletions(psiFile, offset)
            
            log.debug("Returning ${items.size} completion items")
            
            val completionList = CompletionList(
                isIncomplete = false,
                items = items
            )
            
            return gson.toJsonTree(completionList)
            
        } catch (e: Exception) {
            log.error("Error handling completion", e)
            return null
        }
    }
}

