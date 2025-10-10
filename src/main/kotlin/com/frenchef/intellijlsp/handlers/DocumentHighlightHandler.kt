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
import com.intellij.psi.PsiManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * Handles textDocument/documentHighlight requests.
 * Highlights all occurrences of a symbol in the current document.
 */
class DocumentHighlightHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<DocumentHighlightHandler>()
    private val gson = Gson()

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/documentHighlight", this::handleDocumentHighlight)
    }

    /**
     * Handle textDocument/documentHighlight request.
     */
    private fun handleDocumentHighlight(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val positionParams = gson.fromJson(params, TextDocumentPositionParams::class.java)
            val uri = positionParams.textDocument.uri
            val position = positionParams.position
            
            log.debug("Document highlight requested for $uri at line ${position.line}, char ${position.character}")
            
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
            
            // Only highlight identifiers, not keywords or punctuation
            if (!isIdentifierElement(element)) {
                log.debug("Element is not an identifier, skipping highlight")
                return null
            }
            
            val highlights = getDocumentHighlights(element, psiFile)
            
            if (highlights.isEmpty()) {
                log.debug("No highlights found")
                return null
            }
            
            log.info("Found ${highlights.size} highlight(s) for element at line ${position.line}, char ${position.character}")
            highlights.forEachIndexed { index, highlight ->
                log.info("  Highlight ${index + 1}: range=(${highlight.range.start.line}:${highlight.range.start.character} to ${highlight.range.end.line}:${highlight.range.end.character})")
            }
            
            return gson.toJsonTree(highlights)
            
        } catch (e: Exception) {
            log.error("Error handling document highlight", e)
            return null
        }
    }

    /**
     * Get all highlights for a PSI element within the current file.
     */
    private fun getDocumentHighlights(
        element: com.intellij.psi.PsiElement,
        psiFile: com.intellij.psi.PsiFile
    ): List<DocumentHighlight> {
        return ReadAction.compute<List<DocumentHighlight>, RuntimeException> {
            val highlights = mutableListOf<DocumentHighlight>()
            
            try {
                log.info("Getting highlights for element: ${element.javaClass.simpleName}, text='${element.text?.take(50)}'")
                
                // Walk up from LeafPsiElement to find a meaningful parent
                var workingElement = element
                if (element.javaClass.simpleName == "LeafPsiElement") {
                    var parent = element.parent
                    while (parent != null && parent != psiFile) {
                        val className = parent.javaClass.simpleName
                        // Stop at meaningful Kotlin/Java elements
                        if (className.startsWith("Kt") && !className.endsWith("File") ||
                            className.startsWith("Psi") && className != "PsiFile" && className != "PsiWhiteSpace"
                        ) {
                            log.info("Walking up to parent: $className")
                            workingElement = parent
                            break
                        }
                        parent = parent.parent
                    }
                }
                
                log.info("Working element: ${workingElement.javaClass.simpleName}, text='${workingElement.text?.take(50)}'")
                
                // Get the target element (what this element refers to)
                val resolved = workingElement.reference?.resolve()
                val targetElement = if (resolved != null) {
                    log.info("Resolved reference to: ${resolved.javaClass.simpleName}")
                    resolved
                } else {
                    log.info("No reference to resolve, using working element as-is")
                    workingElement
                }
                
                log.info("Target element: ${targetElement.javaClass.simpleName}, text='${targetElement.text?.take(50)}'")
                
                val document = psiFile.viewProvider.document ?: return@compute highlights
                
                // Add highlight for the declaration if it's in this file
                if (targetElement.containingFile == psiFile) {
                    // Try to get the name identifier instead of the entire element
                    val nameElement = try {
                        // For Kotlin elements (KtNamedDeclaration), get the name identifier
                        val nameIdentifierMethod = targetElement.javaClass.getMethod("getNameIdentifier")
                        nameIdentifierMethod.invoke(targetElement) as? com.intellij.psi.PsiElement
                    } catch (e: Exception) {
                        null
                    } ?: try {
                        // For Java elements (PsiNamedElement), get the name identifier
                        val nameIdentifierMethod = targetElement.javaClass.getMethod("getNameIdentifier")
                        nameIdentifierMethod.invoke(targetElement) as? com.intellij.psi.PsiElement
                    } catch (e: Exception) {
                        null
                    } ?: targetElement
                    
                    val textRange = nameElement.textRange
                    if (textRange != null) {
                        val range = PsiMapper.textRangeToRange(document, textRange)
                        highlights.add(DocumentHighlight(range, kind = DocumentHighlightKind.TEXT))
                        log.info("Added highlight for declaration at line ${range.start.line}, text='${nameElement.text}'")
                    }
                }
                
                // Find all references within the current file
                val searchScope = LocalSearchScope(psiFile)
                val query = ReferencesSearch.search(targetElement, searchScope)
                val allReferences = query.findAll()
                log.info("Found ${allReferences.size} reference(s) in file")
                
                for ((index, reference) in allReferences.withIndex()) {
                    val refElement = reference.element
                    val textRange = refElement.textRange
                    
                    if (textRange != null) {
                        // For simplicity, use TEXT kind for all highlights
                        // Could be enhanced to detect READ vs WRITE based on context
                        val range = PsiMapper.textRangeToRange(document, textRange)
                        highlights.add(DocumentHighlight(range, kind = DocumentHighlightKind.TEXT))
                        log.info("Added highlight ${index + 1} at line ${range.start.line}, text='${refElement.text?.take(50)}'")
                    }
                }
                
            } catch (e: Exception) {
                log.warn("Error getting document highlights", e)
            }
            
            highlights
        }
    }

    /**
     * Check if an element is an identifier that should be highlighted.
     * Filters out keywords, punctuation, and other non-identifier elements.
     */
    private fun isIdentifierElement(element: com.intellij.psi.PsiElement): Boolean {
        // Check the element's token type - this is more reliable than text matching
        val elementType = element.node?.elementType?.toString() ?: ""
        
        // IntelliJ uses specific token types for identifiers vs keywords
        // Identifiers typically have "IDENTIFIER" in their type name
        // Keywords have "KEYWORD" or specific keyword names
        if (elementType.contains("IDENTIFIER", ignoreCase = true)) {
            log.info("Element is an identifier: type=$elementType, text='${element.text}'")
            return true
        }
        
        log.info("Element is not an identifier: type=$elementType, text='${element.text}'")
        return false
    }
}

