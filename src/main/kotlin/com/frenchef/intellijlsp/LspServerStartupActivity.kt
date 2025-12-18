package com.frenchef.intellijlsp

import com.frenchef.intellijlsp.config.LspSettings
import com.frenchef.intellijlsp.handlers.LifecycleHandler
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.server.LspServerManager
import com.frenchef.intellijlsp.services.LspProjectService
import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Startup activity that automatically starts the LSP server when a project opens. */
class LspServerStartupActivity : ProjectActivity {
    private val log = logger<LspServerStartupActivity>()

    override suspend fun execute(project: Project) {
        val settings = LspSettings.getInstance()

        if (!settings.autoStart) {
            log.info("Auto-start is disabled for project: ${project.name}")
            return
        }

        log.info("Starting LSP server for project: ${project.name}")

        // Initialize LSP Logger for debugging
        LspLogger.init(project)
        LspLogger.info("Startup", "Starting LSP server for project: ${project.name}")

        try {
            // Create JSON-RPC handler for this project
            val jsonRpcHandler = JsonRpcHandler(project)

            // Create document manager
            val documentManager =
                com.frenchef.intellijlsp.intellij.DocumentManager(project)

            // Create completion provider
            val completionProvider =
                com.frenchef.intellijlsp.intellij.CompletionProvider(project)

            // Register lifecycle handler
            val lifecycleHandler = LifecycleHandler(project, jsonRpcHandler)
            lifecycleHandler.register()

            // Register document sync handler
            val documentSyncHandler =
                com.frenchef.intellijlsp.handlers.DocumentSyncHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            documentSyncHandler.register()

            // Register hover handler
            val hoverHandler =
                com.frenchef.intellijlsp.handlers.HoverHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            hoverHandler.register()

            // Register definition handler
            val definitionHandler =
                com.frenchef.intellijlsp.handlers.DefinitionHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            definitionHandler.register()

            // Register completion handler
            val completionHandler =
                com.frenchef.intellijlsp.handlers.CompletionHandler(
                    project,
                    jsonRpcHandler,
                    documentManager,
                    completionProvider
                )
            completionHandler.register()

            // Register references handler
            val referencesHandler =
                com.frenchef.intellijlsp.handlers.ReferencesHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            referencesHandler.register()

            // Register document highlight handler
            val documentHighlightHandler =
                com.frenchef.intellijlsp.handlers.DocumentHighlightHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            documentHighlightHandler.register()

            // Create and start the server
            val server = LspServerManager.createAndStartServer(project, jsonRpcHandler)

            if (server == null) {
                log.error(
                    "Failed to create LSP server for project: ${project.name}"
                )
                return
            }

            // Store the server in the project service
            val projectService = project.getService(LspProjectService::class.java)
            projectService.setServer(server)

            // Create and start diagnostics handler (Push 模式)
            val diagnosticsProvider =
                com.frenchef.intellijlsp.intellij.DiagnosticsProvider(project)
            val diagnosticsHandler =
                com.frenchef.intellijlsp.handlers.DiagnosticsHandler(
                    project,
                    documentManager,
                    diagnosticsProvider,
                    server
                )
            diagnosticsHandler.start()

            // Store diagnostics handler in project service for access by other handlers
            projectService.setDiagnosticsHandler(diagnosticsHandler)

            // Register typeDefinition handler (abcoder 兼容)
            val typeDefinitionHandler =
                com.frenchef.intellijlsp.handlers.TypeDefinitionHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            typeDefinitionHandler.register()

            // Register documentSymbol handler (abcoder 兼容)
            val documentSymbolHandler =
                com.frenchef.intellijlsp.handlers.DocumentSymbolHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            documentSymbolHandler.register()

            // Register semanticTokens handler (abcoder 兼容)
            val semanticTokensHandler =
                com.frenchef.intellijlsp.handlers.SemanticTokensHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            semanticTokensHandler.register()

            // Phase 9: Register signatureHelp handler
            val signatureHelpHandler =
                com.frenchef.intellijlsp.handlers.SignatureHelpHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            signatureHelpHandler.register()

            // Phase 9: Register workspaceSymbol handler
            val workspaceSymbolHandler =
                com.frenchef.intellijlsp.handlers.WorkspaceSymbolHandler(
                    project,
                    jsonRpcHandler
                )
            workspaceSymbolHandler.register()

            // Phase 9: Register formatting handler
            val formattingHandler =
                com.frenchef.intellijlsp.handlers.FormattingHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            formattingHandler.register()

            // Phase 9: Register codeAction handler
            val codeActionHandler =
                com.frenchef.intellijlsp.handlers.CodeActionHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            codeActionHandler.register()

            // Register codeAction/resolve handler
            val codeActionResolveHandler =
                com.frenchef.intellijlsp.handlers.CodeActionResolveHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            codeActionResolveHandler.register()

            // Phase 9: Register implementation handler
            val implementationHandler =
                com.frenchef.intellijlsp.handlers.ImplementationHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            implementationHandler.register()

            // Phase 9: Register inlayHints handler
            val inlayHintsHandler =
                com.frenchef.intellijlsp.handlers.InlayHintsHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            inlayHintsHandler.register()

            // Phase 10: Register rename handler (T013)
            val renameHandler =
                com.frenchef.intellijlsp.handlers.RenameHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            renameHandler.register()

            // Phase 10: Register callHierarchy handler (T018)
            val callHierarchyHandler =
                com.frenchef.intellijlsp.handlers.CallHierarchyHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            callHierarchyHandler.register()

            // Phase 10: Register typeHierarchy handler (T023)
            val typeHierarchyHandler =
                com.frenchef.intellijlsp.handlers.TypeHierarchyHandler(
                    project,
                    jsonRpcHandler,
                    documentManager
                )
            typeHierarchyHandler.register()

            // Phase 10: Register workspaceFolders handler (T030)
            val workspaceFoldersHandler =
                com.frenchef.intellijlsp.handlers.WorkspaceFoldersHandler(
                    project,
                    jsonRpcHandler
                )
            workspaceFoldersHandler.register()

            // Phase 10: Register fileWatching handler (T032)
            val fileWatchingHandler =
                com.frenchef.intellijlsp.handlers.FileWatchingHandler(
                    project,
                    jsonRpcHandler
                )
            fileWatchingHandler.register()

            // Log server info
            val serverInfo =
                when {
                    server.getPort() != null -> "TCP port ${server.getPort()}"
                    server.getSocketPath() != null ->
                        "Unix socket ${server.getSocketPath()}"

                    else -> "unknown transport"
                }
            log.info("LSP server started for project ${project.name} on $serverInfo")
        } catch (e: Exception) {
            log.error("Error starting LSP server for project: ${project.name}", e)
        }
    }
}
