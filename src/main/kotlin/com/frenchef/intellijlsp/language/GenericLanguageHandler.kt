package com.frenchef.intellijlsp.language

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * 通用语言处理器 - 安全失败的回退方案
 * 
 * 设计原则：
 * - 保证调用链不会炸（返回空/部分结果）
 * - 不尝试做精确的语义分析（避免误报/性能问题）
 * - 仅做基础的引用解析封装
 * - 为后续语言专用 handler 预留扩展位
 */
class GenericLanguageHandler : LanguageHandler {
    private val log = logger<GenericLanguageHandler>()

    override fun supports(file: PsiFile): Boolean = true // 回退方案，接受所有语言

    override fun findContainingFunction(element: PsiElement): FunctionInfo? {
        // 通用实现：返回 null 避免"看起来支持但后续为空"的体验
        // 非 JVM 语言不尝试返回 Item，这样客户端会直接显示"无结果"
        // 而不是显示一个点开后没有内容的 Item
        log.debug("GenericLanguageHandler.findContainingFunction: returning null (semantic analysis not supported)")
        return null
    }

    override fun findContainingClass(element: PsiElement): ClassInfo? {
        // 通用实现：返回 null，理由同上
        log.debug("GenericLanguageHandler.findContainingClass: returning null (semantic analysis not supported)")
        return null
    }

    override fun getCallExpressions(function: FunctionInfo): List<CallExpressionInfo> {
        // 通用实现：返回空列表 
        // 不尝试遍历查找调用，因为没有语义信息会产生大量误报
        log.debug("GenericLanguageHandler.getCallExpressions: returning empty list (not supported)")
        return emptyList()
    }

    override fun getSuperTypes(classInfo: ClassInfo): List<ClassInfo> {
        // 通用实现：返回空列表
        // 没有语义继承信息
        log.debug("GenericLanguageHandler.getSuperTypes: returning empty list (not supported)")
        return emptyList()
    }

    override fun getSubTypes(classInfo: ClassInfo): List<ClassInfo> {
        // 通用实现：返回空列表
        log.debug("GenericLanguageHandler.getSubTypes: returning empty list (not supported)")
        return emptyList()
    }

    override fun resolveReference(element: PsiElement): PsiElement? {
        // 通用实现：尝试基础引用解析（这是 PSI 通用能力）
        return try {
            element.reference?.resolve()
        } catch (e: Exception) {
            log.debug("GenericLanguageHandler.resolveReference failed: ${e.message}")
            null
        }
    }

    override fun findContainingCallExpression(element: PsiElement): CallExpressionInfo? {
        // 通用实现：安全失败，返回 null
        log.debug("GenericLanguageHandler.findContainingCallExpression: not supported")
        return null
    }

    override fun getSignatureInfo(callExpression: CallExpressionInfo): SignatureInfo? {
        // 通用实现：安全失败，返回 null
        log.debug("GenericLanguageHandler.getSignatureInfo: not supported")
        return null
    }

    override fun getCallArgumentsInfo(callExpression: CallExpressionInfo): List<ArgumentInfo> {
        // 通用实现：安全失败，返回空
        log.debug("GenericLanguageHandler.getCallArgumentsInfo: not supported")
        return emptyList()
    }

    override fun getVariablesNeedingTypeHints(
        file: PsiFile,
        startOffset: Int,
        endOffset: Int
    ): List<VariableTypeHintInfo> {
        // 通用实现：安全失败，返回空
        log.debug("GenericLanguageHandler.getVariablesNeedingTypeHints: not supported")
        return emptyList()
    }

    // ============ 启发式判断辅助方法 ============

    private fun isFunctionLike(element: PsiElement): Boolean {
        val className = element.javaClass.simpleName.lowercase()
        return className.contains("function") ||
                className.contains("method") ||
                className.contains("procedure") ||
                className.contains("routine")
    }

    private fun isClassLike(element: PsiElement): Boolean {
        val className = element.javaClass.simpleName.lowercase()
        return (className.contains("class") && !className.contains("anonymous")) ||
                className.contains("interface") ||
                className.contains("trait") ||
                className.contains("struct") ||
                (className.contains("type") && className.contains("def"))
    }
}
