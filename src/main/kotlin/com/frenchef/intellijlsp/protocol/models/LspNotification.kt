package com.frenchef.intellijlsp.protocol.models

import com.google.gson.JsonElement

/**
 * Represents a JSON-RPC notification (request without an id).
 */
data class LspNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement?
)

