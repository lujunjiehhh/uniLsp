package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.*
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * 符合 LSP 3.17 规范的 CodeAction 提供者
 *
 * 使用两种方式收集 CodeAction：
 * 1. DaemonCodeAnalyzer 的 HighlightInfo 中包含的 QuickFix（用于诊断相关的修复）
 * 2. IntentionManager 的 intentions（通过 isAvailable 检查可用性）
 *
 * 返回的 CodeAction 包含 data 字段，用于 codeAction/resolve 时恢复上下文
 */
class CodeActionProvider(private val project: Project) {
    private val log = logger<CodeActionProvider>()

    private companion object {
        const val MAX_ACTIONS = 50
    }

    /** 获取在给定范围内可用的代码操作 */
    fun getCodeActions(
        psiFile: PsiFile,
        range: Range,
        @Suppress("UNUSED_PARAMETER") context: CodeActionContext,
        uri: String // 新增：文件 URI，用于 resolve
    ): List<CodeAction> {
        try {
            // 1. 准备数据 (ReadAction)
            val (document, startOffset, endOffset) =
                ReadAction.compute<Triple<Document, Int, Int>?, RuntimeException> {
                    val doc =
                        PsiDocumentManager.getInstance(project).getDocument(psiFile)
                            ?: return@compute null
                    val start = PsiMapper.positionToOffset(doc, range.start)
                    val end = PsiMapper.positionToOffset(doc, range.end)
                    Triple(doc, start, end)
                }
                    ?: return emptyList()

            val actions = mutableListOf<CodeAction>()
            val seenTitles = mutableSetOf<String>()

            // 2. 收集来自 HighlightInfo 的 QuickFix (ReadAction)
            ReadAction.run<RuntimeException> {
                collectQuickFixesFromHighlights(
                    psiFile,
                    document,
                    startOffset,
                    endOffset,
                    uri,
                    actions,
                    seenTitles
                )
            }

            // 3. 收集来自 IntentionManager 的 intentions (EDT handled internally)
            // 创建 Editor 和检查 intention 可用性需要 EDT
            collectAvailableIntentions(psiFile, document, startOffset, uri, actions, seenTitles)

            log.info("Found ${actions.size} code actions at position $startOffset-$endOffset")
            return actions.take(MAX_ACTIONS)
        } catch (e: Exception) {
            log.warn("Error getting code actions", e)
            return emptyList()
        }
    }

    /** 从 DaemonCodeAnalyzer 的 HighlightInfo 收集 QuickFix */
    private fun collectQuickFixesFromHighlights(
        psiFile: PsiFile,
        document: Document,
        startOffset: Int,
        endOffset: Int,
        uri: String,
        actions: MutableList<CodeAction>,
        seenTitles: MutableSet<String>
    ) {
        try {
            // 使用静态方法处理高亮
            DaemonCodeAnalyzerEx.processHighlights(
                document,
                project,
                null, // severity filter - null means all
                startOffset,
                endOffset
            ) { info: HighlightInfo ->
                if (actions.size >= MAX_ACTIONS) return@processHighlights false

                // 只处理有问题的高亮（弱警告及以上）
                // Kotlin 很多“可修复但不算 WARNING”的提示属于 WEAK_WARNING，否则会漏掉 QuickFix（例如移除形参中的 val/var）
                val severity = info.severity
                if (severity.myVal >= HighlightSeverity.WEAK_WARNING.myVal) {
                    // QuickFix 需要精确的 caret offset；用 HighlightInfo 的起始位置而不是请求 range 的起点
                    collectQuickFixesFromInfo(info, info.startOffset, uri, actions, seenTitles)
                }
                true // 继续处理
            }

            log.debug("Collected ${actions.size} quick fixes from highlights")
        } catch (e: Exception) {
            log.debug("Error collecting quick fixes from highlights: ${e.message}")
        }
    }

