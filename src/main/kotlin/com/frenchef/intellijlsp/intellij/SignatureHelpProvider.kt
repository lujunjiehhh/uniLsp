package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.ParameterInformation
import com.frenchef.intellijlsp.protocol.models.SignatureHelp
import com.frenchef.intellijlsp.protocol.models.SignatureInformation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/**
 * 使用 UAST 实现的签名帮助功能
 *
 * 提供跨 Java/Kotlin 的统一函数签名信息
 */
class SignatureHelpProvider(private val project: Project) {
    private val log = logger<SignatureHelpProvider>()

    /** 获取给定位置的签名帮助信息 */
    fun getSignatureHelp(psiFile: PsiFile, document: Document, offset: Int): SignatureHelp? {
        return ReadAction.compute<SignatureHelp?, RuntimeException> {
            try {
                // 找到偏移位置的 PSI 元素
                val element = psiFile.findElementAt(offset) ?: return@compute null

                // 向上查找函数调用表达式，使用 UAST 统一处理
                val callExpression = findCallExpression(element) ?: return@compute null

                // 将 PSI 转换为 UAST
                val uCall = callExpression.toUElement() as? UCallExpression
                if (uCall != null) {
                    return@compute getSignatureHelpFromUast(uCall, offset)
                }

                null
            } catch (e: Exception) {
                log.warn("Error getting signature help at offset $offset", e)
                null
            }
        }
    }

    /** 查找包含给定元素的函数调用表达式 */
    private fun findCallExpression(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            // 使用 UAST 检测是否为调用表达式
            val uElement = current.toUElement()
            if (uElement is UCallExpression) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /** 从 UAST 调用表达式获取签名帮助 */
    private fun getSignatureHelpFromUast(uCall: UCallExpression, offset: Int): SignatureHelp? {
        // 获取被调用的方法
        val resolvedMethod = uCall.resolve() ?: return null
        val uMethod = resolvedMethod.toUElement() as? UMethod ?: return null

        // 构建签名信息
        val signatureInfo = buildSignatureInfo(uMethod)

        // 计算活动参数索引
        val activeParameter = calculateActiveParameter(uCall, offset)

        return SignatureHelp(
            signatures = listOf(signatureInfo),
            activeSignature = 0,
            activeParameter = activeParameter
        )
    }

    /** 构建方法签名信息 */
    private fun buildSignatureInfo(uMethod: UMethod): SignatureInformation {
        val params = uMethod.uastParameters
        val paramInfos =
            params.map { param ->
                ParameterInformation(
                    label = "${param.name}: ${param.type.presentableText}",
                    documentation = null
                )
            }

        // 构建完整签名
        val paramStr = params.joinToString(", ") { "${it.name}: ${it.type.presentableText}" }
        val returnType = uMethod.returnType?.presentableText ?: "Unit"
        val label = "${uMethod.name}($paramStr): $returnType"

        return SignatureInformation(
            label = label,
            documentation = getMethodDocumentation(uMethod),
            parameters = paramInfos,
            activeParameter = null
        )
    }

    /** 计算活动参数索引 */
    private fun calculateActiveParameter(uCall: UCallExpression, offset: Int): Int {
        val args = uCall.valueArguments
        if (args.isEmpty()) return 0

        // 简化实现：根据参数数量估算
        // 完整实现需要精确计算 offset 在哪个参数范围内
        for ((index, arg) in args.withIndex()) {
            val sourcePsi = arg.sourcePsi ?: continue
            val textRange = sourcePsi.textRange ?: continue
            if (offset <= textRange.endOffset) {
                return index
            }
        }

        return args.size.coerceAtMost(0)
    }

    /** 获取方法文档 */
    private fun getMethodDocumentation(uMethod: UMethod): String? {
        val javaPsi = uMethod.javaPsi
        if (javaPsi is PsiDocCommentOwner) {
            val docComment = javaPsi.docComment
            if (docComment != null) {
                return docComment
                    .text
                    .replace("/**", "")
                    .replace("*/", "")
                    .replace(Regex("^\\s*\\*\\s?", RegexOption.MULTILINE), "")
                    .trim()
            }
        }
        return null
    }
}
