package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspException
import com.frenchef.intellijlsp.protocol.models.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * Handles LSP lifecycle methods: initialize, initialized, shutdown, exit.
 */
class LifecycleHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler
) {
    private val log = logger<LifecycleHandler>()
    private val gson = Gson()
    
    @Volatile
    private var initialized = false
    
    @Volatile
    private var shutdownRequested = false
    
    private var clientCapabilities: ClientCapabilities? = null

    fun register() {
        jsonRpcHandler.registerRequestHandler("initialize", this::handleInitialize)
        jsonRpcHandler.registerNotificationHandler("initialized", this::handleInitialized)
        jsonRpcHandler.registerRequestHandler("shutdown", this::handleShutdown)
        jsonRpcHandler.registerNotificationHandler("exit", this::handleExit)
    }

    /**
     * Handle the 'initialize' request.
     * This is the first request sent by the client.
     */
    private fun handleInitialize(params: JsonElement?): JsonElement {
        if (initialized) {
            throw LspException(
                ErrorCodes.INVALID_REQUEST,
                "Server already initialized"
            )
        }

        log.info("Handling initialize request for project: ${project.name}")

        if (params == null || params.isJsonNull) {
            throw LspException(
                ErrorCodes.INVALID_PARAMS,
                "Initialize params are required"
            )
        }

        val initParams = gson.fromJson(params, InitializeParams::class.java)
        clientCapabilities = initParams.capabilities

        log.info("Client root URI: ${initParams.rootUri}")
        log.info("Client capabilities received")

        val result = InitializeResult(
            capabilities = getServerCapabilities(),
            serverInfo = ServerInfo(
                name = "IntelliJ LSP Server",
                version = "1.0.0"
            )
        )

        return gson.toJsonTree(result)
    }

    /**
     * Handle the 'initialized' notification.
     * Sent by the client after receiving the initialize response.
     */
    private fun handleInitialized(params: JsonElement?) {
        log.info("Received initialized notification for project: ${project.name}")
        initialized = true
    }

    /**
     * Handle the 'shutdown' request.
     * Prepares the server for shutdown.
     */
    private fun handleShutdown(params: JsonElement?): JsonElement {
        log.info("Handling shutdown request for project: ${project.name}")
        shutdownRequested = true
        return JsonNull.INSTANCE
    }

    /**
     * Handle the 'exit' notification.
     * Exits the server.
     */
    private fun handleExit(params: JsonElement?) {
        log.info("Handling exit notification for project: ${project.name}")
        
        // The exit notification is sent to request the server to exit.
        // We don't actually exit the process since we're running inside IntelliJ,
        // but we can clean up resources if needed.
        
        // In a standalone LSP server, this would terminate the process.
        // Here, the client disconnecting will trigger cleanup naturally.
    }

    /**
     * Get the server capabilities to advertise to the client.
     */
    private fun getServerCapabilities(): ServerCapabilities {
        return ServerCapabilities(
            textDocumentSync = TextDocumentSyncOptions(
                openClose = true,
                change = TextDocumentSyncKind.INCREMENTAL,
                willSave = false,
                willSaveWaitUntil = false,
                save = SaveOptions(includeText = false)
            ),
            hoverProvider = true,
            completionProvider = CompletionOptions(
                resolveProvider = false,
                triggerCharacters = listOf(".", "::", "->", "@")
            ),
            signatureHelpProvider = null,
            definitionProvider = true,
            referencesProvider = true,
            documentHighlightProvider = true,
            documentSymbolProvider = false,
            workspaceSymbolProvider = false,
            codeActionProvider = false,
            codeLensProvider = null,
            documentFormattingProvider = false,
            documentRangeFormattingProvider = false,
            documentOnTypeFormattingProvider = null,
            renameProvider = false,
            documentLinkProvider = null,
            executeCommandProvider = null,
            experimental = null
        )
    }

    fun isInitialized(): Boolean = initialized
    
    fun isShutdownRequested(): Boolean = shutdownRequested
    
    fun getClientCapabilities(): ClientCapabilities? = clientCapabilities
}

