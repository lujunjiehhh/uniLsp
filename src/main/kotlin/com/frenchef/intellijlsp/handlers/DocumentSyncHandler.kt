package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspException
import com.frenchef.intellijlsp.protocol.models.*
import com.google.gson.Gson
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
    private val gson = Gson()

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
            
            log.info("Document opened: ${doc.uri} (${doc.languageId}, version ${doc.version})")
            
            documentManager.openDocument(
                uri = doc.uri,
                languageId = doc.languageId,
                version = doc.version,
                text = doc.text
            )
            
            // Note: We could trigger diagnostics here, but we'll do that in the DiagnosticsHandler
            
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
            
            log.debug("Document changed: ${doc.uri} (version ${doc.version}, ${didChangeParams.contentChanges.size} changes)")
            
            documentManager.changeDocument(
                uri = doc.uri,
                version = doc.version,
                changes = didChangeParams.contentChanges
            )
            
            // Note: For now, we don't immediately sync changes to IntelliJ
            // The user will save the file, and IntelliJ will pick it up from disk
            
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
            
            log.info("Document closed: $uri")
            
            documentManager.closeDocument(uri)
            
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
            
            log.info("Document saved: $uri")
            
            documentManager.saveDocument(uri)
            
            // After save, IntelliJ will re-parse the file and update its analysis
            // We can then publish updated diagnostics
            
        } catch (e: Exception) {
            log.error("Error handling didSave", e)
        }
    }
}

