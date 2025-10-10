package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.Location
import com.frenchef.intellijlsp.protocol.models.TextDocumentPositionParams
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Handles textDocument/definition requests.
 * Provides "go to definition" functionality.
 */
class DefinitionHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<DefinitionHandler>()
    private val gson = Gson()

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/definition", this::handleDefinition)
    }

    /**
     * Handle textDocument/definition request.
     */
    private fun handleDefinition(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val defParams = gson.fromJson(params, TextDocumentPositionParams::class.java)
            val uri = defParams.textDocument.uri
            val position = defParams.position
            
            log.debug("Definition requested for $uri at line ${position.line}, char ${position.character}")
            
            val virtualFile = documentManager.getVirtualFile(uri) ?: return null
            val document = documentManager.getIntellijDocument(uri) ?: return null
            
            val psiFile = ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                PsiManager.getInstance(project).findFile(virtualFile)
            } ?: return null
            
            val element = PsiMapper.getPsiElementAtPosition(psiFile, document, position)
            
            if (element == null) {
                log.debug("No PSI element found at position")
                return null
            }
            
            // Get the definitions
            val locations = getDefinitions(element)
            
            if (locations.isEmpty()) {
                log.debug("No definitions found")
                return null
            }
            
            // Return single location or array of locations
            return if (locations.size == 1) {
                gson.toJsonTree(locations.first())
            } else {
                gson.toJsonTree(locations)
            }
            
        } catch (e: Exception) {
            log.error("Error handling definition", e)
            return null
        }
    }

    /**
     * Get definitions for a PSI element.
     */
    private fun getDefinitions(element: com.intellij.psi.PsiElement): List<Location> {
        return ReadAction.compute<List<Location>, RuntimeException> {
            val locations = mutableListOf<Location>()
            
            try {
                // Get the reference from this element
                val reference = element.reference
                
                if (reference != null) {
                    // Resolve the reference to its target
                    val targetElement = reference.resolve()
                    
                    if (targetElement != null) {
                        val location = PsiMapper.psiElementToLocation(targetElement)
                        if (location != null) {
                            locations.add(location)
                        }
                    }
                } else {
                    // If no reference, try the element itself (might be a declaration)
                    val parent = element.parent
                    if (parent != null) {
                        val location = PsiMapper.psiElementToLocation(parent)
                        if (location != null) {
                            locations.add(location)
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Error getting definitions", e)
            }
            
            locations
        }
    }
}

