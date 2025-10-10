package com.frenchef.intellijlsp.protocol.models

import com.google.gson.JsonElement

/**
 * Represents a JSON-RPC response.
 */
data class LspResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement?,
    val result: JsonElement? = null,
    val error: ResponseError? = null
)

/**
 * Error object for JSON-RPC responses.
 */
data class ResponseError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * Standard JSON-RPC error codes.
 */
object ErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val SERVER_NOT_INITIALIZED = -32002
    const val UNKNOWN_ERROR_CODE = -32001
    const val REQUEST_CANCELLED = -32800
    const val CONTENT_MODIFIED = -32801
}

