package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.DocumentSymbolProvider
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.DocumentSymbol
import com.frenchef.intellijlsp.protocol.models.DocumentSymbolParams
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * T036: 处理 textDocument/documentSymbol 请求
 */
class DocumentSymbolHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<DocumentSymbolHandler>()
    private val gson = LspGson.instance
    private val symbolProvider = DocumentSymbolProvider(project)

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/documentSymbol", this::handleDocumentSymbol)
    }

    private fun handleDocumentSymbol(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.warn("DocumentSymbol request received with null params")
            return null
        }

        val symbolParams = gson.fromJson(params, DocumentSymbolParams::class.java)
        val uri = symbolParams.textDocument.uri

        log.info("DocumentSymbol request for $uri")

        return try {
            ReadAction.compute<JsonElement?, Exception> {
                val psiFile = PsiMapper.getPsiFile(project, uri) ?: run {
                    log.warn("Could not find PSI file for $uri")
                    return@compute null
                }

                val symbols = symbolProvider.getDocumentSymbols(psiFile)
                
                if (symbols.isEmpty()) {
                    log.debug("No symbols found in $uri")
                    return@compute gson.toJsonTree(emptyList<DocumentSymbol>())
                }

                log.info("Found ${symbols.size} symbols in $uri")
                gson.toJsonTree(symbols)
            }
        } catch (e: Exception) {
            log.error("Error handling documentSymbol request", e)
            null
        }
    }
}
