package com.frenchef.intellijlsp

import com.frenchef.intellijlsp.config.LspSettings
import com.frenchef.intellijlsp.handlers.LifecycleHandler
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.server.LspServerManager
import com.frenchef.intellijlsp.services.LspProjectService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that automatically starts the LSP server when a project opens.
 */
class LspServerStartupActivity : ProjectActivity {
    private val log = logger<LspServerStartupActivity>()

    override suspend fun execute(project: Project) {
        val settings = LspSettings.getInstance()

        if (!settings.autoStart) {
            log.info("Auto-start is disabled for project: ${project.name}")
            return
        }

        log.info("Starting LSP server for project: ${project.name}")

        try {
            // Create JSON-RPC handler for this project
            val jsonRpcHandler = JsonRpcHandler(project)

            // Create document manager
            val documentManager = com.frenchef.intellijlsp.intellij.DocumentManager(project)

            // Create completion provider
            val completionProvider = com.frenchef.intellijlsp.intellij.CompletionProvider(project)

            // Register lifecycle handler
            val lifecycleHandler = LifecycleHandler(project, jsonRpcHandler)
            lifecycleHandler.register()

            // Register document sync handler
            val documentSyncHandler = com.frenchef.intellijlsp.handlers.DocumentSyncHandler(
                project, jsonRpcHandler, documentManager
            )
            documentSyncHandler.register()

            // Register hover handler
            val hoverHandler = com.frenchef.intellijlsp.handlers.HoverHandler(
                project, jsonRpcHandler, documentManager
            )
            hoverHandler.register()

            // Register definition handler
            val definitionHandler = com.frenchef.intellijlsp.handlers.DefinitionHandler(
                project, jsonRpcHandler, documentManager
            )
            definitionHandler.register()

            // Register completion handler
            val completionHandler = com.frenchef.intellijlsp.handlers.CompletionHandler(
                project, jsonRpcHandler, documentManager, completionProvider
            )
            completionHandler.register()

            // Register references handler
            val referencesHandler = com.frenchef.intellijlsp.handlers.ReferencesHandler(
                project, jsonRpcHandler, documentManager
            )
            referencesHandler.register()

            // Register document highlight handler
            val documentHighlightHandler = com.frenchef.intellijlsp.handlers.DocumentHighlightHandler(
                project, jsonRpcHandler, documentManager
            )
            documentHighlightHandler.register()

            // Create and start the server
            val server = LspServerManager.createAndStartServer(project, jsonRpcHandler)

            if (server == null) {
                log.error("Failed to create LSP server for project: ${project.name}")
                return
            }

            // Store the server in the project service
            val projectService = project.getService(LspProjectService::class.java)
            projectService.setServer(server)

            // Create and start diagnostics handler
            val diagnosticsProvider = com.frenchef.intellijlsp.intellij.DiagnosticsProvider(project)
            val diagnosticsHandler = com.frenchef.intellijlsp.handlers.DiagnosticsHandler(
                project, documentManager, diagnosticsProvider, server
            )
            diagnosticsHandler.start()

            // Register typeDefinition handler (abcoder 兼容)
            val typeDefinitionHandler = com.frenchef.intellijlsp.handlers.TypeDefinitionHandler(
                project, jsonRpcHandler, documentManager
            )
            typeDefinitionHandler.register()

            // Register documentSymbol handler (abcoder 兼容)
            val documentSymbolHandler = com.frenchef.intellijlsp.handlers.DocumentSymbolHandler(
                project, jsonRpcHandler, documentManager
            )
            documentSymbolHandler.register()

            // Register semanticTokens handler (abcoder 兼容)
            val semanticTokensHandler = com.frenchef.intellijlsp.handlers.SemanticTokensHandler(
                project, jsonRpcHandler, documentManager
            )
            semanticTokensHandler.register()

            // Log server info
            val serverInfo = when {
                server.getPort() != null -> "TCP port ${server.getPort()}"
                server.getSocketPath() != null -> "Unix socket ${server.getSocketPath()}"
                else -> "unknown transport"
            }
            log.info("LSP server started for project ${project.name} on $serverInfo")

        } catch (e: Exception) {
            log.error("Error starting LSP server for project: ${project.name}", e)
        }
    }
}

