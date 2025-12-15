package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.InlayHint
import com.frenchef.intellijlsp.protocol.models.InlayHintKinds
import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.*

/**
 * 使用 UAST 实现的内联提示功能
 *
 * 提供跨 Java/Kotlin 的统一类型推断和参数名提示
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
                val document =
                    PsiDocumentManager.getInstance(project).getDocument(psiFile)
                        ?: return@compute emptyList()

                val startOffset = PsiMapper.positionToOffset(document, range.start)
                val endOffset = PsiMapper.positionToOffset(document, range.end)

                val hints = mutableListOf<InlayHint>()

                // 遍历范围内的 PSI 元素并转换为 UAST
                collectHintsFromPsi(psiFile, startOffset, endOffset, document, hints)

                log.info("Found ${hints.size} inlay hints")
                hints.take(MAX_HINTS)
            } catch (e: Exception) {
                log.warn("Error getting inlay hints", e)
                emptyList()
            }
        }
    }

    /** 从 PSI 收集内联提示并使用 UAST 统一处理 */
    private fun collectHintsFromPsi(
        psiFile: PsiFile,
        startOffset: Int,
        endOffset: Int,
        document: com.intellij.openapi.editor.Document,
        hints: MutableList<InlayHint>
    ) {
        // 收集方法调用的参数名提示
        PsiTreeUtil.collectElements(psiFile) { element ->
            val range = element.textRange
            range != null && range.startOffset >= startOffset && range.endOffset <= endOffset
        }
            .forEach { element ->
                if (hints.size >= MAX_HINTS) return@forEach

                // 使用 UAST 转换
                when (val uElement = element.toUElement()) {
                    is UCallExpression -> collectParameterHints(uElement, document, hints)
                    is UVariable -> {
                        if (shouldShowTypeHint(uElement)) {
                            collectTypeHint(uElement, document, hints)
                        }
                    }
                }
            }
    }

    /** 检查是否应该显示类型提示 */
    private fun shouldShowTypeHint(node: UVariable): Boolean {
        // 对于有初始化器的变量，显示类型提示
        return node.uastInitializer != null
    }

    /** 收集参数名提示 */
    private fun collectParameterHints(
        call: UCallExpression,
        document: com.intellij.openapi.editor.Document,
        hints: MutableList<InlayHint>
    ) {
        val method = call.resolve() ?: return
        val uMethod = method.toUElement() as? UMethod ?: return
        val parameters = uMethod.uastParameters
        val arguments = call.valueArguments

        for ((index, arg) in arguments.withIndex()) {
            if (index >= parameters.size) break
            if (hints.size >= MAX_HINTS) break

            val param = parameters[index]
            val paramName = param.name ?: continue

            // 跳过已命名参数
            if (isNamedArgument(arg)) continue

            val argPsi = arg.sourcePsi ?: continue
            val argOffset = argPsi.textRange?.startOffset ?: continue

            val line = document.getLineNumber(argOffset)
            val char = argOffset - document.getLineStartOffset(line)

            hints.add(
                InlayHint(
                    position = Position(line = line, character = char),
                    label = "$paramName:",
                    kind = InlayHintKinds.PARAMETER,
                    paddingLeft = false,
                    paddingRight = true
                )
            )
        }
    }

    /** 检查是否为命名参数 */
    private fun isNamedArgument(arg: UExpression): Boolean {
        // 简化实现：检查源 PSI 是否包含 "=" 之前的标识符
        val sourcePsi = arg.sourcePsi ?: return false
        val text = sourcePsi.text
        return text.contains("=") && !text.startsWith("=")
    }

    /** 收集类型提示 */
    private fun collectTypeHint(
        variable: UVariable,
        document: com.intellij.openapi.editor.Document,
        hints: MutableList<InlayHint>
    ) {
        val sourcePsi = variable.sourcePsi ?: return
        val nameIdentifier = (sourcePsi as? PsiNameIdentifierOwner)?.nameIdentifier ?: return
        val endOffset = nameIdentifier.textRange?.endOffset ?: return

        val type = variable.type
        val typeText = type.presentableText

        // 跳过显式类型声明
        if (hasExplicitType(variable)) return

        val line = document.getLineNumber(endOffset)
        val char = endOffset - document.getLineStartOffset(line)

        hints.add(
            InlayHint(
                position = Position(line = line, character = char),
                label = ": $typeText",
                kind = InlayHintKinds.TYPE,
                paddingLeft = false,
                paddingRight = true
            )
        )
    }

    /** 检查变量是否有显式类型声明 */
    private fun hasExplicitType(variable: UVariable): Boolean {
        val sourcePsi = variable.sourcePsi ?: return true
        val text = sourcePsi.text
        // 简化检查：如果源代码包含类型声明语法
        return text.contains(":") && !text.startsWith(":")
    }
}
