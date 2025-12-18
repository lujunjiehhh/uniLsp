package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.language.LanguageHandlerRegistry
import com.frenchef.intellijlsp.protocol.models.InlayHint
import com.frenchef.intellijlsp.protocol.models.InlayHintKinds
import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * 内联提示 Provider
 *
 * 使用 LanguageHandler 抽象层支持多语言。
 * 对于 JVM 语言提供完整功能（参数名 + 类型提示），其他语言安全降级。
 */
class InlayHintsProvider(private val project: Project) {
    private val log = logger<InlayHintsProvider>()

    private companion object {
        const val MAX_HINTS = 500
    }

    /** 获取给定范围的内联提示 */
    fun getInlayHints(psiFile: PsiFile, range: Range): List<InlayHint> {
        return ReadAction.compute<List<InlayHint>, RuntimeException> {
            try {
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    ?: return@compute emptyList()

                val startOffset = PsiMapper.positionToOffset(document, range.start)
                val endOffset = PsiMapper.positionToOffset(document, range.end)

                val handler = LanguageHandlerRegistry.getHandler(psiFile)
                val hints = mutableListOf<InlayHint>()

                // 1. 收集参数名提示
                // 优化：直接枚举调用表达式节点，避免 O(N * parent-walk) 复杂度
                val callExpressions = handler.getCallExpressionsInRange(psiFile, startOffset, endOffset)

                if (callExpressions.isNotEmpty()) {
                    // 使用优化路径：handler 提供了范围内的调用表达式
                    for (callExpr in callExpressions) {
                        if (hints.size >= MAX_HINTS) break
                        collectParameterHints(handler, callExpr, document, hints)
                    }
                } else {
                    // 回退路径：遍历 PSI 元素（兼容未实现 getCallExpressionsInRange 的 handler）
                    PsiTreeUtil.collectElements(psiFile) { element ->
                        val elRange = element.textRange
                        elRange != null && elRange.startOffset >= startOffset && elRange.endOffset <= endOffset
                    }.forEach { element ->
                        if (hints.size >= MAX_HINTS) return@forEach

                        val callExpression = handler.findContainingCallExpression(element)
                        if (callExpression != null && callExpression.psiElement == element) {
                            collectParameterHints(handler, callExpression, document, hints)
                        }
                    }
                }

                // 2. 收集类型提示
                if (hints.size < MAX_HINTS) {
                    collectTypeHints(handler, psiFile, startOffset, endOffset, document, hints)
                }

                log.info("Found ${hints.size} inlay hints")
                hints.take(MAX_HINTS)
            } catch (e: Exception) {
                log.warn("Error getting inlay hints", e)
                emptyList()
            }
        }
    }

    /** 收集参数名提示 */
    private fun collectParameterHints(
        handler: com.frenchef.intellijlsp.language.LanguageHandler,
        callExpression: com.frenchef.intellijlsp.language.CallExpressionInfo,
        document: com.intellij.openapi.editor.Document,
        hints: MutableList<InlayHint>
    ) {
        val args = handler.getCallArgumentsInfo(callExpression)

        for (arg in args) {
            if (hints.size >= MAX_HINTS) break
            if (arg.isNamed) continue // 跳过已命名参数

            val argOffset = arg.psiElement.textRange?.startOffset ?: continue
            val line = document.getLineNumber(argOffset)
            val char = argOffset - document.getLineStartOffset(line)

            hints.add(
                InlayHint(
                    position = Position(line = line, character = char),
                    label = "${arg.parameterName}:",
                    kind = InlayHintKinds.PARAMETER,
                    paddingLeft = false,
                    paddingRight = true
                )
            )
        }
    }

    /** 收集类型提示 */
    private fun collectTypeHints(
        handler: com.frenchef.intellijlsp.language.LanguageHandler,
        psiFile: PsiFile,
        startOffset: Int,
        endOffset: Int,
        document: com.intellij.openapi.editor.Document,
        hints: MutableList<InlayHint>
    ) {
        val variables = handler.getVariablesNeedingTypeHints(psiFile, startOffset, endOffset)

        for (variable in variables) {
            if (hints.size >= MAX_HINTS) break
            if (variable.hasExplicitType) continue // 跳过有显式类型的变量

            val line = document.getLineNumber(variable.insertOffset)
            val char = variable.insertOffset - document.getLineStartOffset(line)

            hints.add(
                InlayHint(
                    position = Position(line = line, character = char),
                    label = ": ${variable.typeText}",
                    kind = InlayHintKinds.TYPE,
                    paddingLeft = false,
                    paddingRight = true
                )
            )
        }
    }
}
