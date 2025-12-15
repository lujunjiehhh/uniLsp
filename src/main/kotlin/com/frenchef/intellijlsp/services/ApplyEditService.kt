package com.frenchef.intellijlsp.services

import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.ApplyWorkspaceEditParams
import com.frenchef.intellijlsp.protocol.models.ApplyWorkspaceEditResult
import com.frenchef.intellijlsp.protocol.models.ClientCapabilities
import com.frenchef.intellijlsp.protocol.models.WorkspaceEdit
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 服务端发起编辑服务。
 *
 * 负责向客户端发送 `workspace/applyEdit` 请求，应用 WorkspaceEdit。
 *
 * 根据 LSP 3.17 规范：
 * - Server 发送请求到 Client
 * - Client 应用编辑并返回结果
 * - 如果 Client 不支持 applyEdit，则不发送请求
 */
class ApplyEditService(
    private val jsonRpcHandler: JsonRpcHandler,
    private val clientCapabilitiesProvider: () -> ClientCapabilities?
) {
    private val log = logger<ApplyEditService>()
    private val gson = Gson()

    companion object {
        /** workspace/applyEdit 方法名 */
        const val METHOD = "workspace/applyEdit"

        /** 默认超时时间（秒） */
        const val DEFAULT_TIMEOUT_SECONDS = 30L
    }

    /** 检查客户端是否支持 workspace/applyEdit。 */
    fun isSupported(): Boolean {
        val capabilities = clientCapabilitiesProvider()
        return capabilities?.workspace?.applyEdit == true
    }

    /**
     * 发送 workspace/applyEdit 请求到客户端。
     *
     * @param edit 要应用的 WorkspaceEdit
     * @param label 可选的编辑描述（显示在 UI 中）
     * @return ApplyWorkspaceEditResult，如果客户端不支持则返回 null
     */
    fun applyEdit(edit: WorkspaceEdit, label: String? = null): ApplyWorkspaceEditResult? {
        if (!isSupported()) {
            log.info("Client does not support workspace/applyEdit, skipping")
            return null
        }

        val params = ApplyWorkspaceEditParams(label = label, edit = edit)

        log.info(
            "Sending workspace/applyEdit request: label=$label, changes=${edit.changes?.size ?: 0} files"
        )

        return try {
            val future = jsonRpcHandler.sendRequest(METHOD, params)
            val result = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (result != null) {
                val applyResult = gson.fromJson(result, ApplyWorkspaceEditResult::class.java)
                log.info(
                    "workspace/applyEdit result: applied=${applyResult.applied}, failureReason=${applyResult.failureReason}"
                )
                applyResult
            } else {
                log.warn("workspace/applyEdit returned null result")
                ApplyWorkspaceEditResult(applied = false, failureReason = "No result from client")
            }
        } catch (e: Exception) {
            log.error("workspace/applyEdit failed: ${e.message}", e)
            ApplyWorkspaceEditResult(applied = false, failureReason = e.message)
        }
    }

    /**
     * 异步发送 workspace/applyEdit 请求。
     *
     * @param edit 要应用的 WorkspaceEdit
     * @param label 可选的编辑描述
     * @return CompletableFuture 包含结果，如果客户端不支持则返回已完成的 null Future
     */
    fun applyEditAsync(
        edit: WorkspaceEdit,
        label: String? = null
    ): CompletableFuture<ApplyWorkspaceEditResult?> {
        if (!isSupported()) {
            log.info("Client does not support workspace/applyEdit, skipping")
            return CompletableFuture.completedFuture(null)
        }

        val params = ApplyWorkspaceEditParams(label = label, edit = edit)

        log.info("Sending async workspace/applyEdit request: label=$label")

        return jsonRpcHandler
            .sendRequest(METHOD, params)
            .thenApply { result ->
                if (result != null) {
                    val applyResult =
                        gson.fromJson(result, ApplyWorkspaceEditResult::class.java)
                    log.info("workspace/applyEdit result: applied=${applyResult.applied}")
                    applyResult
                } else {
                    log.warn("workspace/applyEdit returned null result")
                    ApplyWorkspaceEditResult(
                        applied = false,
                        failureReason = "No result from client"
                    )
                }
            }
            .exceptionally { e ->
                log.error("workspace/applyEdit failed: ${e.message}", e)
                ApplyWorkspaceEditResult(applied = false, failureReason = e.message)
            }
    }
}
