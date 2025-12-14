package com.frenchef.intellijlsp.protocol

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** T043: MessageReader 单元测试 */
class MessageReaderTest {

    @Test
    fun `readMessage should parse valid LSP message`() {
        val json = """{"jsonrpc":"2.0","id":1,"method":"initialize"}"""
        val message = "Content-Length: ${json.length}\r\n\r\n$json"
        val input = ByteArrayInputStream(message.toByteArray(StandardCharsets.UTF_8))

        val reader = MessageReader(input)
        val result = reader.readMessage()

        assertNotNull(result)
        assertEquals("2.0", result!!["jsonrpc"].asString)
        assertEquals(1, result["id"].asInt)
        assertEquals("initialize", result["method"].asString)
    }

    @Test
    fun `readMessage should handle multiple headers`() {
        val json = """{"method":"test"}"""
        val message =
                "Content-Length: ${json.length}\r\nContent-Type: application/json\r\n\r\n$json"
        val input = ByteArrayInputStream(message.toByteArray(StandardCharsets.UTF_8))

        val reader = MessageReader(input)
        val result = reader.readMessage()

        assertNotNull(result)
        assertEquals("test", result!!["method"].asString)
    }

    @Test
    fun `readMessage should return null on empty stream`() {
        val input = ByteArrayInputStream(ByteArray(0))
        val reader = MessageReader(input)

        val result = reader.readMessage()

        assertNull(result)
    }

    @Test
    fun `readMessage should throw on missing Content-Length`() {
        val message = "Invalid-Header: value\r\n\r\n{}"
        val input = ByteArrayInputStream(message.toByteArray(StandardCharsets.UTF_8))

        val reader = MessageReader(input)

        assertThrows<IllegalArgumentException> { reader.readMessage() }
    }

    @Test
    fun `readMessage should handle LF-only line endings`() {
        val json = """{"id":42}"""
        val message = "Content-Length: ${json.length}\n\n$json"
        val input = ByteArrayInputStream(message.toByteArray(StandardCharsets.UTF_8))

        val reader = MessageReader(input)
        val result = reader.readMessage()

        assertNotNull(result)
        assertEquals(42, result!!["id"].asInt)
    }

    @Test
    fun `readMessage should handle unicode content`() {
        val json = """{"message":"你好世界"}"""
        val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)
        val message = "Content-Length: ${jsonBytes.size}\r\n\r\n$json"
        val input = ByteArrayInputStream(message.toByteArray(StandardCharsets.UTF_8))

        val reader = MessageReader(input)
        val result = reader.readMessage()

        assertNotNull(result)
        assertEquals("你好世界", result!!["message"].asString)
    }
}
