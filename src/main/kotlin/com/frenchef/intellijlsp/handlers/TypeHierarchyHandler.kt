package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.TypeHierarchyProvider
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.TypeHierarchyPrepareParams
import com.frenchef.intellijlsp.protocol.models.TypeHierarchySubtypesParams
import com.frenchef.intellijlsp.protocol.models.TypeHierarchySupertypesParams
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Phase 10: Type Hierarchy Handler (T021)
 *
 * 处理 LSP Type Hierarchy 相关请求：
 * - textDocument/prepareTypeHierarchy
 * - typeHierarchy/supertypes
 * - typeHierarchy/subtypes
 */
class TypeHierarchyHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<TypeHierarchyHandler>()
    private val gson = LspGson.instance
    private val provider = TypeHierarchyProvider(project)

    /** 注册 Type Hierarchy 相关 handlers */
    fun register() {
        jsonRpcHandler.registerRequestHandler(
            "textDocument/prepareTypeHierarchy",
            this::handlePrepare
        )
        jsonRpcHandler.registerRequestHandler("typeHierarchy/supertypes", this::handleSupertypes)
        jsonRpcHandler.registerRequestHandler("typeHierarchy/subtypes", this::handleSubtypes)
        log.info("TypeHierarchyHandler registered")
    }

    /** 处理 textDocument/prepareTypeHierarchy 请求 */
    private fun handlePrepare(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val prepareParams = gson.fromJson(params, TypeHierarchyPrepareParams::class.java)
            val uri = prepareParams.textDocument.uri
            val position = prepareParams.position

            log.debug("prepareTypeHierarchy requested for $uri at line ${position.line}")

            val virtualFile = documentManager.getVirtualFile(uri) ?: return null
            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: return null

            val result = provider.prepareTypeHierarchy(psiFile, position)
            if (result.isNullOrEmpty()) {
                log.debug("prepareTypeHierarchy: no class found")
                return null
            }

            return gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Error handling prepareTypeHierarchy", e)
            return null
        }
    }

    /** 处理 typeHierarchy/supertypes 请求 */
    private fun handleSupertypes(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val typeParams = gson.fromJson(params, TypeHierarchySupertypesParams::class.java)
            log.debug("supertypes requested for class '${typeParams.item.name}'")

            val result = provider.getSupertypes(typeParams.item)
            log.debug("supertypes: found ${result.size} supertypes")

            return gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Error handling supertypes", e)
            return null
        }
    }

    /** 处理 typeHierarchy/subtypes 请求 */
    private fun handleSubtypes(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val typeParams = gson.fromJson(params, TypeHierarchySubtypesParams::class.java)
            log.debug("subtypes requested for class '${typeParams.item.name}'")

            val result = provider.getSubtypes(typeParams.item)
            log.debug("subtypes: found ${result.size} subtypes")

            return gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Error handling subtypes", e)
            return null
        }
    }
}
