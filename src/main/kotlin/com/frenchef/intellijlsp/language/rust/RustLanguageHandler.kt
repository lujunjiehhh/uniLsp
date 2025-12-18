package com.frenchef.intellijlsp.language.rust

import com.frenchef.intellijlsp.language.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.patText

/**
 * Rust 语言处理器
 * 
 * 提供 Rust 语言的 LSP 功能支持，包括：
 * - Call Hierarchy (调用层级)
 * - Type Hierarchy (类型层级)
 * - Go to Definition (跳转到定义)
 * 
 * 参考 RustParser.kt 解析逻辑
 */
class RustLanguageHandler : LanguageHandler {

    override fun supports(file: PsiFile): Boolean {
        return file.language.id == RsLanguage.id
    }

    override fun findContainingFunction(element: PsiElement): FunctionInfo? {
        // 如果 element 本身就是函数，直接使用（不使用 strict=true 的父查找）
        val rsFunction = (element as? RsFunction)
            ?: PsiTreeUtil.getParentOfType(element, RsFunction::class.java, false)
            ?: return null

        // 获取所属的 impl 块（类似于类）
        val implItem = PsiTreeUtil.getParentOfType(rsFunction, RsImplItem::class.java)
        val containingClass = if (implItem != null) findImplClassInfo(implItem) else null

        return FunctionInfo(
            psiElement = rsFunction,
            name = rsFunction.name ?: "<anonymous>",
            containingClass = containingClass,
            nameIdentifier = rsFunction.nameIdentifier,
            isConstructor = rsFunction.name == "new"  // Rust 惯例: new() 作为构造函数
        )
    }

    override fun findContainingClass(element: PsiElement): ClassInfo? {
        // Rust 中的"类"概念可以是：
        // 1. impl 块 (RsImplItem)
        // 2. struct 定义 (RsStructItem)
        // 3. trait 定义 (RsTraitItem)
        // 4. mod 模块 (RsModItem)

        // 自身优先检查（解决 selectionRange 命中类节点时返回 null 的问题）
        if (element is RsImplItem) return findImplClassInfo(element)
        if (element is RsStructItem) return ClassInfo(
            psiElement = element,
            name = element.name ?: "<anonymous>",
            qualifiedName = buildQualifiedName(element),
            nameIdentifier = element.nameIdentifier
        )
        if (element is RsTraitItem) return ClassInfo(
            psiElement = element,
            name = element.name ?: "<anonymous>",
            qualifiedName = buildQualifiedName(element),
            nameIdentifier = element.nameIdentifier
        )

        // 查找父节点（strict=false 包含自身）
        val implItem = PsiTreeUtil.getParentOfType(element, RsImplItem::class.java, false)
        if (implItem != null) {
            return findImplClassInfo(implItem)
        }

        val structItem = PsiTreeUtil.getParentOfType(element, RsStructItem::class.java, false)
        if (structItem != null) {
            return ClassInfo(
                psiElement = structItem,
                name = structItem.name ?: "<anonymous>",
                qualifiedName = buildQualifiedName(structItem),
                nameIdentifier = structItem.nameIdentifier
            )
        }

        val traitItem = PsiTreeUtil.getParentOfType(element, RsTraitItem::class.java, false)
        if (traitItem != null) {
            return ClassInfo(
                psiElement = traitItem,
                name = traitItem.name ?: "<anonymous>",
                qualifiedName = buildQualifiedName(traitItem),
                nameIdentifier = traitItem.nameIdentifier
            )
        }

        return null
    }

    /**
     * 从 impl 块获取类信息
     * 参考 RustParser.classMap()
     */
    private fun findImplClassInfo(implItem: RsImplItem): ClassInfo? {
        // impl 块的 firstChild 通常是类型引用
        val typeName = implItem.typeReference?.text ?: return null
        val mod = PsiTreeUtil.getParentOfType(implItem, RsModItem::class.java)?.name

        val qualifiedName = if (mod != null) "$mod::$typeName" else typeName

        return ClassInfo(
            psiElement = implItem,
            name = typeName,
            qualifiedName = qualifiedName,
            nameIdentifier = implItem.typeReference
        )
    }

