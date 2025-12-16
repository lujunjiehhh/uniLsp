package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 反编译文件缓存管理器
 *
 * 支持的协议：
 * - jar:// - JAR 文件中的类 (如 jar://...util.jar!/com/example/Foo.class)
 * - jrt:// - JDK 模块中的类 (Java 9+, 如 jrt://jdk-path!/java.base/java/lang/String.class)
 *
 * 功能：
 * 1. 物化层：将 jar:// 或 jrt:// 中的反编译内容写入 .lsp-cache/ 目录
 * 2. 双向映射：file:// ↔ jar://|jrt:// URI 映射表
 * 3. 透明转换：所有 LSP Handler 无感知使用
 */
object DecompileCache {
    private const val TAG = "DecompileCache"

    // 双向映射表 (线程安全)
    private val cacheToJar = ConcurrentHashMap<String, String>()  // file:// → jar://
    private val jarToCache = ConcurrentHashMap<String, String>()  // jar:// → file://

    // 缓存目录名
    private const val CACHE_DIR_NAME = ".lsp-cache"

    /**
     * 将 JAR/JRT 中的类文件物化为本地文件（如果尚未缓存）
     *
     * @param sourceUrl 源文件的 URL，支持：
     *                  - jar://...util.jar!/com/.../Project.class
     *                  - jrt://jdk-path!/java.base/java/lang/String.class
     * @param project IntelliJ Project 上下文
     * @return 缓存文件的 file:// URI，或在失败时返回 null
     */
    fun materializeIfNeeded(jarUrl: String, project: Project): String? {
        // 快速路径：已缓存
        jarToCache[jarUrl]?.let { return it }

        synchronized(this) {
            // 双重检查
            jarToCache[jarUrl]?.let { return it }

            try {
                // 1. 获取 JAR 中的 VirtualFile
                val jarVf = VirtualFileManager.getInstance().findFileByUrl(jarUrl)
                if (jarVf == null) {
                    LspLogger.warn(TAG, "无法找到 JAR VirtualFile: $jarUrl")
                    return null
                }

                // 2. 获取反编译内容 (IntelliJ 自动处理)
                // 注意：不使用 ReadAction.compute，因为调用者通常已在读锁内
                // 如果不在读锁内调用，需要调用者确保在读锁内
                val psiFile = PsiManager.getInstance(project).findFile(jarVf)
                val content = psiFile?.text

                if (content.isNullOrEmpty()) {
                    LspLogger.warn(TAG, "无法获取反编译内容: $jarUrl")
                    return null
                }

                // 3. 计算缓存文件路径
                val cachePath = computeCachePath(jarUrl, project)
                if (cachePath == null) {
                    LspLogger.warn(TAG, "无法计算缓存路径: $jarUrl")
                    return null
                }

                // 4. 写入缓存文件
                Files.createDirectories(cachePath.parent)
                Files.writeString(cachePath, content)
                LspLogger.info(TAG, "已物化反编译内容: $jarUrl -> $cachePath")

                // 5. 通知 VFS 异步刷新（不会阻塞，避免在读锁内触发死锁）
                // 后续访问缓存文件时，LocalFileSystem 会自动发现已写入的文件
                com.intellij.openapi.application.invokeLater {
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .refreshAndFindFileByNioFile(cachePath)
                }

                // 6. 记录双向映射（使用规范化 URI）
                val fileUri = normalizeUri(cachePath.toUri().toString())
                cacheToJar[fileUri] = jarUrl
                jarToCache[jarUrl] = fileUri
                LspLogger.info(TAG, "已记录映射: $fileUri -> $jarUrl")

                return fileUri

            } catch (e: Exception) {
                LspLogger.error(TAG, "物化反编译内容失败: $jarUrl - ${e.message}")
                return null
            }
        }
    }

    /**
     * 根据缓存文件 URI 获取原始 JAR 的 VirtualFile
     *
     * @param cacheUri 缓存文件的 file:// URI
     * @return 原始 JAR 中的 VirtualFile，或 null
     */
    fun getOriginalVirtualFile(cacheUri: String): VirtualFile? {
        val normalizedUri = normalizeUri(cacheUri)
        val jarUrl = cacheToJar[normalizedUri]
        if (jarUrl == null) {
            LspLogger.debug(TAG, "缓存 URI 未找到映射: $cacheUri (normalized: $normalizedUri)")
            LspLogger.debug(TAG, "当前映射表: ${cacheToJar.keys.take(5)}")
            return null
        }
        return VirtualFileManager.getInstance().findFileByUrl(jarUrl)
    }

