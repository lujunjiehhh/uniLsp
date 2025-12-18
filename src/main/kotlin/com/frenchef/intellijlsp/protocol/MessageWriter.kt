package com.frenchef.intellijlsp.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.logger
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
    private val log = logger<MessageWriter>()

    private val gson: Gson =
        GsonBuilder()
            .serializeNulls()
            .create()

    /**
     * Write a message to the output stream.
     *
     * @param message The message object to serialize and send
     */
    fun writeMessage(message: Any) {
        val startedNs = System.nanoTime()

        val json = gson.toJson(message)
        val content = json.toByteArray(StandardCharsets.UTF_8)

        val header = "Content-Length: ${content.size}\r\n\r\n"
        val headerBytes = header.toByteArray(StandardCharsets.UTF_8)

        synchronized(output) {
            output.write(headerBytes)
            output.write(content)
            output.flush()
        }

        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000.0

        // Debug instrumentation: detect backpressure / blocking writes.
        // If writes block here, the server read loop might be stuck when sending responses/notifications.
        val largeBytes = content.size >= 256 * 1024
        val slowWrite = elapsedMs >= 200
        if (largeBytes || slowWrite) {
            log.warn("Slow/large LSP write: bytes=${content.size}, elapsedMs=${"%.1f".format(elapsedMs)}")
        } else {
            log.debug("LSP write ok: bytes=${content.size}, elapsedMs=${"%.1f".format(elapsedMs)}")
        }
    }
}

