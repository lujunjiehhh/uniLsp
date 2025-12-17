package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.language.LanguageHandlerRegistry
import com.frenchef.intellijlsp.protocol.models.ParameterInformation
import com.frenchef.intellijlsp.protocol.models.SignatureHelp
import com.frenchef.intellijlsp.protocol.models.SignatureInformation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * 签名帮助 Provider
 *
 * 使用 LanguageHandler 抽象层支持多语言。
 * 对于 JVM 语言提供完整功能，其他语言安全降级。
 */
class SignatureHelpProvider(private val project: Project) {
    private val log = logger<SignatureHelpProvider>()

    /** 获取给定位置的签名帮助信息 */
    fun getSignatureHelp(psiFile: PsiFile, document: Document, offset: Int): SignatureHelp? {
        return ReadAction.compute<SignatureHelp?, RuntimeException> {
            try {
                val element = psiFile.findElementAt(offset) ?: return@compute null

                val handler = LanguageHandlerRegistry.getHandler(psiFile)

                // 查找包含元素的调用表达式
                val callExpression = handler.findContainingCallExpression(element)
                if (callExpression == null) {
                    log.debug("No call expression found at offset $offset")
                    return@compute null
                }

                // 获取签名信息
                val signatureInfo = handler.getSignatureInfo(callExpression)
                if (signatureInfo == null) {
                    log.debug("No signature info available for call expression")
                    return@compute null
                }

                // 计算活动参数索引
                val activeParameter = calculateActiveParameter(handler, callExpression, offset)

                // 转换为 LSP SignatureInformation
                val lspSignature = SignatureInformation(
                    label = signatureInfo.label,
                    documentation = signatureInfo.documentation,
                    parameters = signatureInfo.parameters.map { param ->
                        ParameterInformation(
                            label = "${param.name}: ${param.type}",
                            documentation = param.documentation
                        )
                    },
                    activeParameter = null
                )

                SignatureHelp(
                    signatures = listOf(lspSignature),
                    activeSignature = 0,
                    activeParameter = activeParameter
                )
            } catch (e: Exception) {
                log.warn("Error getting signature help at offset $offset", e)
                null
            }
        }
    }

    /** 计算活动参数索引 */
    private fun calculateActiveParameter(
        handler: com.frenchef.intellijlsp.language.LanguageHandler,
        callExpression: com.frenchef.intellijlsp.language.CallExpressionInfo,
        offset: Int
    ): Int {
        val args = handler.getCallArgumentsInfo(callExpression)
        if (args.isEmpty()) return 0

        for (arg in args) {
            val textRange = arg.psiElement.textRange ?: continue
            if (offset <= textRange.endOffset) {
                return arg.index
            }
        }

        // offset 在所有参数之后，返回最后一个参数的索引 (0-based)
        return (args.size - 1).coerceAtLeast(0)
    }
}
