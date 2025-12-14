package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * T034: 处理 textDocument/typeDefinition 请求
 * 跳转到符号的类型定义位置（如变量类型、返回类型等）
 */
class TypeDefinitionHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<TypeDefinitionHandler>()
    private val gson = Gson()

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/typeDefinition", this::handleTypeDefinition)
    }

    /**
     * 处理 typeDefinition 请求
     * @return Location | Location[] | null
     */
    private fun handleTypeDefinition(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.warn("TypeDefinition request received with null params")
            return null
        }

        val positionParams = gson.fromJson(params, TextDocumentPositionParams::class.java)
        val uri = positionParams.textDocument.uri
        val position = positionParams.position

        log.info("TypeDefinition request for $uri at line ${position.line}, char ${position.character}")

        return try {
            ReadAction.compute<JsonElement?, Exception> {
                val psiFile = PsiMapper.getPsiFile(project, uri) ?: run {
                    log.warn("Could not find PSI file for $uri")
                    return@compute null
                }

                val document = PsiMapper.getDocument(psiFile) ?: run {
                    log.warn("Could not get document for $uri")
                    return@compute null
                }

                val offset = PsiMapper.positionToOffset(document, position)
                val element = psiFile.findElementAt(offset)

                if (element == null) {
                    log.debug("No element found at offset $offset")
                    return@compute null
                }

                // 尝试获取元素的类型定义
                val typeElement = resolveTypeDefinition(element)
                if (typeElement != null) {
                    val location = PsiMapper.elementToLocation(typeElement)
                    if (location != null) {
                        return@compute gson.toJsonTree(location)
                    }
                }

                null
            }
        } catch (e: Exception) {
            log.error("Error handling typeDefinition request", e)
            null
        }
    }

    /**
     * 解析元素的类型定义
     */
    private fun resolveTypeDefinition(element: PsiElement): PsiElement? {
        val namedElement = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java, false)
            ?: return null

        try {
            val typeRef = findTypeReference(namedElement)
            if (typeRef != null) {
                val resolved = resolveReference(typeRef)
                if (resolved != null) {
                    return resolved
                }
            }
        } catch (e: Exception) {
            log.debug("Could not resolve type definition: ${e.message}")
        }

        return null
    }

    private fun findTypeReference(element: PsiElement): PsiElement? {
        for (child in element.children) {
            val childText = child.javaClass.simpleName.lowercase()
            if (childText.contains("type") || childText.contains("reference")) {
                return child
            }
        }
        return null
    }

    private fun resolveReference(element: PsiElement): PsiElement? {
        val references = element.references
        for (ref in references) {
            val resolved = ref.resolve()
            if (resolved != null) {
                return resolved
            }
        }
        return null
    }
}
