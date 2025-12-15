package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.CallHierarchyProvider
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.CallHierarchyIncomingCallsParams
import com.frenchef.intellijlsp.protocol.models.CallHierarchyOutgoingCallsParams
import com.frenchef.intellijlsp.protocol.models.CallHierarchyPrepareParams
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Phase 10: Call Hierarchy Handler (T016)
 *
 * 处理 LSP Call Hierarchy 相关请求：
 * - textDocument/prepareCallHierarchy
 * - callHierarchy/incomingCalls
 * - callHierarchy/outgoingCalls
 */
class CallHierarchyHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<CallHierarchyHandler>()
    private val gson = Gson()
    private val provider = CallHierarchyProvider(project)

    /** 注册 Call Hierarchy 相关 handlers */
    fun register() {
        jsonRpcHandler.registerRequestHandler(
            "textDocument/prepareCallHierarchy",
            this::handlePrepare
        )
        jsonRpcHandler.registerRequestHandler(
            "callHierarchy/incomingCalls",
            this::handleIncomingCalls
        )
        jsonRpcHandler.registerRequestHandler(
            "callHierarchy/outgoingCalls",
            this::handleOutgoingCalls
        )
        log.info("CallHierarchyHandler registered")
    }

    /** 处理 textDocument/prepareCallHierarchy 请求 */
    private fun handlePrepare(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val prepareParams = gson.fromJson(params, CallHierarchyPrepareParams::class.java)
            val uri = prepareParams.textDocument.uri
            val position = prepareParams.position

            log.debug("prepareCallHierarchy requested for $uri at line ${position.line}")

            val virtualFile = documentManager.getVirtualFile(uri) ?: return null
            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: return null

            val result = provider.prepareCallHierarchy(psiFile, position)
            if (result.isNullOrEmpty()) {
                log.debug("prepareCallHierarchy: no method found")
                return null
            }

            return gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Error handling prepareCallHierarchy", e)
            return null
        }
    }

    /** 处理 callHierarchy/incomingCalls 请求 */
    private fun handleIncomingCalls(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val callParams = gson.fromJson(params, CallHierarchyIncomingCallsParams::class.java)
            log.debug("incomingCalls requested for method '${callParams.item.name}'")

            val result = provider.getIncomingCalls(callParams.item)
            log.debug("incomingCalls: found ${result.size} callers")

            return gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Error handling incomingCalls", e)
            return null
        }
    }

    /** 处理 callHierarchy/outgoingCalls 请求 */
    private fun handleOutgoingCalls(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val callParams = gson.fromJson(params, CallHierarchyOutgoingCallsParams::class.java)
            log.debug("outgoingCalls requested for method '${callParams.item.name}'")

            val result = provider.getOutgoingCalls(callParams.item)
            log.debug("outgoingCalls: found ${result.size} callees")

            return gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Error handling outgoingCalls", e)
            return null
        }
    }
}