    /** 从单个 HighlightInfo 收集 QuickFix */
    private fun collectQuickFixesFromInfo(
        info: HighlightInfo,
        offset: Int,
        uri: String,
        actions: MutableList<CodeAction>,
        seenTitles: MutableSet<String>
    ) {
        try {
            info.findRegisteredQuickFix<Any?> { descriptor, _ ->
                val action = descriptor.action
                val title = action.text

                if (title.isNotBlank() && title !in seenTitles) {
                    seenTitles.add(title)
                    actions.add(
                        CodeAction(
                            title = title,
                            kind = CodeActionKinds.QUICK_FIX,
                            diagnostics = null,
                            isPreferred = info.severity == HighlightSeverity.ERROR,
                            edit = null,
                            command = null,
                            data =
                                CodeActionData(
                                    uri = uri,
                                    offset = offset,
                                    actionTitle = title,
                                    actionType = "quickfix"
                                )
                        )
                    )
                }
                null
            }
        } catch (e: Exception) {
            log.trace("Error extracting quick fix: ${e.message}")
        }
    }

    /** 收集在指定位置可用的 Intentions */
    private fun collectAvailableIntentions(
        psiFile: PsiFile,
        document: Document,
        offset: Int,
        uri: String,
        actions: MutableList<CodeAction>,
        seenTitles: MutableSet<String>
    ) {
        // 创建 Editor 和检查 intention 需要在 EDT 中执行
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
            var tempEditor: Editor? = null
            try {
                tempEditor = EditorFactory.getInstance().createEditor(document, project)
                tempEditor.caretModel.moveToOffset(offset)

                val intentionManager = IntentionManager.getInstance()

                // 获取所有注册的 intentions 并检查可用性
                for (intention in intentionManager.intentionActions) {
                    if (actions.size >= MAX_ACTIONS) break

                    try {
                        if (intention.isAvailable(project, tempEditor, psiFile)) {
                            val title = intention.text
                            if (title.isNotBlank() && title !in seenTitles) {
                                seenTitles.add(title)
                                actions.add(intentionToCodeAction(intention, uri, offset))
                                log.debug("Intention available: $title")
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略检查失败的 intentions
                    }
                }
            } catch (e: Exception) {
                log.warn("Error collecting intentions: ${e.message}")
            } finally {
                if (tempEditor != null && !tempEditor.isDisposed) {
                    EditorFactory.getInstance().releaseEditor(tempEditor)
                }
            }
        }
    }

    /** 将 IntentionAction 转换为 LSP CodeAction */
    private fun intentionToCodeAction(
        intention: IntentionAction,
        uri: String,
        offset: Int
    ): CodeAction {
        val kind = determineCodeActionKind(intention)

        return CodeAction(
            title = intention.text,
            kind = kind,
            diagnostics = null,
            isPreferred = isPreferredAction(intention),
            edit = null,
            command = null,
            data =
                CodeActionData(
                    uri = uri,
                    offset = offset,
                    actionTitle = intention.text,
                    actionType = "intention"
                )
        )
    }

    /** 根据 intention 类型确定 CodeAction kind */
    private fun determineCodeActionKind(intention: IntentionAction): String {
        val className = intention.javaClass.simpleName.lowercase()
        val text = intention.text.lowercase()

        return when {
            className.contains("fix") || className.contains("quick") -> CodeActionKinds.QUICK_FIX
            text.contains("fix") || text.contains("suppress") -> CodeActionKinds.QUICK_FIX
            text.contains("extract") -> CodeActionKinds.REFACTOR_EXTRACT
            text.contains("inline") -> CodeActionKinds.REFACTOR_INLINE
            text.contains("convert") ||
                    text.contains("change") ||
                    text.contains("replace") ||
                    text.contains("remove") -> CodeActionKinds.REFACTOR_REWRITE

            text.contains("import") && (text.contains("organize") || text.contains("optimize")) ->
                CodeActionKinds.SOURCE_ORGANIZE_IMPORTS

            className.contains("refactor") || text.contains("refactor") -> CodeActionKinds.REFACTOR
            else -> CodeActionKinds.QUICK_FIX
        }
    }

    /** 检查 Intention 是否应该标记为首选 */
    private fun isPreferredAction(intention: IntentionAction): Boolean {
        val text = intention.text.lowercase()
        return text.contains("fix") && !text.contains("suppress")
    }
}
