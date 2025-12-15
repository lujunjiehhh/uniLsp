package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.WorkspaceSymbolProvider
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.SymbolInformation
import com.frenchef.intellijlsp.protocol.models.WorkspaceSymbolParams
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/** Handles workspace/symbol requests. Provides symbol search across the entire workspace. */
class WorkspaceSymbolHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler
) {
    private val log = logger<WorkspaceSymbolHandler>()
    private val gson = Gson()
    private val workspaceSymbolProvider = WorkspaceSymbolProvider(project)

    /** Register the handler for workspace/symbol requests. */
    fun register() {
        jsonRpcHandler.registerRequestHandler("workspace/symbol", this::handleWorkspaceSymbol)
        log.info("WorkspaceSymbolHandler registered for workspace/symbol")
    }

    /** Handle workspace/symbol request. */
    private fun handleWorkspaceSymbol(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("WorkspaceSymbol: null params received")
            return gson.toJsonTree(emptyList<SymbolInformation>())
        }

        return try {
            val symbolParams = gson.fromJson(params, WorkspaceSymbolParams::class.java)
            val query = symbolParams.query

            log.info("WorkspaceSymbol requested for query: $query")

            val symbols = workspaceSymbolProvider.searchSymbols(query)

            log.info("WorkspaceSymbol: Found ${symbols.size} symbols")

            gson.toJsonTree(symbols)
        } catch (e: Exception) {
            log.error("Error handling workspace/symbol request", e)
            gson.toJsonTree(emptyList<SymbolInformation>())
        }
    }
}
