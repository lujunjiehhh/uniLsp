package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.InspectionDiagnosticsProvider
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.*
import com.google.gson.JsonElement
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * 处理 codeAction/resolve 请求
 *
 * 用于惰性解析 CodeAction 的 edit 属性：
 * 1. 从 data 字段恢复上下文（uri, offset, actionTitle）
 * 2. 找到对应的 IntentionAction/QuickFix/LocalQuickFix
 * 3. 在 PSI 文件副本上应用
 * 4. 计算差异生成 WorkspaceEdit
 */
class CodeActionResolveHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<CodeActionResolveHandler>()
    private val gson = LspGson.instance
    private val inspectionProvider = InspectionDiagnosticsProvider(project)

    /** 注册 codeAction/resolve handler */
    fun register() {
        jsonRpcHandler.registerRequestHandler("codeAction/resolve", this::handleResolve)
        log.info("CodeActionResolveHandler registered for codeAction/resolve")
    }

    /** 处理 codeAction/resolve 请求 */
    private fun handleResolve(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("codeAction/resolve: null params received")
            return null
        }

        return try {
            val codeAction = gson.fromJson(params, CodeAction::class.java)
            val data = codeAction.data

            if (data == null) {
                log.warn("codeAction/resolve: No data field in CodeAction")
                return gson.toJsonTree(codeAction)
            }

            log.info("Resolving CodeAction: ${codeAction.title} for ${data.uri}")

            val resolvedAction = resolveCodeAction(codeAction, data)
            gson.toJsonTree(resolvedAction)
        } catch (e: Exception) {
            log.error("Error resolving code action", e)
            params // 返回原始请求
        }
    }

    /** 解析 CodeAction，计算 edit */
    private fun resolveCodeAction(codeAction: CodeAction, data: CodeActionData): CodeAction {
        val virtualFile = documentManager.getVirtualFile(data.uri)
        if (virtualFile == null) {
            log.warn("Virtual file not found: ${data.uri}")
            return codeAction
        }

        // 1. 获取 PSI 和 Document (ReadAction)
        val (psiFile, document) =
            ReadAction.compute<Pair<PsiFile, Document>?, RuntimeException> {
                val file = PsiManager.getInstance(project).findFile(virtualFile)
                if (file == null) {
                    log.warn("PSI file not found")
                    return@compute null
                }

                val doc = PsiDocumentManager.getInstance(project).getDocument(file)
                if (doc == null) {
                    log.warn("Document not found")
                    return@compute null
                }
                Pair(file, doc)
            }
                ?: return codeAction

        // 2. 根据 actionType 查找并应用对应的 action
        // 注意：resolveQuickFix 和 resolveIntention 内部会处理 EDT/ReadContext，
        // 所以这里不能包裹在 ReadAction 中，否则会导致 deadlock (invokeAndWait form ReadAction)
        val edit =
            when (data.actionType) {
                "quickfix" -> resolveQuickFix(psiFile, document, data)
                "intention" -> resolveIntention(psiFile, document, data)
                "inspection_quickfix" -> resolveInspectionQuickFix(psiFile, document, data)
                else -> {
                    log.warn("Unknown action type: ${data.actionType}")
                    null
                }
            }

        return codeAction.copy(edit = edit)
    }

    /** 解析 QuickFix 类型的 CodeAction */
    private fun resolveQuickFix(
        psiFile: PsiFile,
        document: Document,
        data: CodeActionData
    ): WorkspaceEdit? {
        try {
            var foundAction: IntentionAction? = null
            var result: WorkspaceEdit? = null

            val safeOffset = data.offset.coerceIn(0, document.textLength)

            // 扩大搜索范围：获取当前行的起始和结束位置
            val lineNumber = document.getLineNumber(safeOffset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)

            log.info("Searching QuickFix in range $lineStart-$lineEnd (line $lineNumber)")

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                // 从 HighlightInfo 中查找匹配的 QuickFix
                DaemonCodeAnalyzerEx.processHighlights(
                    document,
                    project,
                    null,
                    lineStart,
                    lineEnd
                ) { info: HighlightInfo ->
                    if (foundAction != null) return@processHighlights false

                    log.debug("Found HighlightInfo: ${info.description}, severity=${info.severity}")

                    info.findRegisteredQuickFix<Any?> { descriptor, _ ->
                        log.debug("  QuickFix: ${descriptor.action.text}")
                        if (descriptor.action.text == data.actionTitle) {
                            foundAction = descriptor.action
                            log.info("Found matching QuickFix: ${data.actionTitle}")
                        }
                        null
                    }
                    foundAction == null
                }

                val action = foundAction
                if (action != null) {
                    result = applyActionAndGetEdit(psiFile, document, action, data)
                }
            }

            if (foundAction == null) {
                log.warn("QuickFix not found: ${data.actionTitle}")
                return null
            }

            return result
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.error("Error resolving quickfix", e)
            return null
        }
    }

    /** 解析 Intention 类型的 CodeAction */
    private fun resolveIntention(
        psiFile: PsiFile,
        document: Document,
        data: CodeActionData
    ): WorkspaceEdit? {
        try {
            var result: WorkspaceEdit? = null

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                var tempEditor: Editor? = null
                try {
                    val safeOffset = data.offset.coerceIn(0, document.textLength)
                    tempEditor = EditorFactory.getInstance().createEditor(document, project)
                    tempEditor.caretModel.moveToOffset(safeOffset)

                    val intentionManager = IntentionManager.getInstance()

                    // 查找匹配的 intention
                    val matchingIntention =
                        intentionManager.intentionActions.find { intention ->
                            try {
                                intention.isAvailable(project, tempEditor, psiFile) &&
                                        intention.text == data.actionTitle
                            } catch (_: Exception) {
                                false
                            }
                        }

                    if (matchingIntention == null) {
                        log.warn("Intention not found: ${data.actionTitle}")
                    } else {
                        // 在 EDT 中应用 action
                        result = applyActionAndGetEdit(psiFile, document, matchingIntention, data)
                    }
                } finally {
                    if (tempEditor != null && !tempEditor.isDisposed) {
                        EditorFactory.getInstance().releaseEditor(tempEditor)
                    }
                }
            }

            return result
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.error("Error resolving intention", e)
            return null
        }
    }

    /** 解析 InspectionQuickFix 类型的 CodeAction */
    private fun resolveInspectionQuickFix(
        psiFile: PsiFile,
        document: Document,
        data: CodeActionData
    ): WorkspaceEdit? {
        try {
            var result: WorkspaceEdit? = null

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                // 获取诊断信息
                val diagnosticsWithDescriptors = inspectionProvider.getDiagnosticsWithDescriptors(psiFile, document)

                // 查找匹配的 QuickFix
                var foundDescriptor: ProblemDescriptor? = null
                var foundFix: LocalQuickFix? = null

                for (item in diagnosticsWithDescriptors) {
                    val diagStartOffset = PsiMapper.positionToOffset(document, item.diagnostic.range.start)
                    val diagEndOffset = PsiMapper.positionToOffset(document, item.diagnostic.range.end)

                    // 检查 offset 是否匹配
                    if (data.offset !in diagStartOffset..diagEndOffset) continue

                    val fixes = item.descriptor.fixes?.filterIsInstance<LocalQuickFix>() ?: continue

                    for (fix in fixes) {
                        if (fix.name == data.actionTitle) {
                            foundDescriptor = item.descriptor
                            foundFix = fix
                            break
                        }
                    }

                    if (foundFix != null) break
                }

                if (foundDescriptor != null && foundFix != null) {
                    result = applyLocalQuickFixAndGetEdit(psiFile, document, foundFix, foundDescriptor, data)
                } else {
                    log.warn("InspectionQuickFix not found: ${data.actionTitle}")
                }
            }

            return result
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.error("Error resolving inspection quickfix", e)
            return null
        }
    }

    /**
     * 应用 LocalQuickFix 并获取 edit
     */
    private fun applyLocalQuickFixAndGetEdit(
        psiFile: PsiFile,
        document: Document,
        fix: LocalQuickFix,
        descriptor: ProblemDescriptor,
        data: CodeActionData
    ): WorkspaceEdit? {
        val originalText = document.text

        try {
            var modifiedText: String? = null

            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    PsiDocumentManager.getInstance(project).commitDocument(document)

                    fix.applyFix(project, descriptor)
                    PsiDocumentManager.getInstance(project).commitDocument(document)

                    modifiedText = document.text
                } catch (e: Exception) {
                    log.warn("Failed to apply LocalQuickFix: ${e.message}", e)
                } finally {
                    try {
                        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
                        document.setText(originalText)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    } catch (e: Exception) {
                        log.warn("Failed to rollback document text: ${e.message}", e)
                    }
                }
            }

            val after = modifiedText ?: return null
            if (after == originalText) return null

            val textEdits = computeFullDocumentReplaceEdit(originalText, after)
            if (textEdits.isEmpty()) return null

            return WorkspaceEdit(changes = mapOf(data.uri to textEdits))
        } catch (e: Exception) {
            log.error("Error applying LocalQuickFix and getting edit", e)
            return null
        }
    }

    /**
     * 在原文件上临时应用 action 并计算 edit，然后立刻回滚文本。
     *
     * 说明：
     * - 很多 IntelliJ/Kotlin 的 Intention/QuickFix 依赖真实的 PSI/VirtualFile 上下文；
     *   在 PsiFileFactory 的副本上 invoke 往往“成功返回但不改文档”，导致 resolve 得不到 edit。
     * - 这里通过“原文档就地应用 → 读取修改后文本 → 还原原文档”来稳定生成 WorkspaceEdit。
     */
    private fun applyActionAndGetEdit(
        originalPsiFile: PsiFile,
        originalDocument: Document,
        action: IntentionAction,
        data: CodeActionData
    ): WorkspaceEdit? {
        val originalText = originalDocument.text
        val safeOffset = data.offset.coerceIn(0, originalDocument.textLength)

        var tempEditor: Editor? = null
        try {
            tempEditor = EditorFactory.getInstance().createEditor(originalDocument, project)
            tempEditor.caretModel.moveToOffset(safeOffset)

            var modifiedText: String? = null

            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    PsiDocumentManager.getInstance(project).commitDocument(originalDocument)

                    action.invoke(project, tempEditor, originalPsiFile)
                    PsiDocumentManager.getInstance(project).commitDocument(originalDocument)

                    modifiedText = originalDocument.text
                } catch (e: Exception) {
                    log.warn("Failed to invoke action: ${e.message}", e)
                } finally {
                    try {
                        // 解锁文档（处理 PSI pending 操作）
                        PsiDocumentManager.getInstance(project)
                            .doPostponedOperationsAndUnblockDocument(originalDocument)
                        // 回滚：避免真实文件被改动（LSP resolve 只需要 edit）
                        originalDocument.setText(originalText)
                        PsiDocumentManager.getInstance(project).commitDocument(originalDocument)
                    } catch (e: Exception) {
                        log.warn("Failed to rollback document text: ${e.message}", e)
                    }
                }
            }

            val after = modifiedText ?: return null
            if (after == originalText) return null

            // 为了鲁棒性：返回“整文件替换”的单个 TextEdit（避免 diff 计算错误导致客户端拒绝应用）
            val textEdits = computeFullDocumentReplaceEdit(originalText, after)
            if (textEdits.isEmpty()) return null

            return WorkspaceEdit(changes = mapOf(data.uri to textEdits))
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.error("Error applying action and getting edit", e)
            return null
        } finally {
            if (tempEditor != null && !tempEditor.isDisposed) {
                EditorFactory.getInstance().releaseEditor(tempEditor)
            }
        }
    }

    private fun computeFullDocumentReplaceEdit(
        originalText: String,
        modifiedText: String
    ): List<TextEdit> {
        if (originalText == modifiedText) return emptyList()

        val lines = originalText.split("\n")
        val endLine = (lines.size - 1).coerceAtLeast(0)
        val endChar = if (lines.isEmpty()) 0 else lines.last().length

        return listOf(
            TextEdit(
                range =
                    Range(
                        start = Position(line = 0, character = 0),
                        end = Position(line = endLine, character = endChar)
                    ),
                newText = modifiedText
            )
        )
    }
}
