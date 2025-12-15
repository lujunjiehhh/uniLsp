package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DiagnosticsProvider
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.protocol.models.PublishDiagnosticsParams
import com.frenchef.intellijlsp.server.LspServer
import com.frenchef.intellijlsp.server.TcpLspServer
import com.frenchef.intellijlsp.server.UdsLspServer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles publishing diagnostics to LSP clients.
 * Monitors IntelliJ's code analysis and pushes updates to clients.
 */
class DiagnosticsHandler(
    private val project: Project,
    private val documentManager: DocumentManager,
    private val diagnosticsProvider: DiagnosticsProvider,
    private val server: LspServer
) {
    private val log = logger<DiagnosticsHandler>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Track pending diagnostic updates per file to debounce
    private val pendingUpdates = ConcurrentHashMap<String, Job>()

    /**
     * Start listening for diagnostic updates.
     */
    fun start() {
        log.info("Starting diagnostics handler")
        
        // Listen to daemon code analyzer updates
        val connection = project.messageBus.connect()
        connection.subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished() {
                    // Daemon finished analyzing, publish diagnostics for open files
                    publishDiagnosticsForOpenFiles()
                }

                override fun daemonCancelEventOccurred(reason: String) {
                    // Analysis was cancelled, ignore
                }
            }
        )
    }

    /**
     * Publish diagnostics for a specific file.
     */
    fun publishDiagnosticsForFile(uri: String) {
        // Cancel any pending update for this file
        pendingUpdates[uri]?.cancel()
        
        // Schedule a new update with debouncing (500ms delay)
        val job = scope.launch {
            delay(500)
            
            try {
                val virtualFile = documentManager.getVirtualFile(uri)
                val document = documentManager.getIntellijDocument(uri)
                
                if (virtualFile == null || document == null) {
                    log.debug("Cannot publish diagnostics for $uri: file or document not found")
                    return@launch
                }
                
                val diagnostics = diagnosticsProvider.getDiagnosticsByUri(uri, virtualFile, document)
                val version = documentManager.getDocumentVersion(uri)
                
                val params = PublishDiagnosticsParams(
                    uri = uri,
                    version = version,
                    diagnostics = diagnostics
                )
                
                // Send notification to client
                broadcastDiagnostics(params)
                
                log.debug("Published ${diagnostics.size} diagnostics for $uri")
                
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    log.warn("Error publishing diagnostics for $uri", e)
                }
            } finally {
                pendingUpdates.remove(uri)
            }
        }
        
        pendingUpdates[uri] = job
    }

    /**
     * Publish diagnostics for all currently open files.
     */
    private fun publishDiagnosticsForOpenFiles() {
        ApplicationManager.getApplication().runReadAction {
            try {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val openFiles = fileEditorManager.openFiles
                
                for (file in openFiles) {
                    val uri = com.frenchef.intellijlsp.intellij.PsiMapper.virtualFileToUri(file)
                    publishDiagnosticsForFile(uri)
                }
            } catch (e: Exception) {
                log.warn("Error publishing diagnostics for open files", e)
            }
        }
    }

    /**
     * Clear diagnostics for a file.
     */
    fun clearDiagnosticsForFile(uri: String) {
        pendingUpdates[uri]?.cancel()
        pendingUpdates.remove(uri)
        
        val params = PublishDiagnosticsParams(
            uri = uri,
            version = null,
            diagnostics = emptyList()
        )
        
        broadcastDiagnostics(params)
        log.debug("Cleared diagnostics for $uri")
    }

    /**
     * Broadcast diagnostics to all connected clients.
     */
    private fun broadcastDiagnostics(params: PublishDiagnosticsParams) {
        try {
            val gson = com.frenchef.intellijlsp.protocol.LspGson.instance
            when (server) {
                is TcpLspServer -> {
                    val notification = com.frenchef.intellijlsp.protocol.models.LspNotification(
                        method = "textDocument/publishDiagnostics",
                        params = gson.toJsonTree(params)
                    )
                    server.broadcast(notification)
                }
                is UdsLspServer -> {
                    val notification = com.frenchef.intellijlsp.protocol.models.LspNotification(
                        method = "textDocument/publishDiagnostics",
                        params = gson.toJsonTree(params)
                    )
                    server.broadcast(notification)
                }
            }
        } catch (e: Exception) {
            log.warn("Error broadcasting diagnostics", e)
        }
    }

    /**
     * Stop the diagnostics handler and clean up.
     */
    fun stop() {
        log.info("Stopping diagnostics handler")
        scope.cancel()
        pendingUpdates.clear()
    }
}

