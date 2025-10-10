package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.models.Location
import com.frenchef.intellijlsp.protocol.models.ReferenceParams
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * Handles textDocument/references requests.
 * Provides "find all references" functionality.
 */
class ReferencesHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<ReferencesHandler>()
    private val gson = Gson()

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/references", this::handleReferences)
    }

    /**
     * Handle textDocument/references request.
     */
    private fun handleReferences(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val refParams = gson.fromJson(params, ReferenceParams::class.java)
            val uri = refParams.textDocument.uri
            val position = refParams.position
            val includeDeclaration = refParams.context.includeDeclaration
            
            log.debug("References requested for $uri at line ${position.line}, char ${position.character}")
            
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
            
            // Get references
            val locations = getReferences(element, includeDeclaration)
            
            if (locations.isEmpty()) {
                log.debug("No references found")
                return null
            }
            
            return gson.toJsonTree(locations)
            
        } catch (e: Exception) {
            log.error("Error handling references", e)
            return null
        }
    }

    /**
     * Get all references to a PSI element.
     */
    private fun getReferences(element: com.intellij.psi.PsiElement, includeDeclaration: Boolean): List<Location> {
        return ReadAction.compute<List<Location>, RuntimeException> {
            val locations = mutableListOf<Location>()
            
            try {
                // Get the target element (what this element refers to)
                val targetElement = element.reference?.resolve() ?: element
                
                // Include the declaration if requested
                if (includeDeclaration) {
                    val declLocation = PsiMapper.psiElementToLocation(targetElement)
                    if (declLocation != null) {
                        locations.add(declLocation)
                    }
                }
                
                // Find all references to the target element
                val query = ReferencesSearch.search(targetElement, targetElement.useScope)
                
                for (reference in query.findAll().take(100)) { // Limit to 100 references
                    val refElement = reference.element
                    val location = PsiMapper.psiElementToLocation(refElement)
                    
                    if (location != null) {
                        locations.add(location)
                    }
                }
                
            } catch (e: Exception) {
                log.warn("Error getting references", e)
            }
            
            locations
        }
    }
}

