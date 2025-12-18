package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.DidChangeTextDocumentParams
import com.frenchef.intellijlsp.protocol.models.DidCloseTextDocumentParams
import com.frenchef.intellijlsp.protocol.models.DidOpenTextDocumentParams
import com.frenchef.intellijlsp.protocol.models.DidSaveTextDocumentParams
import com.frenchef.intellijlsp.services.LspProjectService
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * Handles document synchronization methods: didOpen, didChange, didClose, didSave.
 */
class DocumentSyncHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<DocumentSyncHandler>()
    private val gson = LspGson.instance

    fun register() {
        jsonRpcHandler.registerNotificationHandler("textDocument/didOpen", this::handleDidOpen)
        jsonRpcHandler.registerNotificationHandler("textDocument/didChange", this::handleDidChange)
        jsonRpcHandler.registerNotificationHandler("textDocument/didClose", this::handleDidClose)
        jsonRpcHandler.registerNotificationHandler("textDocument/didSave", this::handleDidSave)
    }

    /**
     * Handle textDocument/didOpen notification.
     * Called when a document is opened in the client.
     */
    private fun handleDidOpen(params: JsonElement?) {
        if (params == null || params.isJsonNull) {
            log.warn("didOpen called with null params")
            return
        }

        try {
            val didOpenParams = gson.fromJson(params, DidOpenTextDocumentParams::class.java)
            val doc = didOpenParams.textDocument

            val normalizedUri = com.frenchef.intellijlsp.util.LspUriUtil.normalize(doc.uri)

            log.info("Document opened: ${doc.uri} (${doc.languageId}, version ${doc.version})")
            if (normalizedUri != doc.uri) {
                log.info("didOpen uri normalize: ${doc.uri} -> $normalizedUri")
            }

            documentManager.openDocument(
                uri = normalizedUri,
                languageId = doc.languageId,
                version = doc.version,
                text = doc.text
            )

            // 文件打开时触发诊断刷新，确保主动推送高亮和诊断
            triggerDiagnosticsRefresh(normalizedUri)
            
        } catch (e: Exception) {
            log.error("Error handling didOpen", e)
        }
    }

    /**
     * Handle textDocument/didChange notification.
     * Called when a document is modified in the client.
     */
    private fun handleDidChange(params: JsonElement?) {
        if (params == null || params.isJsonNull) {
            log.warn("didChange called with null params")
            return
        }

        try {
            val didChangeParams = gson.fromJson(params, DidChangeTextDocumentParams::class.java)
            val doc = didChangeParams.textDocument

            val normalizedUri = com.frenchef.intellijlsp.util.LspUriUtil.normalize(doc.uri)

            log.debug(
                "Document changed: ${doc.uri} (version ${doc.version}, ${didChangeParams.contentChanges.size} changes)"
            )
            if (normalizedUri != doc.uri) {
                log.info("didChange uri normalize: ${doc.uri} -> $normalizedUri")
            }

            documentManager.changeDocument(
                uri = normalizedUri,
                version = doc.version,
                changes = didChangeParams.contentChanges
            )

            // Debug instrumentation: verify whether IntelliJ Document stays in sync with LSP didChange.
            // If IntelliJ Document is stale, semantic tokens/highlighting computed from PSI/Document will also be stale.
            run {
                val tracked = documentManager.getDocumentContent(normalizedUri)
                val trackedLen = tracked?.length
                val ideaDoc = documentManager.getIntellijDocument(normalizedUri)
                val ideaLen = ideaDoc?.textLength
                val ideaStamp = ideaDoc?.modificationStamp

                log.debug(
                    "didChange sync-check: trackedLen=$trackedLen, ideaLen=$ideaLen, ideaStamp=$ideaStamp, trackedVersion=${doc.version}"
                )

                if (trackedLen != null && ideaLen != null && trackedLen != ideaLen) {
                    log.warn(
                        "didChange sync-check mismatch: IntelliJ Document length differs from tracked content; highlighting may be stale"
                    )
                }
            }

            // Trigger diagnostics refresh for this file
            triggerDiagnosticsRefresh(normalizedUri)
        } catch (e: Exception) {
            log.error("Error handling didChange", e)
        }
    }

    /**
     * Handle textDocument/didClose notification.
     * Called when a document is closed in the client.
     */
    private fun handleDidClose(params: JsonElement?) {
        if (params == null || params.isJsonNull) {
            log.warn("didClose called with null params")
            return
        }

        try {
            val didCloseParams = gson.fromJson(params, DidCloseTextDocumentParams::class.java)
            val uri = didCloseParams.textDocument.uri
            val normalizedUri = com.frenchef.intellijlsp.util.LspUriUtil.normalize(uri)

            log.info("Document closed: $uri")
            if (normalizedUri != uri) {
                log.info("didClose uri normalize: $uri -> $normalizedUri")
            }

            documentManager.closeDocument(normalizedUri)
            
        } catch (e: Exception) {
            log.error("Error handling didClose", e)
        }
    }

    /**
     * Handle textDocument/didSave notification.
     * Called when a document is saved in the client.
     */
    private fun handleDidSave(params: JsonElement?) {
        if (params == null || params.isJsonNull) {
            log.warn("didSave called with null params")
            return
        }

        try {
            val didSaveParams = gson.fromJson(params, DidSaveTextDocumentParams::class.java)
            val uri = didSaveParams.textDocument.uri
            val normalizedUri = com.frenchef.intellijlsp.util.LspUriUtil.normalize(uri)

            log.info("Document saved: $uri")
            if (normalizedUri != uri) {
                log.info("didSave uri normalize: $uri -> $normalizedUri")
            }

            documentManager.saveDocument(normalizedUri)

            // After save, IntelliJ will re-parse the file and update its analysis
            // Trigger diagnostics refresh
            triggerDiagnosticsRefresh(normalizedUri)
            
        } catch (e: Exception) {
            log.error("Error handling didSave", e)
        }
    }

    /**
     * Trigger diagnostics refresh for a file.
     */
    private fun triggerDiagnosticsRefresh(uri: String) {
        val normalizedUri = com.frenchef.intellijlsp.util.LspUriUtil.normalize(uri)
        try {
            val projectService = project.getService(LspProjectService::class.java)
            projectService?.getDiagnosticsHandler()?.publishDiagnosticsForFile(normalizedUri)
        } catch (e: Exception) {
            log.warn("Error triggering diagnostics refresh for $normalizedUri", e)
        }
    }
}

