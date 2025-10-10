package com.frenchef.intellijlsp.protocol.models

import com.google.gson.JsonElement

/**
 * Represents a JSON-RPC request.
 */
data class LspRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement,
    val method: String,
    val params: JsonElement?
)

