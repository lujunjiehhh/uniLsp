package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.RenameProvider
import com.frenchef.intellijlsp.intellij.RenameResult
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.PrepareRenameParams
import com.frenchef.intellijlsp.protocol.models.RenameParams
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Phase 10: Rename Handler (T011)
 *
 * 处理 LSP textDocument/prepareRename 和 textDocument/rename 请求。
 */
class RenameHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<RenameHandler>()
    private val gson = LspGson.instance
    private val renameProvider = RenameProvider(project)

    /** 注册 Rename 相关 handlers */
    fun register() {
        jsonRpcHandler.registerRequestHandler(
            "textDocument/prepareRename",
            this::handlePrepareRename
        )
        jsonRpcHandler.registerRequestHandler("textDocument/rename", this::handleRename)
        log.info("RenameHandler registered for textDocument/prepareRename and textDocument/rename")
    }

    /**
     * 处理 textDocument/prepareRename 请求
     *
     * 验证符号是否可重命名，返回重命名范围和占位符名称。
     */
    private fun handlePrepareRename(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("prepareRename: null params")
            return null
        }

        try {
            val prepareParams = gson.fromJson(params, PrepareRenameParams::class.java)
            val uri = prepareParams.textDocument.uri
            val position = prepareParams.position

            log.debug(
                "prepareRename requested for $uri at line ${position.line}, char ${position.character}"
            )

            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.debug("prepareRename: file not found: $uri")
                        return null
                    }

            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.debug("prepareRename: could not get PsiFile for $uri")
                        return null
                    }

            val result = renameProvider.prepareRename(psiFile, position)
            if (result == null) {
                log.debug("prepareRename: element is not renameable")
                return null
            }

            log.debug("prepareRename: returning placeholder '${result.placeholder}'")
            return gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Error handling prepareRename", e)
            return null
        }
    }

    /**
     * 处理 textDocument/rename 请求
     *
     * 执行重命名操作，返回包含所有修改的 WorkspaceEdit。
     */
    private fun handleRename(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            log.debug("rename: null params")
            return null
        }

        try {
            val renameParams = gson.fromJson(params, RenameParams::class.java)
            val uri = renameParams.textDocument.uri
            val position = renameParams.position
            val newName = renameParams.newName

            log.info("rename requested for $uri at line ${position.line}: newName='$newName'")

            val virtualFile =
                documentManager.getVirtualFile(uri)
                    ?: run {
                        log.debug("rename: file not found: $uri")
                        return null
                    }

            val psiFile =
                ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(virtualFile)
                }
                    ?: run {
                        log.debug("rename: could not get PsiFile for $uri")
                        return null
                    }

            return when (val result = renameProvider.rename(psiFile, position, newName)) {
                is RenameResult.Success -> {
                    log.info("rename: success, ${result.edit.changes?.size ?: 0} files affected")
                    gson.toJsonTree(result.edit)
                }

                is RenameResult.Error -> {
                    log.warn("rename: error - ${result.message}")
                    // LSP 允许返回 null 表示无法重命名
                    null
                }

                null -> {
                    log.debug("rename: returned null")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Error handling rename", e)
            return null
        }
    }
}
