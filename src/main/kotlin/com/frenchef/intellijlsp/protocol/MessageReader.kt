package com.frenchef.intellijlsp.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Reads JSON-RPC messages from an input stream.
 * Messages follow the format:
 * Content-Length: {length}\r\n
 * \r\n
 * {JSON payload}
 */
class MessageReader(private val input: InputStream) {
    private val log = logger<MessageReader>()
    private val gson = Gson()

    /**
     * Read the next message from the input stream.
     * 
     * @return The JSON payload as a JsonObject, or null if the stream is closed
     * @throws Exception if the message format is invalid
     */
    fun readMessage(): JsonObject? {
        try {
            log.info("Waiting to read message...")
            
            // Read headers
            val headers = mutableMapOf<String, String>()
            
            while (true) {
                val line = readLine() ?: run {
                    log.info("Stream closed (EOF while reading headers)")
                    return null
                }
                
                log.debug("Read header line: '$line'")
                
                if (line.isEmpty()) {
                    // Empty line indicates end of headers
                    log.debug("End of headers")
                    break
                }
                
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0].trim().lowercase()] = parts[1].trim()
                }
            }
            
            log.info("Headers: $headers")
            
            // Get content length
            val contentLengthStr = headers["content-length"]
                ?: throw IllegalArgumentException("Missing Content-Length header")
            
            val contentLength = contentLengthStr.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid Content-Length: $contentLengthStr")
            
            log.info("Reading message content of length: $contentLength")
            
            // Read content
            val content = ByteArray(contentLength)
            var totalRead = 0
            
            while (totalRead < contentLength) {
                val read = input.read(content, totalRead, contentLength - totalRead)
                if (read == -1) {
                    throw java.io.EOFException("Unexpected end of stream while reading message content")
                }
                totalRead += read
            }
            
            val jsonString = String(content, StandardCharsets.UTF_8)
            log.info("Read complete message: ${jsonString.take(200)}...") // Log first 200 chars
            
            return JsonParser.parseString(jsonString).asJsonObject
        } catch (e: Exception) {
            log.error("Error reading message", e)
            throw e
        }
    }

    /**
     * Read a line from the input stream (up to \r\n or \n).
     * 
     * @return The line without the line terminator, or null if EOF
     */
    private fun readLine(): String? {
        val buffer = StringBuilder()
        var prevChar: Char? = null
        
        while (true) {
            val byte = input.read()
            if (byte == -1) {
                return if (buffer.isEmpty()) null else buffer.toString()
            }
            
            val char = byte.toChar()
            
            if (char == '\n') {
                // End of line - remove trailing \r if present
                if (buffer.isNotEmpty() && buffer.last() == '\r') {
                    buffer.setLength(buffer.length - 1)
                }
                return buffer.toString()
            }
            
            buffer.append(char)
            prevChar = char
        }
    }
}

