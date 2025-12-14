package com.frenchef.intellijlsp.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** T043: MessageWriter 单元测试 */
class MessageWriterTest {

    @Test
    fun `writeMessage should format message with Content-Length header`() {
        val output = ByteArrayOutputStream()
        val writer = MessageWriter(output)

        val message = mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "test")
        writer.writeMessage(message)

        val result = output.toString(StandardCharsets.UTF_8)

        assertTrue(result.startsWith("Content-Length:"))
        assertTrue(result.contains("\r\n\r\n"))
        assertTrue(result.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(result.contains("\"method\":\"test\""))
    }

    @Test
    fun `writeMessage should calculate correct Content-Length`() {
        val output = ByteArrayOutputStream()
        val writer = MessageWriter(output)

        val message = mapOf("key" to "value")
        writer.writeMessage(message)

        val result = output.toString(StandardCharsets.UTF_8)
        val headerEnd = result.indexOf("\r\n\r\n")
        val header = result.substring(0, headerEnd)
        val content = result.substring(headerEnd + 4)

        // 解析 Content-Length
        val lengthMatch = Regex("Content-Length: (\\d+)").find(header)
        assertNotNull(lengthMatch)
        val declaredLength = lengthMatch!!.groupValues[1].toInt()
        val actualLength = content.toByteArray(StandardCharsets.UTF_8).size

        assertEquals(declaredLength, actualLength)
    }

    @Test
    fun `writeMessage should handle unicode content`() {
        val output = ByteArrayOutputStream()
        val writer = MessageWriter(output)

        val message = mapOf("text" to "你好世界")
        writer.writeMessage(message)

        val result = output.toString(StandardCharsets.UTF_8)

        assertTrue(result.contains("你好世界"))

        // 验证 Content-Length 是字节长度而非字符长度
        val headerEnd = result.indexOf("\r\n\r\n")
        val header = result.substring(0, headerEnd)
        val content = result.substring(headerEnd + 4)

        val lengthMatch = Regex("Content-Length: (\\d+)").find(header)
        val declaredLength = lengthMatch!!.groupValues[1].toInt()
        val actualLength = content.toByteArray(StandardCharsets.UTF_8).size

        assertEquals(declaredLength, actualLength)
    }

    @Test
    fun `writeMessage should serialize null values`() {
        val output = ByteArrayOutputStream()
        val writer = MessageWriter(output)

        val message = mapOf("result" to null)
        writer.writeMessage(message)

        val result = output.toString(StandardCharsets.UTF_8)

        assertTrue(result.contains("\"result\":null"))
    }

    @Test
    fun `writeMessage should handle nested objects`() {
        val output = ByteArrayOutputStream()
        val writer = MessageWriter(output)

        val message = mapOf("result" to mapOf("capabilities" to mapOf("hoverProvider" to true)))
        writer.writeMessage(message)

        val result = output.toString(StandardCharsets.UTF_8)

        assertTrue(result.contains("capabilities"))
        assertTrue(result.contains("hoverProvider"))
    }
}
