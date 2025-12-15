package com.frenchef.intellijlsp.protocol

import com.frenchef.intellijlsp.protocol.models.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * Handles JSON-RPC message routing and dispatching to appropriate handlers.
 *
 * 支持双向通信：
 * - 接收客户端请求并分发到 handlers
 * - 发送请求到客户端并等待响应
 */
class JsonRpcHandler(private val project: Project) {
    private val log = logger<JsonRpcHandler>()
    private val gson = Gson()

    // Handler registry
    private val requestHandlers = mutableMapOf<String, RequestHandler>()
    private val notificationHandlers = mutableMapOf<String, NotificationHandler>()

    // 双向通信支持 (Phase 10)
    private val pendingRequestManager = PendingRequestManager()

    // 用于发送消息到客户端的回调
    private var messageSender: ((String) -> Unit)? = null

    /**
     * 设置消息发送回调。
     *
     * @param sender 发送消息到客户端的函数
     */
    fun setMessageSender(sender: (String) -> Unit) {
        this.messageSender = sender
    }

    /** Register a request handler for a specific method. */
    fun registerRequestHandler(method: String, handler: RequestHandler) {
        requestHandlers[method] = handler
    }

    /** Register a notification handler for a specific method. */
    fun registerNotificationHandler(method: String, handler: NotificationHandler) {
        notificationHandlers[method] = handler
    }

    /**
     * Handle an incoming JSON-RPC message.
     *
     * @param json The JSON object representing the message
     * @return A response object, or null for notifications
     */
    fun handleMessage(json: JsonObject): LspResponse? {
        return try {
            val id = json.get("id")
            val method = json.get("method")?.asString
            val params = json.get("params")

            log.info(
                "Processing JSON-RPC message: method=$method, hasId=${id != null && !id.isJsonNull}"
            )

            if (method == null) {
                // This is a response to a request we sent
                if (id != null && !id.isJsonNull) {
                    handleResponse(id.asString, json)
                } else {
                    log.warn("Received message without method or id: $json")
                }
                return null
            }

            if (id == null || id.isJsonNull) {
                // This is a notification (no response expected)
                log.info("Handling notification: $method")
                handleNotification(method, params)
                null
            } else {
                // This is a request (response expected)
                log.info("Handling request: $method with id: $id")
                val response = handleRequest(id, method, params)
                log.info(
                    "Generated response for $method: ${if (response.error != null) "ERROR" else "SUCCESS"}"
                )
                response
            }
        } catch (e: Exception) {
            log.error("Error handling message: $json", e)
            createErrorResponse(
                json.get("id"),
                ErrorCodes.INTERNAL_ERROR,
                "Internal error: ${e.message}"
            )
        }
    }

    /**
     * 处理来自客户端的响应（Server → Client 请求的响应）。
     *
     * @param id 请求 ID
     * @param json 响应 JSON 对象
     */
    private fun handleResponse(id: String, json: JsonObject) {
        val result = json.get("result")
        val error = json.get("error")

        if (error != null && !error.isJsonNull) {
            val errorObj = gson.fromJson(error, ResponseError::class.java)
            log.warn("Received error response for id=$id: ${errorObj.message}")
            pendingRequestManager.failRequest(
                id,
                LspException(errorObj.code, errorObj.message, errorObj.data)
            )
        } else {
            log.info("Received success response for id=$id")
            pendingRequestManager.completeRequest(id, result)
        }
    }

    /**
     * 发送请求到客户端并等待响应。
     *
     * @param method 请求方法名
     * @param params 请求参数
     * @return CompletableFuture 包含响应结果
     * @throws IllegalStateException 如果 messageSender 未设置
     */
    fun sendRequest(
        method: String,
        params: Any?
    ): java.util.concurrent.CompletableFuture<JsonElement?> {
        val sender = messageSender ?: throw IllegalStateException("Message sender not configured")

        val id = pendingRequestManager.nextRequestId()
        val future = pendingRequestManager.registerRequest(id, method)

        val request =
            LspRequest(
                id = JsonPrimitive(id),
                method = method,
                params = if (params != null) gson.toJsonTree(params) else null
            )

        val message = gson.toJson(request)
        log.info("Sending request to client: method=$method, id=$id")

        try {
            sender(message)
        } catch (e: Exception) {
            log.error("Failed to send request: $e")
            pendingRequestManager.failRequest(id, e)
        }

        return future
    }

    /** Handle a request and return a response. */
    private fun handleRequest(id: JsonElement, method: String, params: JsonElement?): LspResponse {
        val handler = requestHandlers[method]

        if (handler == null) {
            log.warn("No handler registered for method: $method")
            return createErrorResponse(id, ErrorCodes.METHOD_NOT_FOUND, "Method not found: $method")
        }

        return try {
            val result = handler.handle(params)
            LspResponse(id = id, result = result)
        } catch (e: LspException) {
            log.warn("LSP exception in handler for $method", e)
            createErrorResponse(id, e.code, e.message ?: "Unknown error", e.data)
        } catch (e: Exception) {
            log.error("Error in handler for $method", e)
            createErrorResponse(id, ErrorCodes.INTERNAL_ERROR, "Internal error: ${e.message}")
        }
    }

    /** Handle a notification (no response). */
    private fun handleNotification(method: String, params: JsonElement?) {
        val handler = notificationHandlers[method]

        if (handler == null) {
            log.debug("No handler registered for notification: $method")
            return
        }

        try {
            handler.handle(params)
        } catch (e: Exception) {
            log.error("Error in notification handler for $method", e)
        }
    }

    /** Create an error response. */
    private fun createErrorResponse(
        id: JsonElement?,
        code: Int,
        message: String,
        data: JsonElement? = null
    ): LspResponse {
        return LspResponse(
            id = id,
            error = ResponseError(code = code, message = message, data = data)
        )
    }

    /** Send a notification to the client. */
    fun sendNotification(method: String, params: Any?): LspNotification {
        val paramsElement =
            if (params != null) {
                gson.toJsonTree(params)
            } else {
                null
            }

        return LspNotification(method = method, params = paramsElement)
    }

    /** 获取 PendingRequestManager（用于 shutdown 时取消所有 pending requests）。 */
    fun getPendingRequestManager(): PendingRequestManager = pendingRequestManager
}

/** Interface for request handlers. */
fun interface RequestHandler {
    /**
     * Handle a request and return the result.
     *
     * @param params The request parameters
     * @return The result as a JsonElement
     * @throws LspException if there's an LSP protocol error
     */
    fun handle(params: JsonElement?): JsonElement?
}

/** Interface for notification handlers. */
fun interface NotificationHandler {
    /**
     * Handle a notification (no return value).
     *
     * @param params The notification parameters
     */
    fun handle(params: JsonElement?)
}

/** Exception thrown by LSP handlers. */
class LspException(val code: Int, message: String, val data: JsonElement? = null) :
    Exception(message)
