package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.DidChangeWatchedFilesParams
import com.frenchef.intellijlsp.protocol.models.FileChangeType
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Phase 10: File Watching Handler (T031)
 *
 * 处理 LSP workspace/didChangeWatchedFiles 通知。 根据文件事件类型执行相应操作。
 */
class FileWatchingHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler
) {
    private val log = logger<FileWatchingHandler>()
    private val gson = LspGson.instance

    /** 注册 File Watching handler */
    fun register() {
        jsonRpcHandler.registerNotificationHandler(
            "workspace/didChangeWatchedFiles",
            this::handleDidChangeWatchedFiles
        )
        log.info("FileWatchingHandler registered")
    }

    /** 处理 workspace/didChangeWatchedFiles 通知 */
    private fun handleDidChangeWatchedFiles(params: JsonElement?) {
        if (params == null || params.isJsonNull) {
            log.debug("didChangeWatchedFiles: null params")
            return
        }

        try {
            val changeParams = gson.fromJson(params, DidChangeWatchedFilesParams::class.java)

            for (event in changeParams.changes) {
                when (event.type) {
                    FileChangeType.CREATED -> handleFileCreated(event.uri)
                    FileChangeType.CHANGED -> handleFileChanged(event.uri)
                    FileChangeType.DELETED -> handleFileDeleted(event.uri)
                    else -> log.warn("Unknown file change type: ${event.type}")
                }
            }
        } catch (e: Exception) {
            log.error("Error handling didChangeWatchedFiles", e)
        }
    }

    /** 处理文件创建事件 */
    private fun handleFileCreated(uri: String) {
        log.debug("File created: $uri")
        refreshVirtualFile(uri)
    }

    /** 处理文件修改事件 */
    private fun handleFileChanged(uri: String) {
        log.debug("File changed: $uri")
        refreshVirtualFile(uri)
        // TODO: 触发诊断刷新（如果需要）
    }

    /** 处理文件删除事件 */
    private fun handleFileDeleted(uri: String) {
        log.debug("File deleted: $uri")
        refreshVirtualFile(uri)
        // TODO: 清理相关状态（如果需要）
    }

    /** 刷新 VirtualFile 状态 */
    private fun refreshVirtualFile(uri: String) {
        try {
            val normalizedUri = uri.replace("file:///", "file://")
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl(normalizedUri)

            if (virtualFile != null) {
                virtualFile.refresh(true, false)
                log.debug("Refreshed VirtualFile: ${virtualFile.path}")
            } else {
                // 文件可能是新创建的，刷新父目录
                val parentPath = normalizedUri.substringBeforeLast("/")
                val parentFile = VirtualFileManager.getInstance().findFileByUrl(parentPath)
                parentFile?.refresh(true, true)
                log.debug("Refreshed parent directory for: $uri")
            }
        } catch (e: Exception) {
            log.warn("Error refreshing VirtualFile: $uri", e)
        }
    }
}