    override fun getCallExpressions(function: FunctionInfo): List<CallExpressionInfo> {
        val rsFunction = function.psiElement as? RsFunction ?: return emptyList()
        val results = mutableListOf<CallExpressionInfo>()

        // 使用 RsCallExpr 和 RsMethodCall 作为入口点
        // 这样可以避免把"函数 item 当值使用"误判为调用

        // 处理函数调用 (fn_name() 或 path::fn_name())
        val callExprs = PsiTreeUtil.findChildrenOfType(rsFunction, RsCallExpr::class.java)
        for (call in callExprs) {
            val pathExpr = call.expr as? RsPathExpr
            val resolved = pathExpr?.path?.reference?.resolve()
            if (resolved is RsFunction) {
                val resolvedFunctionInfo = findContainingFunction(resolved)
                if (resolvedFunctionInfo != null) {
                    results.add(
                        CallExpressionInfo(
                            psiElement = call,
                            methodName = pathExpr.path.referenceName ?: "",
                            resolvedTarget = resolvedFunctionInfo
                        )
                    )
                }
            }
        }

        // 处理方法调用 (receiver.method())
        val methodCalls = PsiTreeUtil.findChildrenOfType(rsFunction, RsMethodCall::class.java)
        for (methodCall in methodCalls) {
            val resolved = methodCall.reference.resolve()
            if (resolved is RsFunction) {
                val resolvedFunctionInfo = findContainingFunction(resolved)
                if (resolvedFunctionInfo != null) {
                    results.add(
                        CallExpressionInfo(
                            psiElement = methodCall,
                            methodName = methodCall.referenceName,
                            resolvedTarget = resolvedFunctionInfo
                        )
                    )
                }
            }
        }

        return results
    }

    override fun getSuperTypes(classInfo: ClassInfo): List<ClassInfo> {
        val results = mutableListOf<ClassInfo>()

        // 对于 impl 块，查找实现的 trait
        val implItem = classInfo.psiElement as? RsImplItem
        if (implItem != null) {
            val traitRef = implItem.traitRef
            if (traitRef != null) {
                val resolved = traitRef.path.reference?.resolve()
                if (resolved is RsTraitItem) {
                    results.add(
                        ClassInfo(
                            psiElement = resolved,
                            name = resolved.name ?: "<anonymous>",
                            qualifiedName = buildQualifiedName(resolved),
                            nameIdentifier = resolved.nameIdentifier
                        )
                    )
                }
            }
        }

        // 对于 trait，查找 super trait
        val traitItem = classInfo.psiElement as? RsTraitItem
        traitItem?.typeParamBounds?.let { bounds ->
            bounds.polyboundList.forEach { polybound ->
                val resolved = polybound.bound.traitRef?.path?.reference?.resolve()
                if (resolved is RsTraitItem) {
                    results.add(
                        ClassInfo(
                            psiElement = resolved,
                            name = resolved.name ?: "<anonymous>",
                            qualifiedName = buildQualifiedName(resolved),
                            nameIdentifier = resolved.nameIdentifier
                        )
                    )
                }
            }
        }

        return results
    }

    override fun getSubTypes(classInfo: ClassInfo): List<ClassInfo> {
        // 查找子类型需要全局搜索，暂时返回空列表
        // 遵循"安全失败原则"
        return emptyList()
    }

    override fun resolveReference(element: PsiElement): PsiElement? {
        // 处理路径引用
        if (element is RsPath) {
            return element.reference?.resolve()
        }

        // 处理方法调用
        if (element is RsMethodCall) {
            return element.reference.resolve()
        }

        // 处理父元素为引用的情况
        val parentPath = element.parent as? RsPath
        if (parentPath != null) {
            return parentPath.reference?.resolve()
        }

        return null
    }

    override fun findContainingCallExpression(element: PsiElement): CallExpressionInfo? {
        // 如果 element 本身就是 RsCallExpr，直接使用
        val callExpr = (element as? RsCallExpr)
            ?: PsiTreeUtil.getParentOfType(element, RsCallExpr::class.java, false)
        if (callExpr != null) {
            val path = callExpr.expr as? RsPathExpr
            if (path != null) {
                val resolved = path.path.reference?.resolve() as? RsFunction
                return CallExpressionInfo(
                    psiElement = callExpr,
                    methodName = path.path.referenceName ?: "",
                    resolvedTarget = resolved?.let { findContainingFunction(it) }
                )
            }
        }

        // 如果 element 本身就是 RsMethodCall，直接使用
        val methodCall = (element as? RsMethodCall)
            ?: PsiTreeUtil.getParentOfType(element, RsMethodCall::class.java, false)
        if (methodCall != null) {
            val resolved = methodCall.reference.resolve() as? RsFunction
            return CallExpressionInfo(
                psiElement = methodCall,
                methodName = methodCall.referenceName,
                resolvedTarget = resolved?.let { findContainingFunction(it) }
            )
        }

        return null
    }

    override fun getSignatureInfo(callExpression: CallExpressionInfo): SignatureInfo? {
        val resolvedTarget = callExpression.resolvedTarget?.psiElement as? RsFunction
            ?: return null

        val parameters = mutableListOf<ParameterInfo>()

        // 获取参数列表
        resolvedTarget.valueParameterList?.valueParameterList?.forEach { param ->
            parameters.add(
                ParameterInfo(
                    name = param.patText ?: "",
                    type = param.typeReference?.text ?: "",
                    documentation = null
                )
            )
        }

        // 构建签名标签
        val paramText = parameters.joinToString(", ") { "${it.name}: ${it.type}" }
        val returnType = resolvedTarget.retType?.typeReference?.text
        val label = "fn ${resolvedTarget.name}($paramText)" +
                (if (returnType != null) " -> $returnType" else "")

        return SignatureInfo(
            label = label,
            documentation = resolvedTarget.documentation,
            parameters = parameters,
            returnType = returnType
        )
    }

