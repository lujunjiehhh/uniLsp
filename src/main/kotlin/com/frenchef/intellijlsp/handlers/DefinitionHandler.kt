package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.Location
import com.frenchef.intellijlsp.protocol.models.TextDocumentPositionParams
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
    private val gson = LspGson.instance

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

            log.info("Found ${locations.size} definitions for element '${element.text}'")
            locations.forEach { loc ->
                log.info("  Definition: ${loc.uri} range=${loc.range.start.line}:${loc.range.start.character}-${loc.range.end.line}:${loc.range.end.character}")
            }
            
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
     * Get definitions for a PSI element using GotoDeclarationHandler extension point.
     * This is the same mechanism IntelliJ uses for "Go to Definition".
     */
    private fun getDefinitions(element: com.intellij.psi.PsiElement): List<Location> {
        return ReadAction.compute<List<Location>, RuntimeException> {
            val locations = mutableListOf<Location>()
            
            try {
                log.info(
                    "Getting definitions for element: ${element.javaClass.simpleName}, text='${
                        element.text?.take(
                            50
                        )
                    }'"
                )

                // Method 1: Use GotoDeclarationHandler extension point (most reliable)
                val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
                log.info("Found ${handlers.size} GotoDeclarationHandler(s)")

                for (handler in handlers) {
                    try {
                        val targets = handler.getGotoDeclarationTargets(element, 0, null)
                        if (targets != null && targets.isNotEmpty()) {
                            log.info("Handler ${handler.javaClass.simpleName} returned ${targets.size} target(s)")
                            for (target in targets) {
                                // Skip if target is the same element
                                if (target != element && target != element.parent) {
                                    val location = PsiMapper.psiElementToLocation(target)
                                    if (location != null) {
                                        log.info("  Target: ${target.javaClass.simpleName}, file=${target.containingFile?.virtualFile?.path}")
                                        locations.add(location)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.debug("Handler ${handler.javaClass.simpleName} failed: ${e.message}")
                    }
                }

                // Method 2: Try element.references (plural) - Kotlin often uses multiple references
                if (locations.isEmpty()) {
                    log.info("Trying element.references (plural)...")
                    val references = element.references
                    for (ref in references) {
                        val resolved = ref.resolve()
                        if (resolved != null && resolved != element && resolved != element.parent) {
                            val location = PsiMapper.psiElementToLocation(resolved)
                            if (location != null) {
                                log.info("Resolved via references: ${resolved.javaClass.simpleName}")
                                locations.add(location)
                            }
                        }
                    }
                }

                // Method 3: Walk up parent tree to find a meaningful named element
                if (locations.isEmpty()) {
                    log.info("Trying parent walk for named element...")
                    var current: com.intellij.psi.PsiElement? = element
                    while (current != null && current !is com.intellij.psi.PsiFile) {
                        val refs = current.references
                        for (ref in refs) {
                            val resolved = ref.resolve()
                            if (resolved != null && resolved != current) {
                                val location = PsiMapper.psiElementToLocation(resolved)
                                if (location != null) {
                                    log.info("Resolved via parent: ${resolved.javaClass.simpleName}")
                                    locations.add(location)
                                    break
                                }
                            }
                        }
                        if (locations.isNotEmpty()) break
                        current = current.parent
                    }
                }

                log.info("Total definitions found: ${locations.size}")
                
            } catch (e: Exception) {
                log.warn("Error getting definitions", e)
            }

            // Deduplicate by URI and range
            locations.distinctBy { "${it.uri}:${it.range.start.line}:${it.range.start.character}" }
        }
    }
}

