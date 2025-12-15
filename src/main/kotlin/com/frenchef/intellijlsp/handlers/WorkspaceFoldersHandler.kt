package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.DidChangeWorkspaceFoldersParams
import com.frenchef.intellijlsp.protocol.models.WorkspaceFolder
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * Phase 10: Workspace Folders Handler (T028)
 *
 * 处理 LSP workspace/didChangeWorkspaceFolders 通知。 维护工作区文件夹列表状态。
 */
class WorkspaceFoldersHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler
) {
    private val log = logger<WorkspaceFoldersHandler>()
    private val gson = LspGson.instance

    /** 当前工作区文件夹列表 */
    private val workspaceFolders = mutableListOf<WorkspaceFolder>()

    /** 注册 Workspace Folders handler */
    fun register() {
        jsonRpcHandler.registerNotificationHandler(
            "workspace/didChangeWorkspaceFolders",
            this::handleDidChangeWorkspaceFolders
        )
        log.info("WorkspaceFoldersHandler registered")
    }

    /** 初始化工作区文件夹列表（从 initialize 请求） */
    fun initializeWorkspaceFolders(folders: List<WorkspaceFolder>?) {
        workspaceFolders.clear()
        folders?.let {
            workspaceFolders.addAll(it)
            log.info("Initialized ${it.size} workspace folders")
        }
    }

    /** 处理 workspace/didChangeWorkspaceFolders 通知 */
    private fun handleDidChangeWorkspaceFolders(params: JsonElement?) {
        if (params == null || params.isJsonNull) {
            log.debug("didChangeWorkspaceFolders: null params")
            return
        }

        try {
            val changeParams = gson.fromJson(params, DidChangeWorkspaceFoldersParams::class.java)
            val event = changeParams.event

            // 移除文件夹
            for (removed in event.removed) {
                val wasRemoved = workspaceFolders.removeIf { it.uri == removed.uri }
                if (wasRemoved) {
                    log.info("Removed workspace folder: ${removed.name} (${removed.uri})")
                }
            }

            // 添加文件夹
            for (added in event.added) {
                // 避免重复添加
                if (workspaceFolders.none { it.uri == added.uri }) {
                    workspaceFolders.add(added)
                    log.info("Added workspace folder: ${added.name} (${added.uri})")
                }
            }

            log.info("Current workspace folders: ${workspaceFolders.size}")
        } catch (e: Exception) {
            log.error("Error handling didChangeWorkspaceFolders", e)
        }
    }

    /** 获取当前工作区文件夹列表 */
    fun getWorkspaceFolders(): List<WorkspaceFolder> = workspaceFolders.toList()
}
