package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspException
import com.frenchef.intellijlsp.protocol.models.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

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
        
        // Validate that rootUri is within or equal to the project path
        if (!validateRootUri(initParams.rootUri, project.basePath)) {
            val errorMsg = "Invalid rootUri: must be equal to or a subfolder of project path"
            log.warn("$errorMsg. rootUri=${initParams.rootUri}, projectPath=${project.basePath}")
            throw LspException(
                ErrorCodes.INVALID_PARAMS,
                errorMsg
            )
        }

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
     * Validate that the client's rootUri is equal to or a subfolder of the project path.
     * 
     * @param rootUri The rootUri from the client's initialize request
     * @param projectBasePath The IntelliJ project base path
     * @return true if validation passes, false otherwise
     */
    private fun validateRootUri(rootUri: String?, projectBasePath: String?): Boolean {
        // If either is null, reject the connection
        if (rootUri == null || projectBasePath == null) {
            log.warn("RootUri validation failed: rootUri or projectBasePath is null")
            return false
        }
        
        try {
            // Parse the URI to get the file system path
            val uri = URI(rootUri)
            if (uri.scheme != "file") {
                log.warn("RootUri validation failed: unsupported URI scheme '${uri.scheme}'")
                return false
            }
            
            // Convert URI to Path and normalize
            val clientPath: Path = Paths.get(uri).toAbsolutePath().normalize()
            val projectPath: Path = Paths.get(projectBasePath).toAbsolutePath().normalize()
            
            // Accept if client path equals project path OR is a subfolder of project path
            val isValid = clientPath == projectPath || clientPath.startsWith(projectPath)
            
            if (!isValid) {
                log.warn("RootUri validation failed: clientPath=$clientPath is not within projectPath=$projectPath")
            }
            
            return isValid
        } catch (e: Exception) {
            log.error("Error validating rootUri: ${e.message}", e)
            return false
        }
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
            typeDefinitionProvider = true,  // T039: abcoder 兼容
            referencesProvider = true,
            documentHighlightProvider = true,
            documentSymbolProvider = true,  // T040: abcoder 兼容
            workspaceSymbolProvider = false,
            codeActionProvider = false,
            codeLensProvider = null,
            documentFormattingProvider = false,
            documentRangeFormattingProvider = false,
            documentOnTypeFormattingProvider = null,
            renameProvider = false,
            documentLinkProvider = null,
            executeCommandProvider = null,
            // T041: SemanticTokens - abcoder 兼容
            semanticTokensProvider = SemanticTokensOptions(
                legend = SemanticTokensLegend(
                    tokenTypes = listOf(
                        SemanticTokenTypes.NAMESPACE,
                        SemanticTokenTypes.TYPE,
                        SemanticTokenTypes.CLASS,
                        SemanticTokenTypes.ENUM,
                        SemanticTokenTypes.INTERFACE,
                        SemanticTokenTypes.STRUCT,
                        SemanticTokenTypes.TYPE_PARAMETER,
                        SemanticTokenTypes.PARAMETER,
                        SemanticTokenTypes.VARIABLE,
                        SemanticTokenTypes.PROPERTY,
                        SemanticTokenTypes.ENUM_MEMBER,
                        SemanticTokenTypes.FUNCTION,
                        SemanticTokenTypes.METHOD,
                        SemanticTokenTypes.KEYWORD,
                        SemanticTokenTypes.MODIFIER,
                        SemanticTokenTypes.COMMENT,
                        SemanticTokenTypes.STRING,
                        SemanticTokenTypes.NUMBER,
                        SemanticTokenTypes.OPERATOR
                    ),
                    tokenModifiers = listOf(
                        SemanticTokenModifiers.DECLARATION,
                        SemanticTokenModifiers.DEFINITION,
                        SemanticTokenModifiers.READONLY,
                        SemanticTokenModifiers.STATIC,
                        SemanticTokenModifiers.DEPRECATED,
                        SemanticTokenModifiers.ABSTRACT
                    )
                ),
                range = true,
                full = SemanticTokensFullOptions(delta = false)
            ),
            experimental = null
        )
    }

    fun isInitialized(): Boolean = initialized
    
    fun isShutdownRequested(): Boolean = shutdownRequested
    
    fun getClientCapabilities(): ClientCapabilities? = clientCapabilities
}

