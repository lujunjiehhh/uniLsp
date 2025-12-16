package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.net.URI

/**
 * LSP 反编译文件 URI 解析器
 * 
 * 核心逻辑：
 * - 对外（客户端）：永远给 file://（缓存物化文件）
 * - 对内（分析）：用 jar://（原始 VirtualFile/PsiFile）做解析与全项目引用搜索
 */
object LspDecompiledUriResolver {
    private const val TAG = "UriResolver"

    /**
     * 解析结果
     * @param clientUri 客户端看到的 URI（file:// 缓存文件）
     * @param analysisPsiFile 用于语义分析的 PsiFile（jar 反编译 PSI，有索引能力）
     * @param fileText 用于 offset 计算的文本（缓存文件文本，和客户端一致）
     */
    data class Resolved(
        val clientUri: String,
        val analysisPsiFile: PsiFile,
        val fileText: String
    )

    /**
     * 用于处理"请求进来"的 URI：
     * - 如果是 .lsp-cache 的 file://：映射回 jar:// 的 PsiFile 来做语义分析；文本用缓存文件的
     * - 如果直接是 jar://：先物化成 file://，再同上
     * - 普通 file://：直接返回普通 PsiFile
     */
    fun resolveForRequest(project: Project, uri: String): Resolved? {
        val normalizedUri = normalizeUri(uri)
        LspLogger.info(TAG, "解析请求 URI: $normalizedUri")

        // Case 1: 直接是 jar:// 或 jrt:// URL → 先物化，再按缓存文件处理
        if (DecompileCache.isDecompilableUrl(normalizedUri)) {
            LspLogger.info(TAG, "检测到可反编译 URL (jar/jrt)，先物化")
            val cacheUri = DecompileCache.materializeIfNeeded(normalizedUri, project) ?: return null
            return resolveForRequest(project, cacheUri)
        }

        // Case 2: 是缓存文件 → 用原始 jar PSI 做分析，用缓存文件文本算 offset
        if (DecompileCache.isCacheUri(normalizedUri)) {
            LspLogger.info(TAG, "检测到缓存 URI，反查原始 jar PSI")

            // 获取缓存文件的物理 VirtualFile 和文本
            val cacheVf = findPhysicalFile(normalizedUri)
            val cacheText = if (cacheVf != null) {
                ReadAction.compute<String, RuntimeException> {
                    FileDocumentManager.getInstance().getDocument(cacheVf)?.text
                        ?: cacheVf.inputStream.bufferedReader().readText()
                }
            } else {
                LspLogger.warn(TAG, "无法找到缓存物理文件: $normalizedUri")
                return null
            }

            // 获取原始 jar VirtualFile 和 PsiFile（用于语义分析）
            val originalVf = DecompileCache.getOriginalVirtualFile(normalizedUri)
            if (originalVf == null) {
                LspLogger.warn(TAG, "无法反查原始 jar VF: $normalizedUri")
                return null
            }

            val analysisPsi = ReadAction.compute<PsiFile?, RuntimeException> {
                PsiManager.getInstance(project).findFile(originalVf)
            }
            if (analysisPsi == null) {
                LspLogger.warn(TAG, "无法获取原始 jar PsiFile: ${originalVf.url}")
                return null
            }

            LspLogger.info(TAG, "成功解析缓存 URI: $normalizedUri -> ${originalVf.url}")
            return Resolved(
                clientUri = normalizedUri,
                analysisPsiFile = analysisPsi,
                fileText = cacheText
            )
        }

        // Case 3: 普通文件 → 直接返回
        val vf = findPhysicalFile(normalizedUri) ?: return null
        val psi = ReadAction.compute<PsiFile?, RuntimeException> {
            PsiManager.getInstance(project).findFile(vf)
        } ?: return null

        val text = ReadAction.compute<String, RuntimeException> { psi.text ?: "" }

        return Resolved(
            clientUri = normalizedUri,
            analysisPsiFile = psi,
            fileText = text
        )
    }

    /**
     * 用于"响应出去"的跳转 URI：把 jar:// 目标变成可打开的 file://（.lsp-cache）
     */
    fun toClientUri(project: Project, targetFile: VirtualFile): String? {
        val url = targetFile.url
        return if (DecompileCache.isJarUrl(url)) {
            val cacheUri = DecompileCache.materializeIfNeeded(url, project)
            LspLogger.info(TAG, "出站映射: $url -> $cacheUri")
            cacheUri
        } else {
            // 普通文件，使用标准 file:// 格式
            try {
                java.nio.file.Path.of(targetFile.path).toUri().toString()
            } catch (e: Exception) {
                url
            }
        }
    }

    /**
     * 查找物理文件的 VirtualFile
     */
    private fun findPhysicalFile(uri: String): VirtualFile? {
        return try {
            val rawPath = URI(uri).path ?: return null
            val localPath = if (rawPath.length >= 3 && rawPath[0] == '/' && rawPath[2] == ':') {
                rawPath.substring(1)
            } else {
                rawPath
            }
            LocalFileSystem.getInstance().findFileByPath(localPath)
        } catch (e: Exception) {
            LspLogger.warn(TAG, "无法解析 URI: $uri - ${e.message}")
            null
        }
    }

    /**
     * 规范化 URI
     * - URL 解码（%3A -> :）
     * - 统一使用小写盘符
     * - 统一使用正斜杠
     * - 移除末尾斜杠
     */
    private fun normalizeUri(uri: String): String {
        // 首先进行 URL 解码，处理 %3A 等编码字符
        var normalized = try {
            java.net.URLDecoder.decode(uri, "UTF-8")
        } catch (e: Exception) {
            uri
        }

        normalized = normalized
            .replace("\\", "/")
            .trimEnd('/')

        if (normalized.startsWith("file:///") && normalized.length > 9) {
            val driveLetter = normalized[8]
            if (driveLetter.isUpperCase()) {
                normalized = normalized.substring(0, 8) + driveLetter.lowercaseChar() + normalized.substring(9)
            }
        }

        return normalized
    }
}
