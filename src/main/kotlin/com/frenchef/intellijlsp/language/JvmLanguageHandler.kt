package com.frenchef.intellijlsp.language

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * JVM 语言处理器 - 使用 UAST 支持 Java/Kotlin/Groovy/Scala
 * 
 * 封装所有 UAST 特定逻辑，使 Provider/Handler 不再直接依赖 UAST API。
 */
class JvmLanguageHandler : LanguageHandler {
    private val log = logger<JvmLanguageHandler>()

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("JAVA", "kotlin", "Groovy", "Scala")
    }

    override fun supports(file: PsiFile): Boolean {
        return file.language.id in SUPPORTED_LANGUAGES
    }

    override fun findContainingFunction(element: PsiElement): FunctionInfo? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            // 尝试 UAST 转换
            val uElement = current.toUElement()
            if (uElement is UMethod) {
                return uMethodToFunctionInfo(uElement)
            }
            // 回退到纯 Java PSI
            if (current is PsiMethod) {
                return psiMethodToFunctionInfo(current)
            }
            current = current.parent
        }
        return null
    }

    override fun findContainingClass(element: PsiElement): ClassInfo? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            val uElement = current.toUElement()
            if (uElement is UClass) {
                return psiClassToClassInfo(uElement.javaPsi)
            }
            if (current is PsiClass) {
                return psiClassToClassInfo(current)
            }
            current = current.parent
        }
        return null
    }

    override fun getCallExpressions(function: FunctionInfo): List<CallExpressionInfo> {
        val results = mutableListOf<CallExpressionInfo>()

        val uMethod = function.psiElement.toUElement() as? UMethod
        if (uMethod == null) {
            log.debug("Cannot convert to UMethod: ${function.name}")
            return results
        }

        uMethod.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val sourcePsi = node.sourcePsi ?: return false
                val resolved = node.resolve()

                results.add(
                    CallExpressionInfo(
                    psiElement = sourcePsi,
                    methodName = node.methodName ?: "<unknown>",
                    resolvedTarget = resolved?.let { psiMethodToFunctionInfo(it) }
                ))
                return false // 继续遍历
            }
        })

        return results
    }

    override fun getSuperTypes(classInfo: ClassInfo): List<ClassInfo> {
        val psiClass = classInfo.psiElement as? PsiClass ?: return emptyList()
        return psiClass.supers.mapNotNull { superClass ->
            // 过滤掉 java.lang.Object（除非明确需要）
            if (superClass.qualifiedName == "java.lang.Object") null
            else psiClassToClassInfo(superClass)
        }
    }

    override fun getSubTypes(classInfo: ClassInfo): List<ClassInfo> {
        val psiClass = classInfo.psiElement as? PsiClass ?: return emptyList()
        val project = psiClass.project
        val scope = GlobalSearchScope.projectScope(project)

        return try {
            ClassInheritorsSearch.search(psiClass, scope, false)
                .findAll()
                .take(100) // 限制结果数量
                .map { psiClassToClassInfo(it) }
        } catch (e: Exception) {
            log.warn("Error searching for subtypes of ${classInfo.name}", e)
            emptyList()
        }
    }

    override fun resolveReference(element: PsiElement): PsiElement? {
        // 尝试解析引用
        val reference = element.reference ?: return null
        return reference.resolve()
    }

    override fun findContainingCallExpression(element: PsiElement): CallExpressionInfo? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            val uElement = current.toUElement()
            if (uElement is UCallExpression) {
                val resolved = uElement.resolve()
                return CallExpressionInfo(
                    psiElement = current,
                    methodName = uElement.methodName ?: "<unknown>",
                    resolvedTarget = resolved?.let { psiMethodToFunctionInfo(it) }
                )
            }
            current = current.parent
        }
        return null
    }

    override fun getSignatureInfo(callExpression: CallExpressionInfo): SignatureInfo? {
        val uCall = callExpression.psiElement.toUElement() as? UCallExpression ?: return null
        val resolved = uCall.resolve() ?: return null
        val uMethod = resolved.toUElement() as? UMethod ?: return null

        val params = uMethod.uastParameters.map { param ->
            ParameterInfo(
                name = param.name ?: "",
                type = param.type.presentableText,
                documentation = null
            )
        }

        val paramStr = params.joinToString(", ") { "${it.name}: ${it.type}" }
        val returnType = uMethod.returnType?.presentableText ?: "Unit"
        val label = "${uMethod.name}($paramStr): $returnType"

        // 获取文档
        val documentation = getMethodDocumentation(uMethod)

        return SignatureInfo(
            label = label,
            documentation = documentation,
            parameters = params,
            returnType = returnType
        )
    }

    override fun getCallArgumentsInfo(callExpression: CallExpressionInfo): List<ArgumentInfo> {
        val uCall = callExpression.psiElement.toUElement() as? UCallExpression ?: return emptyList()
        val resolved = uCall.resolve() ?: return emptyList()
        val uMethod = resolved.toUElement() as? UMethod ?: return emptyList()

        val parameters = uMethod.uastParameters
        val arguments = uCall.valueArguments
        val results = mutableListOf<ArgumentInfo>()

        for ((index, arg) in arguments.withIndex()) {
            if (index >= parameters.size) break

            val param = parameters[index]
            val paramName = param.name ?: continue
            val argPsi = arg.sourcePsi ?: continue

            // 使用 UAST 判断是否为命名参数
            // UNamedExpression 表示 Kotlin 的命名参数语法（paramName = value）
            val isNamed = arg is UNamedExpression

            results.add(
                ArgumentInfo(
                    psiElement = argPsi,
                    parameterName = paramName,
                    index = index,
                    isNamed = isNamed
                )
            )
        }

        return results
    }

    /** 获取方法文档 */
    private fun getMethodDocumentation(uMethod: UMethod): String? {
        val javaPsi = uMethod.javaPsi
        val docComment = (javaPsi as? PsiDocCommentOwner)?.docComment
        if (docComment != null) {
            return docComment.text
                .replace("/**", "")
                .replace("*/", "")
                .replace(Regex("^\\s*\\*\\s?", RegexOption.MULTILINE), "")
                .trim()
        }
        return null
    }

    override fun getVariablesNeedingTypeHints(
        file: PsiFile,
        startOffset: Int,
        endOffset: Int
    ): List<VariableTypeHintInfo> {
        val results = mutableListOf<VariableTypeHintInfo>()

        PsiTreeUtil.collectElements(file) { element ->
            val range = element.textRange
            range != null && range.startOffset >= startOffset && range.endOffset <= endOffset
        }.forEach { element ->
            val uVariable = element.toUElement() as? UVariable ?: return@forEach

            // 检查是否需要类型提示
            if (uVariable.uastInitializer == null) return@forEach
            if (hasExplicitType(uVariable)) return@forEach

            val sourcePsi = uVariable.sourcePsi ?: return@forEach
            val nameIdentifier = (sourcePsi as? PsiNameIdentifierOwner)?.nameIdentifier ?: return@forEach
            val insertOffset = nameIdentifier.textRange?.endOffset ?: return@forEach

            val typeText = uVariable.type.presentableText

            results.add(
                VariableTypeHintInfo(
                    insertOffset = insertOffset,
                    typeText = typeText,
                    hasExplicitType = false
                )
            )
        }

        return results
    }

    override fun getCallExpressionsInRange(file: PsiFile, startOffset: Int, endOffset: Int): List<CallExpressionInfo> {
        val results = mutableListOf<CallExpressionInfo>()

        val uFile = file.toUElement()
        if (uFile == null) {
            log.debug("Cannot convert file to UAST: ${file.name}")
            return results
        }

        // 直接访问 UAST 调用表达式，避免 O(N * parent-walk)
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val sourcePsi = node.sourcePsi ?: return false
                val range = sourcePsi.textRange ?: return false

                // 检查是否在指定范围内
                if (range.startOffset >= startOffset && range.endOffset <= endOffset) {
                    val resolved = node.resolve()
                    results.add(
                        CallExpressionInfo(
                            psiElement = sourcePsi,
                            methodName = node.methodName ?: "<unknown>",
                            resolvedTarget = resolved?.let { psiMethodToFunctionInfo(it) }
                        )
                    )
                }
                return false // 继续遍历
            }
        })

        return results
    }

    /** 检查变量是否有显式类型声明 */
    private fun hasExplicitType(variable: UVariable): Boolean {
        val sourcePsi = variable.sourcePsi ?: return true

        // Java: 绝大多数局部变量都有显式类型（除了 `var`）
        if (sourcePsi is PsiVariable) {
            val typeText = sourcePsi.typeElement?.text?.trim()
            return !typeText.isNullOrBlank() && typeText != "var"
        }

        // Kotlin/Scala 等：常见形式为 `name: Type`，这里使用变量名做一个更稳妥的启发式
        val name = variable.name
        if (!name.isNullOrBlank()) {
            val pattern = Regex("\\b${Regex.escape(name)}\\s*:")
            if (pattern.containsMatchIn(sourcePsi.text)) {
                return true
            }
        }

        return false
    }

    // ============ 辅助转换方法 ============

    private fun uMethodToFunctionInfo(uMethod: UMethod): FunctionInfo {
        // 优先使用 sourcePsi（原始源代码元素），避免 Kotlin light element 定位问题
        // javaPsi 对于 Kotlin 可能是 light element，导致 containingFile/textRange 不正确
        val sourcePsi = uMethod.sourcePsi
        val javaPsi = uMethod.javaPsi

        // 使用 sourcePsi 作为主要元素（如果可用），确保定位准确
        val psiElement = sourcePsi ?: javaPsi
        
        return FunctionInfo(
            psiElement = psiElement,
            name = javaPsi.name,
            containingClass = javaPsi.containingClass?.let { psiClassToClassInfo(it) },
            // 名称标识符优先从 sourcePsi 获取
            nameIdentifier = (sourcePsi as? PsiNameIdentifierOwner)?.nameIdentifier
                ?: javaPsi.nameIdentifier,
            isConstructor = javaPsi.isConstructor
        )
    }

    private fun psiMethodToFunctionInfo(psiMethod: PsiMethod): FunctionInfo {
        // 优先使用 navigationElement 获取源文件中的元素
        // 这样可以避免 Kotlin light method 导致的定位问题
        val navElement = psiMethod.navigationElement
        val sourceElement = if (navElement !== psiMethod && navElement != null) navElement else psiMethod
        
        return FunctionInfo(
            psiElement = sourceElement,
            name = psiMethod.name,
            containingClass = psiMethod.containingClass?.let { psiClassToClassInfo(it) },
            nameIdentifier = (sourceElement as? PsiNameIdentifierOwner)?.nameIdentifier
                ?: psiMethod.nameIdentifier,
            isConstructor = psiMethod.isConstructor
        )
    }

    private fun psiClassToClassInfo(psiClass: PsiClass): ClassInfo {
        return ClassInfo(
            psiElement = psiClass,
            name = psiClass.name ?: "<anonymous>",
            qualifiedName = psiClass.qualifiedName,
            nameIdentifier = psiClass.nameIdentifier
        )
    }
}