    override fun getCallArgumentsInfo(callExpression: CallExpressionInfo): List<ArgumentInfo> {
        val results = mutableListOf<ArgumentInfo>()
        val resolvedTarget = callExpression.resolvedTarget?.psiElement as? RsFunction
            ?: return emptyList()

        // 获取调用点的参数列表
        val callExpr = callExpression.psiElement as? RsCallExpr
        val methodCall = callExpression.psiElement as? RsMethodCall

        val argList = callExpr?.valueArgumentList ?: methodCall?.valueArgumentList

        val params = resolvedTarget.valueParameterList?.valueParameterList ?: emptyList()
        val args = argList?.exprList ?: emptyList()

        args.forEachIndexed { index, arg ->
            val paramName = params.getOrNull(index)?.patText ?: "arg$index"
            results.add(
                ArgumentInfo(
                    psiElement = arg,
                    parameterName = paramName,
                    index = index,
                    isNamed = false  // Rust 不支持命名参数
                )
            )
        }

        return results
    }

    override fun getVariablesNeedingTypeHints(
        file: PsiFile,
        startOffset: Int,
        endOffset: Int
    ): List<VariableTypeHintInfo> {
        val results = mutableListOf<VariableTypeHintInfo>()

        // 查找范围内的 let 绑定
        PsiTreeUtil.findChildrenOfType(file, RsLetDecl::class.java).forEach { letDecl ->
            val nameRange = letDecl.pat?.textRange ?: return@forEach

            // 检查是否在范围内
            if (nameRange.startOffset < startOffset || nameRange.endOffset > endOffset) {
                return@forEach
            }

            // 检查是否已有显式类型声明
            val hasExplicitType = letDecl.typeReference != null
            if (hasExplicitType) {
                return@forEach
            }

            // 尝试推断类型 (简化实现，实际需要类型推断引擎)
            val inferredType = letDecl.expr?.let { inferType(it) } ?: return@forEach

            results.add(
                VariableTypeHintInfo(
                    insertOffset = nameRange.endOffset,
                    typeText = inferredType,
                    hasExplicitType = false
                )
            )
        }

        return results
    }

    /**
     * 构建限定名称
     */
    private fun buildQualifiedName(element: PsiElement): String? {
        val parts = mutableListOf<String>()

        // 获取元素名称
        when (element) {
            is RsStructItem -> element.name?.let { parts.add(it) }
            is RsTraitItem -> element.name?.let { parts.add(it) }
            is RsEnumItem -> element.name?.let { parts.add(it) }
        }

        // 获取父模块
        var current: PsiElement? = element.parent
        while (current != null) {
            if (current is RsModItem) {
                current.name?.let { parts.add(0, it) }
            }
            current = current.parent
        }

        return if (parts.isNotEmpty()) parts.joinToString("::") else null
    }

    /**
     * 简单类型推断 (用于 inlay hints)
     */
    private fun inferType(expr: RsExpr): String? {
        return when (expr) {
            is RsLitExpr -> when {
                expr.kind is RsLiteralKind.Integer -> "i32"
                expr.kind is RsLiteralKind.Float -> "f64"
                expr.kind is RsLiteralKind.Boolean -> "bool"
                expr.kind is RsLiteralKind.String -> "&str"
                expr.kind is RsLiteralKind.Char -> "char"
                else -> null
            }

            is RsPathExpr -> {
                // 尝试从路径解析类型
                when (val resolved = expr.path.reference?.resolve()) {
                    is RsStructItem -> resolved.name
                    is RsEnumItem -> resolved.name
                    else -> null
                }
            }

            else -> null
        }
    }
}

// 获取函数的文档注释
private val RsFunction.documentation: String?
    get() = this.outerDocElements.joinToString("\n") { it.text.trimStart('/').trim() }
        .takeIf { it.isNotEmpty() }

// 获取外部文档元素
private val RsFunction.outerDocElements: List<PsiElement>
    get() {
        val result = mutableListOf<PsiElement>()
        var sibling = this.prevSibling
        while (sibling != null) {
            if (sibling is PsiWhiteSpace) {
                sibling = sibling.prevSibling
                continue
            }
            if (sibling is RsOuterAttr && sibling.text.startsWith("///")) {
                result.add(0, sibling)
                sibling = sibling.prevSibling
            } else {
                break
            }
        }
        return result
    }

// 辅助类型扩展
private typealias PsiWhiteSpace = com.intellij.psi.PsiWhiteSpace
