package com.frenchef.intellijlsp.language

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * 语言处理器接口 - 定义语言特定的符号解析方法
 * 
 * 每种语言（或语言组）应实现此接口来提供语言特定的 AST 遍历和符号解析能力。
 * 这允许 LSP 功能（如 Call Hierarchy、Type Hierarchy）跨语言工作。
 */
interface LanguageHandler {

    /** 检查是否支持该文件的语言 */
    fun supports(file: PsiFile): Boolean

    /** 查找包含指定元素的方法/函数 */
    fun findContainingFunction(element: PsiElement): FunctionInfo?

    /** 查找包含指定元素的类/类型 */
    fun findContainingClass(element: PsiElement): ClassInfo?

    /** 获取函数内的所有调用表达式 */
    fun getCallExpressions(function: FunctionInfo): List<CallExpressionInfo>

    /** 获取类的父类型（父类 + 实现的接口） */
    fun getSuperTypes(classInfo: ClassInfo): List<ClassInfo>

    /** 获取类的子类型（子类 + 实现类） */
    fun getSubTypes(classInfo: ClassInfo): List<ClassInfo>

    /** 尝试解析元素的引用目标 */
    fun resolveReference(element: PsiElement): PsiElement?

    /** 查找包含指定元素的调用表达式 */
    fun findContainingCallExpression(element: PsiElement): CallExpressionInfo?

    /** 获取签名帮助信息（用于 textDocument/signatureHelp） */
    fun getSignatureInfo(callExpression: CallExpressionInfo): SignatureInfo?

    /** 获取调用表达式的参数信息（用于 inlay hints） */
    fun getCallArgumentsInfo(callExpression: CallExpressionInfo): List<ArgumentInfo>

    /** 获取需要类型提示的变量列表（用于 inlay hints） */
    fun getVariablesNeedingTypeHints(file: PsiFile, startOffset: Int, endOffset: Int): List<VariableTypeHintInfo>
}

/**
 * 统一的函数/方法信息表示
 * 
 * 抽象不同语言的函数概念（Java 方法、Kotlin 函数、JS 函数等）
 */
data class FunctionInfo(
    /** 原始 PSI 元素 */
    val psiElement: PsiElement,
    /** 函数名称 */
    val name: String,
    /** 包含此函数的类（如果有） */
    val containingClass: ClassInfo?,
    /** 名称标识符元素（用于精确定位） */
    val nameIdentifier: PsiElement?,
    /** 是否为构造函数 */
    val isConstructor: Boolean = false
)

/**
 * 统一的类/类型信息表示
 * 
 * 抽象不同语言的类概念（Java 类、Kotlin 类、JS 类/原型等）
 */
data class ClassInfo(
    /** 原始 PSI 元素 */
    val psiElement: PsiElement,
    /** 类名称 */
    val name: String,
    /** 完全限定名（如果可用） */
    val qualifiedName: String?,
    /** 名称标识符元素 */
    val nameIdentifier: PsiElement?
)

/**
 * 统一的调用表达式信息
 * 
 * 表示函数/方法调用
 */
data class CallExpressionInfo(
    /** 调用表达式的 PSI 元素 */
    val psiElement: PsiElement,
    /** 被调用的方法名 */
    val methodName: String,
    /** 解析后的目标函数（如果可解析） */
    val resolvedTarget: FunctionInfo?
)

/**
 * 签名信息 - 用于 textDocument/signatureHelp
 */
data class SignatureInfo(
    /** 完整签名标签 */
    val label: String,
    /** 文档描述 */
    val documentation: String?,
    /** 参数列表 */
    val parameters: List<ParameterInfo>,
    /** 返回类型 */
    val returnType: String?
)

/**
 * 参数信息
 */
data class ParameterInfo(
    /** 参数名 */
    val name: String,
    /** 参数类型 */
    val type: String,
    /** 参数文档 */
    val documentation: String?
)

/**
 * 调用参数信息 - 用于 inlay hints
 */
data class ArgumentInfo(
    /** 参数位置的 PSI 元素 */
    val psiElement: PsiElement,
    /** 参数名 */
    val parameterName: String,
    /** 参数索引 */
    val index: Int,
    /** 是否为命名参数 */
    val isNamed: Boolean
)

/**
 * 变量类型提示信息 - 用于 inlay hints
 */
data class VariableTypeHintInfo(
    /** 变量名后的偏移位置（用于插入类型提示） */
    val insertOffset: Int,
    /** 类型文本 */
    val typeText: String,
    /** 是否有显式类型声明 */
    val hasExplicitType: Boolean
)
