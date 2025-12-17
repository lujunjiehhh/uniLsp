package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.BackgroundAnalyzer
import com.frenchef.intellijlsp.intellij.DiagnosticsProvider
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.InspectionDiagnosticsProvider
import com.frenchef.intellijlsp.protocol.models.Diagnostic
import com.frenchef.intellijlsp.protocol.models.PublishDiagnosticsParams
import com.frenchef.intellijlsp.server.LspServer
import com.frenchef.intellijlsp.server.TcpLspServer
import com.frenchef.intellijlsp.server.UdsLspServer
import com.frenchef.intellijlsp.services.LspProjectService
import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles publishing diagnostics to LSP clients.
 * 
 * 使用三重策略获取诊断：
 * 1. BackgroundAnalyzer - 确保文件在编辑器中打开以触发 DaemonCodeAnalyzer
 * 2. MarkupModel (DiagnosticsProvider) - 快速但需要文件在编辑器中打开
 * 3. Inspection API (InspectionDiagnosticsProvider) - 较慢但不需要打开文件
 */
class DiagnosticsHandler(
    private val project: Project,
    private val documentManager: DocumentManager,
    private val diagnosticsProvider: DiagnosticsProvider,
    private val server: LspServer
) {
    private val log = logger<DiagnosticsHandler>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Background analyzer for triggering code analysis
    private val backgroundAnalyzer = BackgroundAnalyzer(project)

    // Inspection-based provider for files not open in editor
    private val inspectionProvider = InspectionDiagnosticsProvider(project)
    
    // Track pending diagnostic updates per file to debounce
    private val pendingUpdates = ConcurrentHashMap<String, Job>()

    private fun isPushDiagnosticsEnabled(): Boolean {
        return !project.getService(LspProjectService::class.java).isUsePullDiagnostics()
    }

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
     * Uses BackgroundAnalyzer + MarkupModel + Inspection API approaches.
     */
    fun publishDiagnosticsForFile(uri: String) {
        if (!isPushDiagnosticsEnabled()) {
            LspLogger.debug("Diagnostics", "Pull diagnostics enabled; suppressing publishDiagnostics for $uri")
            return
        }
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

                // Step 1: 确保文件在编辑器中打开以触发 DaemonCodeAnalyzer
                if (!virtualFile.isValid) return@launch
                backgroundAnalyzer.ensureFileOpenForAnalysis(virtualFile, uri)

                // Step 2 & 3: 智能轮询 MarkupModel (最多等待 1 秒)
                var diagnostics = emptyList<Diagnostic>()
                var source = "MarkupModel"
                var attempts = 0
                val maxAttempts = 10 // 10 * 100ms = 1s max

                while (attempts < maxAttempts) {
                    attempts++
                    delay(100) // 使用 delay 而不是 Thread.sleep，因为我们在协程中

                    if (!virtualFile.isValid) {
                        LspLogger.debug("Diagnostics", "文件已失效，停止轮询: $uri")
                        return@launch
                    }

                    val currentDiagnostics = diagnosticsProvider.getDiagnosticsByUri(uri, virtualFile, document)
                    if (currentDiagnostics.isNotEmpty()) {
                        // 发现诊断！立即使用
                        LspLogger.debug(
                            "Diagnostics",
                            "MarkupModel 快速返回 ${currentDiagnostics.size} 个诊断 (尝试 $attempts): $uri"
                        )
                        diagnostics = currentDiagnostics
                        break
                    }
                }

                if (!virtualFile.isValid) return@launch

                // 如果轮询结束后仍然为空，再次尝试（以防万一）
                if (diagnostics.isEmpty()) {
                    diagnostics = diagnosticsProvider.getDiagnosticsByUri(uri, virtualFile, document)
                    if (diagnostics.isNotEmpty()) {
                        LspLogger.info("Diagnostics", "MarkupModel 最终返回 ${diagnostics.size} 个诊断: $uri")
                    }
                } else {
                    LspLogger.info("Diagnostics", "MarkupModel 返回 ${diagnostics.size} 个诊断: $uri")
                }

                // Step 4: If still no diagnostics from MarkupModel, try Inspection API
                if (diagnostics.isEmpty()) {
                    if (!virtualFile.isValid) return@launch

                    LspLogger.info("Diagnostics", "Trying Inspection API for $uri")
                    try {
                        diagnostics = ReadAction.compute<List<Diagnostic>, RuntimeException> {
                            if (!virtualFile.isValid) return@compute emptyList()
                            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                            if (psiFile != null) {
                                inspectionProvider.getDiagnostics(psiFile, document)
                            } else {
                                emptyList()
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略文件访问异常
                        LspLogger.warn("Diagnostics", "Inspection API 异常 (可能是文件已删除): ${e.message}")
                    }

                    source = "InspectionAPI"
                    if (diagnostics.isNotEmpty()) {
                        LspLogger.info(
                            "Diagnostics",
                            "Inspection API returned ${diagnostics.size} diagnostics for $uri"
                        )
                    }
                }
                
                val version = documentManager.getDocumentVersion(uri)
                
                val params = PublishDiagnosticsParams(
                    uri = uri,
                    version = version,
                    diagnostics = diagnostics
                )
                
                // Send notification to client
                broadcastDiagnostics(params)

                LspLogger.info("Diagnostics", "Published ${diagnostics.size} diagnostics ($source) for $uri")
                
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
                    val uri = com.frenchef.intellijlsp.intellij.PsiMapper.virtualFileToUri(file, project)
                    if (uri != null) {
                        publishDiagnosticsForFile(uri)
                    }
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
        if (!isPushDiagnosticsEnabled()) {
            return
        }
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
        if (!isPushDiagnosticsEnabled()) {
            return
        }
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
        backgroundAnalyzer.dispose()
    }
}

