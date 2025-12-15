package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.intellij.SemanticTokensProvider
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.SemanticTokensParams
import com.frenchef.intellijlsp.protocol.models.SemanticTokensRangeParams
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * T038: 处理 textDocument/semanticTokens/full 和 range 请求
 */
class SemanticTokensHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<SemanticTokensHandler>()
    private val gson = LspGson.instance
    private val tokensProvider = SemanticTokensProvider(project)

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/semanticTokens/full", this::handleSemanticTokensFull)
        jsonRpcHandler.registerRequestHandler("textDocument/semanticTokens/range", this::handleSemanticTokensRange)
    }

    private fun handleSemanticTokensFull(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.warn("SemanticTokens/full request received with null params")
            return null
        }

        val tokenParams = gson.fromJson(params, SemanticTokensParams::class.java)
        val uri = tokenParams.textDocument.uri

        log.info("SemanticTokens/full request for $uri")

        return try {
            ReadAction.compute<JsonElement?, Exception> {
                val psiFile = PsiMapper.getPsiFile(project, uri) ?: run {
                    log.warn("Could not find PSI file for $uri")
                    return@compute null
                }

                val tokens = tokensProvider.getSemanticTokens(psiFile)
                log.info("Returning ${tokens.data.size / 5} semantic tokens for $uri")
                
                gson.toJsonTree(tokens)
            }
        } catch (e: Exception) {
            log.error("Error handling semanticTokens/full request", e)
            null
        }
    }

    private fun handleSemanticTokensRange(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.warn("SemanticTokens/range request received with null params")
            return null
        }

        val rangeParams = gson.fromJson(params, SemanticTokensRangeParams::class.java)
        val uri = rangeParams.textDocument.uri
        val range = rangeParams.range

        log.info("SemanticTokens/range request for $uri (${range.start.line}-${range.end.line})")

        return try {
            ReadAction.compute<JsonElement?, Exception> {
                val psiFile = PsiMapper.getPsiFile(project, uri) ?: run {
                    log.warn("Could not find PSI file for $uri")
                    return@compute null
                }

                val tokens = tokensProvider.getSemanticTokensRange(psiFile, range)
                log.info("Returning ${tokens.data.size / 5} semantic tokens for range in $uri")
                
                gson.toJsonTree(tokens)
            }
        } catch (e: Exception) {
            log.error("Error handling semanticTokens/range request", e)
            null
        }
    }
}
