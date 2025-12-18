package com.frenchef.intellijlsp.language.go

import com.frenchef.intellijlsp.language.*
import com.goide.GoLanguage
import com.goide.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * Go 语言处理器
 * 
 * 参考 draw-graph 的 GoParser.kt 实现
 */
class GoLanguageHandler : LanguageHandler {

    override fun supports(file: PsiFile): Boolean {
        return file.language.id == GoLanguage.INSTANCE.id
    }

    override fun findContainingFunction(element: PsiElement): FunctionInfo? {
        // 如果 element 本身就是函数，直接使用（不使用 strict=true 的父查找）123
        val func = (element as? GoFunctionOrMethodDeclaration)
            ?: PsiTreeUtil.getParentOfType(element, GoFunctionOrMethodDeclaration::class.java, false)
            ?: return null

        // 获取方法接收者对应的类型信息
        val classInfo = if (func is GoMethodDeclaration) {
            val receiver = func.receiver
            val receiverType = receiver?.type
            val receiverTypeName = receiverType?.text?.replace("*", "") ?: ""

            // 尝试解析 receiver 类型到实际的 GoTypeSpec
            // GoType 需要通过 reference 或其他方式解析
            val typeSpec: GoTypeSpec? = when (val type = receiverType) {
                is GoPointerType -> {
                    val innerType = type.type
                    (innerType as? GoTypeReferenceExpression)?.resolve() as? GoTypeSpec
                }

                is GoTypeReferenceExpression -> type.resolve() as? GoTypeSpec
                else -> null
            }

            if (typeSpec != null) {
                ClassInfo(
                    psiElement = typeSpec,
                    name = typeSpec.name ?: receiverTypeName,
                    qualifiedName = typeSpec.qualifiedName,
                    nameIdentifier = typeSpec.nameIdentifier
                )
            } else if (receiverTypeName.isNotEmpty()) {
                // 回退：如果无法解析，仍返回基本信息
                ClassInfo(
                    psiElement = receiver ?: func,
                    name = receiverTypeName,
                    qualifiedName = null,
                    nameIdentifier = receiver
                )
            } else {
                null
            }
        } else {
            null
        }

        return FunctionInfo(
            psiElement = func,
            name = func.name ?: "<anonymous>",
            containingClass = classInfo,
            nameIdentifier = func.nameIdentifier
        )
    }

    override fun findContainingClass(element: PsiElement): ClassInfo? {
        // 自身优先：如果 element 本身就是类型定义，直接返回
        val typeSpec = (element as? GoTypeSpec)
            ?: PsiTreeUtil.getParentOfType(element, GoTypeSpec::class.java, false)
            ?: return null
        return ClassInfo(
            psiElement = typeSpec,
            name = typeSpec.name ?: "<anonymous>",
            qualifiedName = typeSpec.qualifiedName,
            nameIdentifier = typeSpec.nameIdentifier
        )
    }

    override fun getCallExpressions(function: FunctionInfo): List<CallExpressionInfo> {
        // 使用 GoCallExpr 作为入口点，而不是 GoReferenceExpression
        // 这样可以避免把"函数当值传递/赋值"等纯引用误判为调用
        val element = function.psiElement
        val callExprs = PsiTreeUtil.findChildrenOfType(element, GoCallExpr::class.java)

        val result = mutableListOf<CallExpressionInfo>()
        for (call in callExprs) {
            val expr = call.expression
            val resolved = resolveCalleeExpression(expr)

            if (resolved is GoFunctionDeclaration || resolved is GoMethodDeclaration) {
                result.add(
                    CallExpressionInfo(
                        psiElement = call,
                        methodName = expr.text ?: "<unknown>",
                        resolvedTarget = findContainingFunction(resolved)
                    )
                )
            }
        }
        return result
    }

    override fun getSuperTypes(classInfo: ClassInfo): List<ClassInfo> {
        // Go 的 embedding / interfaces 暂不支持
        return emptyList()
    }

    override fun getSubTypes(classInfo: ClassInfo): List<ClassInfo> {
        return emptyList()
    }

    override fun resolveReference(element: PsiElement): PsiElement? {
        return (element as? GoReferenceExpression)?.resolve()
    }

    override fun findContainingCallExpression(element: PsiElement): CallExpressionInfo? {
        // 如果 element 本身就是调用表达式，直接使用（不使用 strict=true 的父查找）
        val call = (element as? GoCallExpr)
            ?: PsiTreeUtil.getParentOfType(element, GoCallExpr::class.java, false)
            ?: return null

        // 尝试从 GoCallExpr 获取引用并解析
        // 支持多种 callee 形式：直接引用、selector 表达式等
        val expr = call.expression
        val resolved: PsiElement? = resolveCalleeExpression(expr)

        val target = if (resolved != null) findContainingFunction(resolved) else null

        return CallExpressionInfo(
            psiElement = call,
            methodName = expr.text ?: "<unknown>",
            resolvedTarget = target
        )
    }

    /** 解析调用表达式的 callee，支持多种形式 */
    private fun resolveCalleeExpression(expr: GoExpression?): PsiElement? {
        // Go 的 selector 表达式（如 a.Method() 或 pkg.Func()）
        // 也是通过 GoReferenceExpression 处理的（带有 qualifier）
        // GoReferenceExpressionBase 是基类，包含所有引用表达式
        return when (expr) {
            is GoReferenceExpressionBase -> expr.resolve()
            else -> null
        }
    }

    override fun getSignatureInfo(callExpression: CallExpressionInfo): SignatureInfo? {
        val target = callExpression.resolvedTarget?.psiElement as? GoSignatureOwner ?: return null

        val signature = target.signature ?: return null
        val params = signature.parameters.parameterDeclarationList.flatMap { decl ->
            decl.paramDefinitionList.mapNotNull { def ->
                val paramName = def.identifier.text ?: def.text
                val typeText = decl.type?.text ?: ""
                ParameterInfo(
                    name = paramName,
                    type = typeText,
                    documentation = null
                )
            }
        }

        val returnType = signature.result?.type?.text
        val label = buildString {
            append((target as? GoNamedElement)?.name ?: "<anonymous>")
            append("(")
            append(params.joinToString(", ") { "${it.name}: ${it.type}" })
            append(")")
            if (!returnType.isNullOrBlank()) {
                append(": ")
                append(returnType)
            }
        }

        return SignatureInfo(
            label = label,
            documentation = null,
            parameters = params,
            returnType = returnType
        )
    }

    override fun getCallArgumentsInfo(callExpression: CallExpressionInfo): List<ArgumentInfo> {
        val call = callExpression.psiElement as? GoCallExpr ?: return emptyList()
        val args = call.argumentList.expressionList

        val target = callExpression.resolvedTarget?.psiElement as? GoSignatureOwner
        val signature = target?.signature
        val paramNames = signature?.parameters?.parameterDeclarationList.orEmpty().flatMap { decl ->
            decl.paramDefinitionList.mapNotNull { def ->
                def.identifier.text ?: def.text
            }
        }

        return args.mapIndexed { index, arg ->
            ArgumentInfo(
                psiElement = arg,
                parameterName = paramNames.getOrNull(index) ?: "arg$index",
                index = index,
                isNamed = false
            )
        }
    }

    override fun getVariablesNeedingTypeHints(
        file: PsiFile,
        startOffset: Int,
        endOffset: Int
    ): List<VariableTypeHintInfo> {
        // Go 的类型推断需要更复杂的实现，暂时返回空列表
        // Go 编译器会自动推断类型，IDE 通常使用内置的类型推断 API
        return emptyList()
    }
}
