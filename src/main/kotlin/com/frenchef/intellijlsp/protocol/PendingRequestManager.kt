package com.frenchef.intellijlsp.protocol

import com.frenchef.intellijlsp.protocol.models.ErrorCodes
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 管理双向通信中的 pending requests。
 *
 * 负责：
 * - 生成唯一请求 ID
 * - 维护 pending requests 映射
 * - 处理请求超时（30s）
 */
class PendingRequestManager {
    private val log = logger<PendingRequestManager>()

    /** 请求 ID 生成器 */
    private val requestIdCounter = AtomicLong(1)

    /** Pending requests 映射：ID -> CompletableFuture */
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()

    /** 默认超时时间（毫秒） */
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }

    /** 生成下一个唯一请求 ID。 */
    fun nextRequestId(): String {
        return "server-${requestIdCounter.getAndIncrement()}"
    }

    /**
     * 注册一个 pending request 并返回 Future。
     *
     * @param id 请求 ID
     * @param method 请求方法名（用于日志）
     * @return CompletableFuture 用于等待响应
     */
    fun registerRequest(id: String, method: String): CompletableFuture<JsonElement?> {
        val future = CompletableFuture<JsonElement?>()
        val pendingRequest =
            PendingRequest(
                id = id,
                method = method,
                future = future,
                createdAt = System.currentTimeMillis()
            )

        pendingRequests[id] = pendingRequest
        log.info("Registered pending request: id=$id, method=$method")

        // 设置超时
        scheduleTimeout(id, DEFAULT_TIMEOUT_MS)

        return future
    }

    /**
     * 完成一个 pending request（成功）。
     *
     * @param id 请求 ID
     * @param result 响应结果
     * @return true 如果请求存在并被完成
     */
    fun completeRequest(id: String, result: JsonElement?): Boolean {
        val pending = pendingRequests.remove(id)
        if (pending != null) {
            val elapsed = System.currentTimeMillis() - pending.createdAt
            log.info(
                "Completed pending request: id=$id, method=${pending.method}, elapsed=${elapsed}ms"
            )
            pending.future.complete(result)
            return true
        }
        log.warn("No pending request found for id=$id")
        return false
    }

    /**
     * 完成一个 pending request（失败）。
     *
     * @param id 请求 ID
     * @param error 错误信息
     * @return true 如果请求存在并被完成
     */
    fun failRequest(id: String, error: Throwable): Boolean {
        val pending = pendingRequests.remove(id)
        if (pending != null) {
            val elapsed = System.currentTimeMillis() - pending.createdAt
            log.warn(
                "Failed pending request: id=$id, method=${pending.method}, elapsed=${elapsed}ms, error=${error.message}"
            )
            pending.future.completeExceptionally(error)
            return true
        }
        with(log) { warn("No pending request found for id=$id") }
        return false
    }

    /**
     * 取消一个 pending request。
     *
     * @param id 请求 ID
     * @param reason 取消原因
     * @return true 如果请求存在并被取消
     */
    fun cancelRequest(id: String, reason: String = "Request cancelled"): Boolean {
        val pending = pendingRequests.remove(id)
        if (pending != null) {
            log.info("Cancelled pending request: id=$id, method=${pending.method}, reason=$reason")
            pending.future.cancel(false)
            return true
        }
        return false
    }

    /** 获取当前 pending requests 数量。 */
    fun pendingCount(): Int = pendingRequests.size

    /** 取消所有 pending requests（用于 shutdown）。 */
    fun cancelAll(reason: String = "Server shutdown") {
        val count = pendingRequests.size
        pendingRequests.keys.toList().forEach { id -> cancelRequest(id, reason) }
        if (count > 0) {
            log.info("Cancelled $count pending requests: reason=$reason")
        }
    }

    /** 设置请求超时。 */
    private fun scheduleTimeout(id: String, timeoutMs: Long) {
        CompletableFuture.delayedExecutor(timeoutMs, TimeUnit.MILLISECONDS).execute {
            val pending = pendingRequests.remove(id)
            if (pending != null) {
                log.warn(
                    "Request timeout: id=$id, method=${pending.method}, timeout=${timeoutMs}ms"
                )
                pending.future.completeExceptionally(
                    LspException(
                        code = ErrorCodes.REQUEST_CANCELLED,
                        message = "Request timeout after ${timeoutMs}ms"
                    )
                )
            }
        }
    }

    /** 内部类：表示一个 pending request。 */
    private data class PendingRequest(
        val id: String,
        val method: String,
        val future: CompletableFuture<JsonElement?>,
        val createdAt: Long
    )
}
