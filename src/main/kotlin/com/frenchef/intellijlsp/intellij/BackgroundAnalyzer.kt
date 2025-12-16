package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 后台代码分析器
 * 
 * 用于触发 DaemonCodeAnalyzer 对文件进行分析，即使文件没有在 IDEA 编辑器中打开。
 * 
 * 核心策略：
 * 1. 如果文件已在编辑器中打开，直接调用 DaemonCodeAnalyzer.restart()
 * 2. 如果文件未在编辑器中打开，在后台静默打开文件，触发分析后关闭
 * 
 * 注意：此类需要在 EDT (Event Dispatch Thread) 中操作 FileEditorManager。
 */
class BackgroundAnalyzer(private val project: Project) {
    private val log = logger<BackgroundAnalyzer>()

    // 跟踪正在分析的文件
    private val analyzingFiles = ConcurrentHashMap<String, AnalysisState>()

    private data class AnalysisState(
        val inProgress: AtomicBoolean = AtomicBoolean(true),
        val wasOpenedByUs: AtomicBoolean = AtomicBoolean(false)
    )

    companion object {
        private const val TAG = "BackgroundAnalyzer"

        // 分析超时时间（毫秒）
        private const val ANALYSIS_TIMEOUT_MS = 5000L

        // 分析完成后延迟关闭编辑器的时间（毫秒）
        private const val CLOSE_DELAY_MS = 2000L
    }

    /**
     * 确保文件在 IntelliJ 编辑器中打开，以触发 DaemonCodeAnalyzer。
     * 
     * @param virtualFile 要分析的文件
     * @param uri 文件 URI（用于日志和跟踪）
     * @return 如果文件已准备好进行分析，返回 true
     */
    fun ensureFileOpenForAnalysis(virtualFile: VirtualFile, uri: String): Boolean {
        // 检查文件是否已在编辑器中打开
        val isAlreadyOpen = ApplicationManager.getApplication().runReadAction<Boolean> {
            FileEditorManager.getInstance(project).isFileOpen(virtualFile)
        }

        if (isAlreadyOpen) {
            LspLogger.debug(TAG, "文件已在编辑器中打开: $uri")
            triggerAnalysis(virtualFile, uri)
            return true
        }

        // 文件未打开，需要在后台打开
        LspLogger.info(TAG, "在后台打开文件以触发分析: $uri")

        val state = analyzingFiles.getOrPut(uri) { AnalysisState() }

        try {
            // 在 EDT 中打开文件
            ApplicationManager.getApplication().invokeLater {
                try {
                    if (project.isDisposed) return@invokeLater

                    // 在后台打开文件（不获取焦点）
                    OpenFileDescriptor(project, virtualFile)
                    val editors = FileEditorManager.getInstance(project).openFile(
                        virtualFile,
                        false,  // 不请求焦点
                        true    // 搜索已存在的编辑器
                    )

                    if (editors.isNotEmpty()) {
                        state.wasOpenedByUs.set(true)
                        LspLogger.info(TAG, "成功在后台打开文件: $uri")

                        // 触发分析
                        triggerAnalysis(virtualFile, uri)

                        // 延迟关闭编辑器（如果是我们打开的）
                        scheduleEditorClose(virtualFile, uri, state)
                    } else {
                        LspLogger.warn(TAG, "无法打开文件编辑器: $uri")
                    }

                } catch (e: Exception) {
                    LspLogger.error(TAG, "打开文件时出错: $uri - ${e.message}")
                } finally {
                    state.inProgress.set(false)
                }
            }

            return true
        } catch (e: Exception) {
            log.warn("Error ensuring file open for analysis: $uri", e)
            return false
        }
    }

    /**
     * 触发对文件的代码分析
     */
    private fun triggerAnalysis(virtualFile: VirtualFile, uri: String) {
        ApplicationManager.getApplication().invokeLater {
            try {
                if (project.isDisposed) return@invokeLater

                ReadAction.run<RuntimeException> {
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                    if (psiFile != null) {
                        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                        LspLogger.debug(TAG, "已触发 DaemonCodeAnalyzer.restart(): $uri")
                    }
                }
            } catch (e: Exception) {
                LspLogger.error(TAG, "触发分析时出错: $uri - ${e.message}")
            }
        }
    }

    /**
     * 计划关闭编辑器（如果是我们打开的）
     */
    private fun scheduleEditorClose(virtualFile: VirtualFile, uri: String, state: AnalysisState) {
        // 使用协程延迟关闭
        CoroutineScope(Dispatchers.Default).launch {
            delay(CLOSE_DELAY_MS)

            // 检查是否应该关闭
            if (state.wasOpenedByUs.get()) {
                ApplicationManager.getApplication().invokeLater {
                    try {
                        if (project.isDisposed) return@invokeLater

                        // 检查文件是否仍然打开且只有我们打开的编辑器
                        val editorManager = FileEditorManager.getInstance(project)
                        if (editorManager.isFileOpen(virtualFile)) {
                            // 注意：这里不关闭文件，因为关闭会导致 MarkupModel 被清除
                            // 我们保持文件打开以便后续的诊断请求可以访问高亮信息
                            LspLogger.debug(TAG, "保持文件打开以保留分析结果: $uri")
                        }
                    } catch (e: Exception) {
                        LspLogger.debug(TAG, "检查编辑器状态时出错: ${e.message}")
                    } finally {
                        analyzingFiles.remove(uri)
                    }
                }
            }
        }
    }

    /**
     * 等待文件分析完成
     * 
     * @param uri 文件 URI
     * @param timeoutMs 超时时间（毫秒）
     * @return 如果分析在超时前完成，返回 true
     */
    fun waitForAnalysis(uri: String, timeoutMs: Long = ANALYSIS_TIMEOUT_MS): Boolean {
        val state = analyzingFiles[uri] ?: return true // 没有正在进行的分析

        val startTime = System.currentTimeMillis()
        while (state.inProgress.get()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                LspLogger.warn(TAG, "等待分析超时: $uri")
                return false
            }
            Thread.sleep(100)
        }
        return true
    }

    /**
     * 获取文件的高亮信息
     */
    fun getHighlights(
        virtualFile: VirtualFile,
        startOffset: Int = 0,
        endOffset: Int = Int.MAX_VALUE
    ): List<HighlightInfo> {
        return ReadAction.compute<List<HighlightInfo>, RuntimeException> {
            try {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@compute emptyList()
                val actualEndOffset = endOffset.coerceAtMost(document.textLength)

                val highlights = mutableListOf<HighlightInfo>()
                DaemonCodeAnalyzerEx.processHighlights(
                    document,
                    project,
                    null,
                    startOffset,
                    actualEndOffset
                ) { info ->
                    highlights.add(info)
                    true
                }

                LspLogger.debug(TAG, "获取到 ${highlights.size} 个高亮")
                highlights
            } catch (e: Exception) {
                log.debug("Error getting highlights: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 清理资源
     */
    fun dispose() {
        analyzingFiles.clear()
    }
}
