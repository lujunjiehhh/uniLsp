package com.frenchef.intellijlsp.protocol

import com.frenchef.intellijlsp.protocol.models.*
import com.google.gson.*
import java.lang.reflect.Type

/**
 * 统一的 Gson 实例，配置了 LSP 协议所需的枚举序列化器。
 * LSP 协议要求枚举类型序列化为其数值，而不是字符串名称。
 */
object LspGson {
    val instance: Gson by lazy {
        GsonBuilder()
            // 枚举序列化为 value 字段（LSP 规范要求）
            .registerTypeAdapter(SymbolKind::class.java, EnumValueSerializer<SymbolKind>())
            .registerTypeAdapter(SymbolKind::class.java, SymbolKindDeserializer())
            .registerTypeAdapter(CompletionItemKind::class.java, EnumValueSerializer<CompletionItemKind>())
            .registerTypeAdapter(CompletionItemKind::class.java, CompletionItemKindDeserializer())
            .registerTypeAdapter(DiagnosticSeverity::class.java, EnumValueSerializer<DiagnosticSeverity>())
            .registerTypeAdapter(DiagnosticSeverity::class.java, DiagnosticSeverityDeserializer())
            .registerTypeAdapter(TextDocumentSyncKind::class.java, EnumValueSerializer<TextDocumentSyncKind>())
            .registerTypeAdapter(InsertTextFormat::class.java, EnumValueSerializer<InsertTextFormat>())
            .registerTypeAdapter(CompletionTriggerKind::class.java, EnumValueSerializer<CompletionTriggerKind>())
            .registerTypeAdapter(MarkupKind::class.java, MarkupKindSerializer())
            .registerTypeAdapter(MarkupKind::class.java, MarkupKindDeserializer())
            .create()
    }
}

/**
 * 通用枚举序列化器 - 将枚举序列化为其 value 字段
 */
private class EnumValueSerializer<T : Enum<*>> : JsonSerializer<T> {
    override fun serialize(src: T?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        // 尝试获取 value 字段
        return try {
            val valueField = src.javaClass.getDeclaredField("value")
            valueField.isAccessible = true
            when (val value = valueField.get(src)) {
                is Int -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                else -> JsonPrimitive(src.name)
            }
        } catch (e: Exception) {
            JsonPrimitive(src.name)
        }
    }
}

/**
 * SymbolKind 反序列化器
 */
private class SymbolKindDeserializer : JsonDeserializer<SymbolKind> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): SymbolKind? {
        if (json == null || json.isJsonNull) return null
        val value = json.asInt
        return SymbolKind.entries.find { it.value == value }
    }
}

/**
 * CompletionItemKind 反序列化器
 */
private class CompletionItemKindDeserializer : JsonDeserializer<CompletionItemKind> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): CompletionItemKind? {
        if (json == null || json.isJsonNull) return null
        val value = json.asInt
        return CompletionItemKind.entries.find { it.value == value }
    }
}

/**
 * DiagnosticSeverity 反序列化器
 */
private class DiagnosticSeverityDeserializer : JsonDeserializer<DiagnosticSeverity> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DiagnosticSeverity? {
        if (json == null || json.isJsonNull) return null
        val value = json.asInt
        return DiagnosticSeverity.entries.find { it.value == value }
    }
}

/**
 * MarkupKind 序列化器（value 是 String）
 */
private class MarkupKindSerializer : JsonSerializer<MarkupKind> {
    override fun serialize(src: MarkupKind?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        return JsonPrimitive(src.value)
    }
}

/**
 * MarkupKind 反序列化器
 */
private class MarkupKindDeserializer : JsonDeserializer<MarkupKind> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): MarkupKind? {
        if (json == null || json.isJsonNull) return null
        val value = json.asString
        return MarkupKind.entries.find { it.value == value }
    }
}
