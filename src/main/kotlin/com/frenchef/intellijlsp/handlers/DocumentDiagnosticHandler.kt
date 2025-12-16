package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.BackgroundAnalyzer
import com.frenchef.intellijlsp.intellij.DiagnosticsProvider
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.InspectionDiagnosticsProvider
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.Diagnostic
import com.frenchef.intellijlsp.protocol.models.DocumentDiagnosticParams
import com.frenchef.intellijlsp.protocol.models.FullDocumentDiagnosticReport
import com.frenchef.intellijlsp.protocol.models.UnchangedDocumentDiagnosticReport
import com.frenchef.intellijlsp.util.LspLogger
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 处理 textDocument/diagnostic 请求 (LSP 3.17 Pull 模式诊断)
 * 
 * 这是客户端主动请求诊断的端点，与 publishDiagnostics (Push 模式) 不同。
 * 适用于：
 * 1. 客户端首次打开文件需要立即获取诊断
 * 2. Agent 在后台编辑文件后，客户端主动拉取诊断
 * 3. 需要同步获取诊断而不是等待服务端推送
 */
class DocumentDiagnosticHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<DocumentDiagnosticHandler>()
    private val gson = LspGson.instance

    // 后台分析器
    private val backgroundAnalyzer = BackgroundAnalyzer(project)

    // 诊断提供器
    private val diagnosticsProvider = DiagnosticsProvider(project)
    private val inspectionProvider = InspectionDiagnosticsProvider(project)

    // 缓存 resultId 用于增量更新
    private val resultIdCache = ConcurrentHashMap<String, String>()
    private val diagnosticsCache = ConcurrentHashMap<String, List<Diagnostic>>()

    companion object {
        private const val TAG = "DocumentDiagnostic"
    }

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/diagnostic", this::handleDocumentDiagnostic)
        log.info("DocumentDiagnosticHandler registered for textDocument/diagnostic")
    }

    /**
     * 处理 textDocument/diagnostic 请求
     */
    private fun handleDocumentDiagnostic(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.warn("textDocument/diagnostic called with null params")
            return null
        }

        return try {
            val request = gson.fromJson(params, DocumentDiagnosticParams::class.java)
            val uri = request.textDocument.uri

            LspLogger.info(TAG, "收到诊断请求: $uri")

            // 检查是否可以返回 unchanged 报告
            val previousResultId = request.previousResultId
            if (previousResultId != null) {
                val cachedResultId = resultIdCache[uri]
                if (cachedResultId == previousResultId) {
                    // 诊断没有变化，返回 unchanged 报告
                    LspLogger.debug(TAG, "诊断未变化，返回 unchanged: $uri")
                    return gson.toJsonTree(UnchangedDocumentDiagnosticReport(resultId = cachedResultId))
                }
            }

            // 获取诊断
            val diagnostics = getDiagnosticsForFile(uri)

            // 生成新的 resultId
            val newResultId = "${System.currentTimeMillis()}-${diagnostics.hashCode()}"
            resultIdCache[uri] = newResultId
            diagnosticsCache[uri] = diagnostics

            LspLogger.info(TAG, "返回 ${diagnostics.size} 个诊断: $uri")

            // 返回完整报告
            val report = FullDocumentDiagnosticReport(
                resultId = newResultId,
                items = diagnostics
            )

            gson.toJsonTree(report)
        } catch (e: Exception) {
            log.error("Error handling textDocument/diagnostic", e)
            null
        }
    }

    /**
     * 获取文件的诊断信息
     * 
     * 策略：
     * 1. 确保文件在 IntelliJ 编辑器中打开以触发 DaemonCodeAnalyzer
     * 2. 尝试从 MarkupModel 获取诊断（需要文件在编辑器中打开后有分析结果）
     * 3. 如果 MarkupModel 没有结果，使用 Inspection API
     */
    /**
     * 获取文件的诊断信息
     * 
     * 策略：
     * 1. 确保文件在 IntelliJ 编辑器中打开以触发 DaemonCodeAnalyzer
     * 2. 轮询检测 MarkupModel 中的诊断（最多等待 1 秒）
     * 3. 如果 MarkupModel 没有结果，使用 Inspection API 作为 fallback
     */
    private fun getDiagnosticsForFile(uri: String): List<Diagnostic> {
        val virtualFile = documentManager.getVirtualFile(uri)
        val document = documentManager.getIntellijDocument(uri)

        if (virtualFile == null || document == null) {
            LspLogger.warn(TAG, "无法获取文件或文档: $uri")
            return emptyList()
        }

        if (!virtualFile.isValid) return emptyList()

        // Step 1: 确保文件在 IntelliJ 编辑器中打开
        backgroundAnalyzer.ensureFileOpenForAnalysis(virtualFile, uri)

        // Step 2 & 3: 智能轮询 MarkupModel
        var diagnostics = emptyList<Diagnostic>()
        var attempts = 0
        val maxAttempts = 10 // 10 * 100ms = 1s max

        while (attempts < maxAttempts) {
            attempts++
            Thread.sleep(100)

            if (!virtualFile.isValid) return emptyList()

            val currentDiagnostics = diagnosticsProvider.getDiagnosticsByUri(uri, virtualFile, document)
            if (currentDiagnostics.isNotEmpty()) {
                // 发现诊断！立即返回
                LspLogger.debug(TAG, "MarkupModel 快速返回 ${currentDiagnostics.size} 个诊断 (尝试 $attempts): $uri")
                diagnostics = currentDiagnostics
                break
            }
        }

        if (diagnostics.isNotEmpty()) {
            return diagnostics
        }

        if (!virtualFile.isValid) return emptyList()

        // 如果轮询结束后仍然为空（可能是真的没有错误，或者是分析还没完成但我们必须响应了）
        // 再试一次 MarkupModel
        diagnostics = diagnosticsProvider.getDiagnosticsByUri(uri, virtualFile, document)
        if (diagnostics.isNotEmpty()) {
            LspLogger.debug(TAG, "MarkupModel 最终返回 ${diagnostics.size} 个诊断: $uri")
            return diagnostics
        }

        // Step 4: 如果 MarkupModel 仍然没有诊断，尝试 Inspection API
        // 注意：如果文件真的没有错误，这里回退也是安全的，因为 Inspection API 对无错误文件也会返回空列表
        LspLogger.debug(TAG, "尝试 Inspection API: $uri")
        try {
            diagnostics = ReadAction.compute<List<Diagnostic>, RuntimeException> {
                if (!virtualFile.isValid) return@compute emptyList()
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null) {
                    inspectionProvider.getDiagnostics(psiFile, document)
                } else {
                    emptyList()
                }
            }
            LspLogger.debug(TAG, "Inspection API 返回 ${diagnostics.size} 个诊断: $uri")
        } catch (e: Exception) {
            LspLogger.warn(TAG, "Inspection API 异常: ${e.message}")
        }

        return diagnostics
    }

    /**
     * 清理资源
     */
    fun dispose() {
        resultIdCache.clear()
        diagnosticsCache.clear()
        backgroundAnalyzer.dispose()
    }
}