    /**
     * 获取原始 JAR URL
     *
     * @param cacheUri 缓存文件的 file:// URI
     * @return 原始 jar:// URL，或 null
     */
    fun getOriginalUrl(cacheUri: String): String? {
        return cacheToJar[normalizeUri(cacheUri)]
    }

    /**
     * 检查 URI 是否是缓存文件
     */
    fun isCacheUri(uri: String): Boolean {
        return cacheToJar.containsKey(normalizeUri(uri))
    }

    /**
     * 规范化 URI 用于一致性比较
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

        // Windows 盘符规范化：file:///F: -> file:///f:
        if (normalized.startsWith("file:///") && normalized.length > 9) {
            val driveLetter = normalized[8]
            if (driveLetter.isUpperCase()) {
                normalized = normalized.substring(0, 8) + driveLetter.lowercaseChar() + normalized.substring(9)
            }
        }

        return normalized
    }

    /**
     * 检查 URL 是否是可反编译的类文件（JAR 或 JRT）
     */
    fun isDecompilableUrl(url: String): Boolean {
        return url.startsWith("jar://") || url.startsWith("jrt://") || url.contains(".jar!")
    }

    /**
     * 检查 URL 是否是 JAR 内的类文件 (兼容旧方法名)
     */
    fun isJarUrl(url: String): Boolean = isDecompilableUrl(url)

    /**
     * 计算缓存文件路径
     *
     * 支持格式:
     * - jar://.../foo.jar!/com/example/Foo.class
     * - jrt://jdk-path!/java.base/java/lang/String.class
     *
     * 输出: <project>/.lsp-cache/<source-hash>/<package>/<ClassName>.java
     */
    private fun computeCachePath(sourceUrl: String, project: Project): Path? {
        val projectBase = project.basePath ?: return null

        val (sourcePath, classPath) = when {
            sourceUrl.startsWith("jrt://") -> {
                // JRT URL: jrt://jdk-path!/module/package/Class.class
                val parts = sourceUrl.split("!/")
                if (parts.size != 2) return null
                val jdkPath = parts[0].removePrefix("jrt://")
                val moduleAndClass = parts[1]
                // 移除模块名前缀（如 java.base/）得到类路径
                val classPathPart = if (moduleAndClass.contains("/")) {
                    moduleAndClass.substringAfter("/")
                } else {
                    moduleAndClass
                }
                Pair(jdkPath, classPathPart)
            }

            sourceUrl.startsWith("jar://") || sourceUrl.contains(".jar!") -> {
                // JAR URL: jar://.../foo.jar!/com/example/Foo.class
                val parts = sourceUrl.split("!/")
                if (parts.size != 2) return null
                val jarPath = parts[0].removePrefix("jar://")
                Pair(jarPath, parts[1])
            }

            else -> return null
        }

        // 使用源路径的哈希作为目录名（避免路径过长）
        val sourceHash = md5Short(sourcePath)

        // 转换类路径：com/example/Foo.class → com/example/Foo.java
        val javaPath = classPath
            .removeSuffix(".class")
            .plus(".java")

        return Path.of(projectBase, CACHE_DIR_NAME, sourceHash, javaPath)
    }

    /**
     * 计算短 MD5 哈希
     */
    private fun md5Short(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /**
     * 清理项目的反编译缓存
     */
    fun clearCache(project: Project) {
        val projectBase = project.basePath ?: return
        val cacheDir = Path.of(projectBase, CACHE_DIR_NAME)

        try {
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
                LspLogger.info(TAG, "已清理反编译缓存: $cacheDir")
            }

            // 清理映射表中该项目的条目
            val prefix = cacheDir.toUri().toString()
            cacheToJar.keys.filter { it.startsWith(prefix) }.forEach {
                val jarUrl = cacheToJar.remove(it)
                jarUrl?.let { jarToCache.remove(it) }
            }

        } catch (e: Exception) {
            LspLogger.warn(TAG, "清理缓存失败: $cacheDir - ${e.message}")
        }
    }
}
