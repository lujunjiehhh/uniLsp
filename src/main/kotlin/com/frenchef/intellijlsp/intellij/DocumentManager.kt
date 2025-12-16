package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.TextDocumentContentChangeEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages document synchronization between LSP client and IntelliJ.
 * Tracks document versions and applies incremental changes.
 */
class DocumentManager(private val project: Project) {
    private val log = logger<DocumentManager>()

    // Map of URI to document info
    private val documents = ConcurrentHashMap<String, DocumentInfo>()

    /**
     * Open a document and track it.
     */
    fun openDocument(uri: String, languageId: String, version: Int, text: String) {
        log.info("Opening document: $uri (version $version)")

        val docInfo = DocumentInfo(
            uri = uri,
            languageId = languageId,
            version = version,
            content = text
        )

        documents[uri] = docInfo
    }

    /**
     * Close a document and stop tracking it.
     */
    fun closeDocument(uri: String) {
        log.info("Closing document: $uri")
        documents.remove(uri)
    }

    /**
     * Apply incremental changes to a document.
     */
    fun changeDocument(uri: String, version: Int, changes: List<TextDocumentContentChangeEvent>) {
        val docInfo = documents[uri]

        if (docInfo == null) {
            log.warn("Attempted to change unopened document: $uri")
            return
        }

        log.debug("Changing document: $uri (version $version, ${changes.size} changes)")

        var content = docInfo.content

        for (change in changes) {
            content = if (change.range == null) {
                // Full document update
                change.text
            } else {
                // Incremental update
                applyIncrementalChange(content, change)
            }
        }

        documents[uri] = docInfo.copy(version = version, content = content)
    }

    /**
     * Save a document (trigger IntelliJ VFS refresh).
     */
    fun saveDocument(uri: String) {
        log.info("Saving document: $uri")

        val physicalFile = getPhysicalVirtualFile(uri)
        if (physicalFile != null) {
            physicalFile.refresh(false, false)
        }
    }

    /**
     * Get the current version of a document.
     */
    fun getDocumentVersion(uri: String): Int? {
        return documents[uri]?.version
    }

    /**
     * Get the content of a tracked document.
     */
    fun getDocumentContent(uri: String): String? {
        return documents[uri]?.content
    }

    /**
     * Check if a document is tracked.
     */
    fun isDocumentOpen(uri: String): Boolean {
        return documents.containsKey(uri)
    }

    /**
     * Get the VirtualFile for a URI.
     *
     * 支持反编译缓存 URI 反向查找：
     * 如果 URI 是缓存文件，返回原始 JAR 中的 VirtualFile
     */
    fun getVirtualFile(uri: String): VirtualFile? {
        return try {
            // 优先检查是否是反编译缓存 URI → 返回原始 JAR VirtualFile
            val originalVf = DecompileCache.getOriginalVirtualFile(uri)
            if (originalVf != null) {
                log.debug("缓存 URI 反向查找: $uri -> ${originalVf.url}")
                return originalVf
            }

            // 普通文件
            val path = URI(uri).path
            VirtualFileManager.getInstance().findFileByUrl("file://$path")
        } catch (e: Exception) {
            log.warn("Failed to get VirtualFile for URI: $uri", e)
            null
        }
    }

    /**
     * Get the IntelliJ Document for a URI.
     *
     * 注意：对于反编译缓存文件，必须拿到"磁盘上的缓存文件 Document"，
     * 而不是 cache->jar 反查后的 jar VirtualFile（jar 往往没有 Document）。
     */
    fun getIntellijDocument(uri: String): Document? {
        val physicalFile = getPhysicalVirtualFile(uri) ?: return null

        return ReadAction.compute<Document?, RuntimeException> {
            FileDocumentManager.getInstance().getDocument(physicalFile)
        }
    }

    /**
     * 获取磁盘上的物理文件（不做 cache→jar 反查）
     */
    private fun getPhysicalVirtualFile(uri: String): VirtualFile? {
        return try {
            val rawPath = URI(uri).path ?: return null
            val localPath =
                if (rawPath.length >= 3 && rawPath[0] == '/' && rawPath[2] == ':') rawPath.substring(1) else rawPath

            // First try to find in VFS (fast)
            var file = LocalFileSystem.getInstance().findFileByPath(localPath)

            // If not found, try to refresh and find (slower but handles new files)
            if (file == null) {
                file = LocalFileSystem.getInstance().refreshAndFindFileByPath(localPath)
            }

            file
        } catch (e: Exception) {
            log.warn("Failed to get physical VirtualFile for URI: $uri", e)
            null
        }
    }

    /**
     * Apply an incremental change to document content.
     */
    private fun applyIncrementalChange(content: String, change: TextDocumentContentChangeEvent): String {
        val range = change.range ?: return change.text

        val lines = content.split("\n")
        val startLine = range.start.line
        val startChar = range.start.character
        val endLine = range.end.line
        val endChar = range.end.character

        if (startLine < 0 || startLine >= lines.size) {
            log.warn("Invalid start line: $startLine (document has ${lines.size} lines)")
            return content
        }

        if (endLine < 0 || endLine >= lines.size) {
            log.warn("Invalid end line: $endLine (document has ${lines.size} lines)")
            return content
        }

        // Build the new content
        val result = StringBuilder()

        // Add lines before the change
        for (i in 0 until startLine) {
            result.append(lines[i])
            if (i < lines.size - 1) result.append("\n")
        }

        // Add the part of the start line before the change
        if (startLine < lines.size) {
            val startLineContent = lines[startLine]
            if (startChar <= startLineContent.length) {
                result.append(startLineContent.substring(0, startChar))
            }
        }

        // Add the new text
        result.append(change.text)

        // Add the part of the end line after the change
        if (endLine < lines.size) {
            val endLineContent = lines[endLine]
            if (endChar <= endLineContent.length) {
                result.append(endLineContent.substring(endChar))
            }
        }

        // Add lines after the change
        for (i in endLine + 1 until lines.size) {
            result.append("\n")
            result.append(lines[i])
        }

        return result.toString()
    }

    /**
     * Information about a tracked document.
     */
    private data class DocumentInfo(
        val uri: String,
        val languageId: String,
        val version: Int,
        val content: String
    )
}
