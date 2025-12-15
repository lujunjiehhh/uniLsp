package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/**
 * 使用 UAST 实现的工作区符号搜索
 * 
 * 提供跨 Java/Kotlin 的统一符号搜索功能
 */
class WorkspaceSymbolProvider(private val project: Project) {
    private val log = logger<WorkspaceSymbolProvider>()

    private companion object {
        const val MAX_RESULTS = 100
    }

    /**
     * 搜索匹配查询的符号
     */
    fun searchSymbols(query: String): List<SymbolInformation> {
        if (query.isBlank()) {
            log.debug("Empty query, returning empty list")
            return emptyList()
        }

        return ReadAction.compute<List<SymbolInformation>, RuntimeException> {
            try {
                val results = mutableListOf<SymbolInformation>()
                val scope = GlobalSearchScope.projectScope(project)

                // 使用 PsiShortNamesCache 搜索类（Java plugin 提供）
                searchClasses(query, scope, results)

                // 使用 PsiShortNamesCache 搜索方法
                searchMethods(query, scope, results)

                log.info("Found ${results.size} symbols for query: $query")
                results.take(MAX_RESULTS)
            } catch (e: Exception) {
                log.warn("Error searching symbols for query: $query", e)
                emptyList()
            }
        }
    }

    /**
     * 搜索类 (使用 UAST 统一处理)
     */
    private fun searchClasses(
        query: String,
        scope: GlobalSearchScope,
        results: MutableList<SymbolInformation>
    ) {
        try {
            val cache = PsiShortNamesCache.getInstance(project)
            val classNames = cache.allClassNames.filter {
                it.contains(query, ignoreCase = true)
            }.take(50)

            for (name in classNames) {
                if (results.size >= MAX_RESULTS) break
                val classes = cache.getClassesByName(name, scope)
                for (psiClass in classes) {
                    if (results.size >= MAX_RESULTS) break
                    // 使用 UAST 统一转换
                    val uClass = psiClass.toUElement() as? UClass
                    if (uClass != null) {
                        val symbol = uClassToSymbol(uClass, psiClass)
                        if (symbol != null) {
                            results.add(symbol)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("Error searching classes: ${e.message}")
        }
    }

    /**
     * 搜索方法 (使用 UAST 统一处理)
     */
    private fun searchMethods(
        query: String,
        scope: GlobalSearchScope,
        results: MutableList<SymbolInformation>
    ) {
        try {
            val cache = PsiShortNamesCache.getInstance(project)
            val methodNames = cache.allMethodNames.filter {
                it.contains(query, ignoreCase = true)
            }.take(50)

            for (name in methodNames) {
                if (results.size >= MAX_RESULTS) break
                val methods = cache.getMethodsByName(name, scope)
                for (method in methods) {
                    if (results.size >= MAX_RESULTS) break
                    // 使用 UAST 统一转换
                    val uMethod = method.toUElement() as? UMethod
                    if (uMethod != null) {
                        val symbol = uMethodToSymbol(uMethod, method)
                        if (symbol != null) {
                            results.add(symbol)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("Error searching methods: ${e.message}")
        }
    }

    /**
     * 将 UClass 转换为 SymbolInformation
     */
    private fun uClassToSymbol(uClass: UClass, psiClass: PsiClass): SymbolInformation? {
        val file = psiClass.containingFile?.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiClass.containingFile) ?: return null

        val name = uClass.name ?: return null
        val kind = when {
            psiClass.isInterface -> SymbolKind.INTERFACE
            psiClass.isEnum -> SymbolKind.ENUM
            else -> SymbolKind.CLASS
        }

        val startOffset = psiClass.textRange?.startOffset ?: return null
        val startLine = document.getLineNumber(startOffset)
        val startChar = startOffset - document.getLineStartOffset(startLine)

        return SymbolInformation(
            name = name,
            kind = kind,
            location = Location(
                uri = file.url.replace("file://", "file:///"),
                range = Range(
                    start = Position(line = startLine, character = startChar),
                    end = Position(line = startLine, character = startChar + name.length)
                )
            ),
            containerName = psiClass.containingClass?.qualifiedName,
            tags = null,
            deprecated = psiClass.isDeprecated
        )
    }

    /**
     * 将 UMethod 转换为 SymbolInformation
     */
    private fun uMethodToSymbol(uMethod: UMethod, psiMethod: PsiMethod): SymbolInformation? {
        val file = psiMethod.containingFile?.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiMethod.containingFile) ?: return null

        val name = uMethod.name
        val kind = if (uMethod.isConstructor) SymbolKind.CONSTRUCTOR else SymbolKind.METHOD

        val startOffset = psiMethod.textRange?.startOffset ?: return null
        val startLine = document.getLineNumber(startOffset)
        val startChar = startOffset - document.getLineStartOffset(startLine)

        return SymbolInformation(
            name = name,
            kind = kind,
            location = Location(
                uri = file.url.replace("file://", "file:///"),
                range = Range(
                    start = Position(line = startLine, character = startChar),
                    end = Position(line = startLine, character = startChar + name.length)
                )
            ),
            containerName = psiMethod.containingClass?.qualifiedName,
            tags = null,
            deprecated = psiMethod.isDeprecated
        )
    }
}
