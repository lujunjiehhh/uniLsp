package com.frenchef.intellijlsp.util

import com.frenchef.intellijlsp.util.LspUriUtil.toUri
import java.net.URI
import java.net.URLDecoder

/**
 * URI utilities for dealing with LSP file URIs.
 *
 * Motivation:
 * Some clients may send Windows drive letters percent-encoded (e.g. file:///f%3A/...).
 * IntelliJ/VFS helpers usually expect decoded file URIs (e.g. file:///f:/...).
 */
object LspUriUtil {

    /**
     * Normalize an incoming URI for internal comparisons / filesystem lookups.
     * - URL decode (e.g. %3A -> :)
     * - backslash -> slash
     * - trim trailing '/'
     * - lower-case Windows drive letter in file:///X:/...
     */
    fun normalize(uri: String): String {
        var normalized = try {
            URLDecoder.decode(uri, "UTF-8")
        } catch (_: Exception) {
            uri
        }

        normalized = normalized.replace("\\", "/").trimEnd('/')

        // Windows drive letter normalization: file:///F:/... -> file:///f:/...
        if (normalized.startsWith("file:///") && normalized.length > 9) {
            val driveLetter = normalized[8]
            if (driveLetter.isUpperCase()) {
                normalized = normalized.substring(0, 8) + driveLetter.lowercaseChar() + normalized.substring(9)
            }
        }

        return normalized
    }

    /**
     * Convert a file:// URI into a local filesystem path.
     *
     * Returns null if the URI cannot be parsed into a path.
     */
    fun toLocalPath(fileUri: String): String? {
        val normalized = normalize(fileUri)
        return try {
            val rawPath = URI(normalized).path ?: return null
            // On Windows, java.net.URI returns "/C:/..." for file URIs.
            if (rawPath.length >= 3 && rawPath[0] == '/' && rawPath[2] == ':') rawPath.substring(1) else rawPath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Convert a VirtualFile to a normalized file:// URI.
     */
    fun toUri(file: com.intellij.openapi.vfs.VirtualFile): String {
        return toUri(file.path)
    }

    /**
     * Convert a local filesystem path to a normalized file:// URI.
     */
    fun toUri(path: String): String {
        var normalizedPath = path.replace("\\", "/")

        // Ensure path starts with /
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/$normalizedPath"
        }

        return normalize("file://$normalizedPath")
    }
}
