package com.frenchef.intellijlsp.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * LSP 日志管理器
 * 
 * 提供以下功能：
 * 1. 将日志写入文件（方便外部查看）
 * 2. 保持内存中的日志队列（用于 Tool Window）
 * 3. 支持不同级别的日志
 */
object LspLogger {
    private val log = logger<LspLogger>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    // 日志队列（用于 Tool Window）
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private const val MAX_QUEUE_SIZE = 1000

    // 日志文件
    private var logFile: File? = null
    private var logWriter: PrintWriter? = null

    // 日志监听器
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    data class LogEntry(
        val timestamp: Date,
        val level: LogLevel,
        val category: String,
        val message: String
    ) {
        fun format(): String {
            return "[${dateFormat.format(timestamp)}] [$level] [$category] $message"
        }
    }

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * 初始化日志文件
     * @param project 项目实例（用于确定日志路径）
     */
    @Synchronized
    fun init(project: Project) {
        try {
            // 获取设置中的自定义路径
            val settings = com.frenchef.intellijlsp.config.LspSettings.getInstance()
            val customPath = settings.logFilePath

            // 确定日志目录
            val logDir = if (customPath.isNotBlank()) {
                File(customPath)
            } else {
                // 默认：用户主目录下
                File(System.getProperty("user.home"), ".intellij-lsp-logs")
            }

            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val projectName = project.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
            logFile = File(logDir, "lsp-${projectName}.log")

            // 追加模式打开
            logWriter = PrintWriter(FileWriter(logFile, true), true)

            info("LspLogger", "=".repeat(80))
            info("LspLogger", "LSP Logger initialized for project: ${project.name}")
            info("LspLogger", "Log file: ${logFile?.absolutePath}")
            info("LspLogger", "=".repeat(80))

            log.info("LSP log file created at: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            log.error("Failed to initialize LspLogger", e)
        }
    }

    /**
     * 关闭日志
     */
    @Synchronized
    fun close() {
        try {
            logWriter?.close()
            logWriter = null
            logFile = null
        } catch (e: Exception) {
            log.warn("Error closing LspLogger", e)
        }
    }

    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? = logFile?.absolutePath

    /**
     * 添加日志监听器（用于 Tool Window）
     */
    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    /**
     * 移除日志监听器
     */
    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * 获取最近的日志条目
     */
    fun getRecentLogs(): List<LogEntry> = logQueue.toList()

    /**
     * 清空日志队列
     */
    fun clearLogs() {
        logQueue.clear()
    }

    // 日志方法
    fun debug(category: String, message: String) = write(LogLevel.DEBUG, category, message)
    fun info(category: String, message: String) = write(LogLevel.INFO, category, message)
    fun warn(category: String, message: String) = write(LogLevel.WARN, category, message)
    fun error(category: String, message: String) = write(LogLevel.ERROR, category, message)

    private fun write(level: LogLevel, category: String, message: String) {
        val entry = LogEntry(Date(), level, category, message)

        // 添加到队列
        logQueue.add(entry)
        while (logQueue.size > MAX_QUEUE_SIZE) {
            logQueue.poll()
        }

        // 写入文件
        try {
            logWriter?.println(entry.format())
        } catch (e: Exception) {
            // 忽略写入错误
        }

        // 通知监听器
        listeners.forEach {
            try {
                it(entry)
            } catch (_: Exception) {
            }
        }

        // 同时写入 IDEA 日志
        when (level) {
            LogLevel.DEBUG -> log.debug("[LSP:$category] $message")
            LogLevel.INFO -> log.info("[LSP:$category] $message")
            LogLevel.WARN -> log.warn("[LSP:$category] $message")
            LogLevel.ERROR -> log.error("[LSP:$category] $message")
        }
    }
}
