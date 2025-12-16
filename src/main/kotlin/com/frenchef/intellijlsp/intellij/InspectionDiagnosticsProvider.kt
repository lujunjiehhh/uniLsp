package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.Diagnostic
import com.frenchef.intellijlsp.protocol.models.DiagnosticSeverity
import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * 基于 Inspection API 的诊断提供器。
 * 不依赖编辑器打开，可以对任意 PsiFile 运行检查。
 * 
 * 优点：
 * - 不需要文件在编辑器中打开
 * - 可以获取 ProblemDescriptor，包含 QuickFix 信息
 * 
 * 缺点：
 * - 性能较慢（需要运行所有检查）
 * - 可能与编辑器中的高亮不完全一致
 */
class InspectionDiagnosticsProvider(private val project: Project) {
    private val log = logger<InspectionDiagnosticsProvider>()

    /**
     * 缓存的检查结果，用于 CodeAction resolve 时获取 QuickFix
     */
    data class DiagnosticWithDescriptor(
        val diagnostic: Diagnostic,
        val descriptor: ProblemDescriptor,
        val inspection: LocalInspectionTool
    )

    /**
     * 获取诊断，同时保留 ProblemDescriptor 用于 QuickFix
     */
    fun getDiagnosticsWithDescriptors(psiFile: PsiFile, document: Document): List<DiagnosticWithDescriptor> {
        return ReadAction.compute<List<DiagnosticWithDescriptor>, RuntimeException> {
            try {
                val results = mutableListOf<DiagnosticWithDescriptor>()
                val seen = mutableSetOf<String>()

                val inspectionManager = InspectionManager.getInstance(project)
                val profile = InspectionProjectProfileManager.getInstance(project).currentProfile

                LspLogger.info("Inspection", "Using profile: ${profile.name} for file: ${psiFile.name}")

                // 获取所有启用的本地检查工具
                val enabledTools = getEnabledLocalInspections(profile, psiFile)

                LspLogger.info("Inspection", "Found ${enabledTools.size} enabled local inspections for ${psiFile.name}")

                for ((tool, wrapper) in enabledTools) {
                    try {
                        // 运行检查
                        val problems = tool.processFile(psiFile, inspectionManager)

                        if (problems.isNotEmpty()) {
                            LspLogger.debug("Inspection", "${tool.shortName} found ${problems.size} problems")
                        }

                        for (problem in problems) {
                            val diagnostic = problemDescriptorToDiagnostic(problem, document, wrapper)
                            if (diagnostic != null) {
                                val key = "${diagnostic.range.start.line}:${diagnostic.range.start.character}:" +
                                        "${diagnostic.range.end.line}:${diagnostic.range.end.character}:" +
                                        "${diagnostic.message}:${diagnostic.severity?.value}"
                                if (seen.add(key)) {
                                    results.add(DiagnosticWithDescriptor(diagnostic, problem, tool))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.debug("Error running inspection ${tool.shortName}: ${e.message}")
                    }
                }

                LspLogger.info(
                    "Inspection",
                    "Total: ${results.size} diagnostics from ${enabledTools.size} inspections for ${psiFile.name}"
                )
                results
            } catch (e: Exception) {
                log.warn("Error getting inspection diagnostics", e)
                emptyList()
            }
        }
    }

    /**
     * 获取诊断列表（不包含 descriptor，用于发布）
     */
    fun getDiagnostics(psiFile: PsiFile, document: Document): List<Diagnostic> {
        return getDiagnosticsWithDescriptors(psiFile, document).map { it.diagnostic }
    }

    /**
     * 获取文件的诊断
     */
    fun getDiagnosticsByUri(uri: String, virtualFile: VirtualFile, document: Document): List<Diagnostic> {
        return ReadAction.compute<List<Diagnostic>, RuntimeException> {
            try {
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null) {
                    getDiagnostics(psiFile, document)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                log.warn("Error getting diagnostics for $uri", e)
                emptyList()
            }
        }
    }

    /**
     * 获取启用的本地检查工具
     */
    private fun getEnabledLocalInspections(
        profile: InspectionProfileImpl,
        psiFile: PsiFile
    ): List<Pair<LocalInspectionTool, InspectionToolWrapper<*, *>>> {
        val result = mutableListOf<Pair<LocalInspectionTool, InspectionToolWrapper<*, *>>>()

        for (toolWrapper in profile.getInspectionTools(psiFile)) {
            // 使用 isToolEnabled 检查工具是否启用
            val key = com.intellij.codeInsight.daemon.HighlightDisplayKey.find(toolWrapper.shortName)
            if (key == null || !profile.isToolEnabled(key, psiFile)) continue

            val tool = toolWrapper.tool
            if (tool is LocalInspectionTool) {
                // 检查该检查是否适用于当前文件
                if (tool.isSuppressedFor(psiFile)) continue
                result.add(Pair(tool, toolWrapper))
            }
        }

        return result
    }

    /**
     * 将 ProblemDescriptor 转换为 LSP Diagnostic
     */
    private fun problemDescriptorToDiagnostic(
        descriptor: ProblemDescriptor,
        document: Document,
        wrapper: InspectionToolWrapper<*, *>
    ): Diagnostic? {
        try {
            val psiElement = descriptor.psiElement ?: return null
            val textRange = descriptor.textRangeInElement?.shiftRight(psiElement.textOffset)
                ?: psiElement.textRange
                ?: return null

            if (textRange.startOffset < 0 || textRange.endOffset > document.textLength) {
                return null
            }

            val range = PsiMapper.textRangeToRange(document, textRange)
            val severity = highlightTypeToDiagnosticSeverity(descriptor.highlightType)
            val message = descriptor.descriptionTemplate ?: return null

            if (message.isBlank()) return null

            return Diagnostic(
                range = range,
                severity = severity,
                code = wrapper.shortName,
                source = "IntelliJ",
                message = message
            )
        } catch (e: Exception) {
            log.debug("Error converting ProblemDescriptor to Diagnostic: ${e.message}")
            return null
        }
    }

    /**
     * 转换高亮类型到诊断严重性
     */
    private fun highlightTypeToDiagnosticSeverity(highlightType: ProblemHighlightType): DiagnosticSeverity {
        return when (highlightType) {
            ProblemHighlightType.ERROR,
            ProblemHighlightType.GENERIC_ERROR -> DiagnosticSeverity.ERROR

            ProblemHighlightType.WARNING,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING -> DiagnosticSeverity.WARNING

            ProblemHighlightType.WEAK_WARNING,
            ProblemHighlightType.INFORMATION -> DiagnosticSeverity.INFORMATION

            else -> DiagnosticSeverity.HINT
        }
    }

    /**
     * 根据位置和消息查找对应的 ProblemDescriptor（用于 CodeAction resolve）
     */
    fun findProblemDescriptor(
        psiFile: PsiFile,
        document: Document,
        offset: Int,
        message: String
    ): Pair<ProblemDescriptor, LocalInspectionTool>? {
        val diagnosticsWithDescriptors = getDiagnosticsWithDescriptors(psiFile, document)

        for (item in diagnosticsWithDescriptors) {
            val startOffset = PsiMapper.positionToOffset(document, item.diagnostic.range.start)
            val endOffset = PsiMapper.positionToOffset(document, item.diagnostic.range.end)

            // 检查 offset 是否在范围内，且消息匹配
            if (offset in startOffset..endOffset && item.diagnostic.message == message) {
                return Pair(item.descriptor, item.inspection)
            }
        }

        return null
    }

    /**
     * 获取 ProblemDescriptor 的 QuickFix 列表
     */
    fun getQuickFixes(descriptor: ProblemDescriptor): Array<out LocalQuickFix>? {
        return descriptor.fixes?.filterIsInstance<LocalQuickFix>()?.toTypedArray()
    }
}
