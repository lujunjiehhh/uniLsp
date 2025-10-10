package com.frenchef.intellijlsp.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Writes JSON-RPC messages to an output stream.
 * Messages follow the format:
 * Content-Length: {length}\r\n
 * \r\n
 * {JSON payload}
 */
class MessageWriter(private val output: OutputStream) {
    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .create()

    /**
     * Write a message to the output stream.
     * 
     * @param message The message object to serialize and send
     */
    fun writeMessage(message: Any) {
        val json = gson.toJson(message)
        val content = json.toByteArray(StandardCharsets.UTF_8)
        
        val header = "Content-Length: ${content.size}\r\n\r\n"
        val headerBytes = header.toByteArray(StandardCharsets.UTF_8)
        
        synchronized(output) {
            output.write(headerBytes)
            output.write(content)
            output.flush()
        }
    }
}

