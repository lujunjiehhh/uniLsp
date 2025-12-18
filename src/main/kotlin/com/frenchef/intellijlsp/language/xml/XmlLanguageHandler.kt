package com.frenchef.intellijlsp.language.xml

import com.frenchef.intellijlsp.language.*
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * XML 语言处理器
 * 
 * XML 是标记语言，与编程语言不同：
 * - 没有函数/方法概念，findContainingFunction 返回 null
 * - XmlTag 可视为结构化元素，作为 ClassInfo 返回
 * - 支持基础的引用解析（如 DTD/Schema 引用）
 */
class XmlLanguageHandler : LanguageHandler {

    private val log = logger<XmlLanguageHandler>()

    override fun supports(file: PsiFile): Boolean {
        // 支持 XML 语言文件
        return file.language.id == XMLLanguage.INSTANCE.id || file is XmlFile
    }

    override fun findContainingFunction(element: PsiElement): FunctionInfo? {
        // XML 没有函数概念，返回 null
        log.debug("XmlLanguageHandler.findContainingFunction: XML 没有函数概念，返回 null")
        return null
    }

    override fun findContainingClass(element: PsiElement): ClassInfo? {
        // 将 XmlTag 视为"类"概念
        val xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)
            ?: (element as? XmlTag)
            ?: return null

        return ClassInfo(
            psiElement = xmlTag,
            name = xmlTag.name,
            qualifiedName = getTagQualifiedName(xmlTag),
            // 使用标签的名称元素而不是 firstChild（firstChild 可能是 '<' token）
            nameIdentifier = xmlTag.children.find { it.text == xmlTag.name }
        )
    }

    override fun getCallExpressions(function: FunctionInfo): List<CallExpressionInfo> {
        // XML 没有函数调用概念
        log.debug("XmlLanguageHandler.getCallExpressions: XML 没有函数调用，返回空列表")
        return emptyList()
    }

    override fun getSuperTypes(classInfo: ClassInfo): List<ClassInfo> {
        // XML 没有类继承体系
        // 可以考虑返回父标签作为"父类型"，但为了保持语义清晰，暂返回空
        log.debug("XmlLanguageHandler.getSuperTypes: XML 没有继承体系，返回空列表")
        return emptyList()
    }

    override fun getSubTypes(classInfo: ClassInfo): List<ClassInfo> {
        // XML 没有类继承体系
        log.debug("XmlLanguageHandler.getSubTypes: XML 没有继承体系，返回空列表")
        return emptyList()
    }

    override fun resolveReference(element: PsiElement): PsiElement? {
        // 尝试解析 XML 引用（如 DTD/Schema 引用、属性值引用等）
        return try {
            element.reference?.resolve()
        } catch (e: Exception) {
            log.debug("XmlLanguageHandler.resolveReference 失败: ${e.message}")
            null
        }
    }

    override fun findContainingCallExpression(element: PsiElement): CallExpressionInfo? {
        // XML 没有调用表达式
        log.debug("XmlLanguageHandler.findContainingCallExpression: XML 没有调用表达式，返回 null")
        return null
    }

    override fun getSignatureInfo(callExpression: CallExpressionInfo): SignatureInfo? {
        // XML 没有函数签名
        return null
    }

    override fun getCallArgumentsInfo(callExpression: CallExpressionInfo): List<ArgumentInfo> {
        // XML 没有函数参数
        return emptyList()
    }

    override fun getVariablesNeedingTypeHints(
        file: PsiFile,
        startOffset: Int,
        endOffset: Int
    ): List<VariableTypeHintInfo> {
        // XML 没有变量类型
        return emptyList()
    }

    // ============ 辅助方法 ============

    /**
     * 获取 XML 标签的完全限定名（路径形式）
     * 例如: project/dependencies/dependency
     */
    private fun getTagQualifiedName(tag: XmlTag): String {
        val path = mutableListOf<String>()
        var current: XmlTag? = tag

        while (current != null) {
            path.add(0, current.name)
            current = current.parentTag
        }

        return path.joinToString("/")
    }
}
