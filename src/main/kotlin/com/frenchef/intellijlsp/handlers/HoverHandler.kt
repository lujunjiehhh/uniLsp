package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.config.LspSettings
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.Hover
import com.frenchef.intellijlsp.protocol.models.MarkupContent
import com.frenchef.intellijlsp.protocol.models.MarkupKind
import com.frenchef.intellijlsp.protocol.models.TextDocumentPositionParams
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiManager

/**
 * Handles textDocument/hover requests.
 * Provides documentation and type information when hovering over code.
 */
class HoverHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    private val log = logger<HoverHandler>()
    private val gson = LspGson.instance

    fun register() {
        jsonRpcHandler.registerRequestHandler("textDocument/hover", this::handleHover)
    }

    /**
     * Handle textDocument/hover request.
     */
    private fun handleHover(params: JsonElement?): JsonElement? {
        if (params == null || params.isJsonNull) {
            return null
        }

        try {
            val hoverParams = gson.fromJson(params, TextDocumentPositionParams::class.java)
            val uri = hoverParams.textDocument.uri
            val position = hoverParams.position
            
            log.info("Hover requested for $uri at line ${position.line}, char ${position.character}")
            
            val virtualFile = documentManager.getVirtualFile(uri) ?: run {
                log.warn("Virtual file not found for $uri")
                return null
            }
            val document = documentManager.getIntellijDocument(uri) ?: run {
                log.warn("Document not found for $uri")
                return null
            }
            
            val psiFile = ReadAction.compute<com.intellij.psi.PsiFile?, RuntimeException> {
                PsiManager.getInstance(project).findFile(virtualFile)
            } ?: run {
                log.warn("PSI file not found")
                return null
            }
            
            val element = PsiMapper.getPsiElementAtPosition(psiFile, document, position)
            
            if (element == null) {
                log.info("No PSI element found at position")
                return null
            }
            
            log.info("Found PSI element: ${element.javaClass.simpleName} - '${element.text?.take(50)}'")
            log.info("Element details: line=${position.line}, char=${position.character}, text='${element.text?.trim()?.take(100)}'")
            
            // Get documentation using the modern backend API
            val offset = PsiMapper.positionToOffset(document, position)
            val documentation = getDocumentationViaTargets(psiFile, document, offset)
            
            if (documentation.isNullOrBlank()) {
                log.info("No documentation found for element: ${element.javaClass.simpleName}")
                return null
            }
            
            log.info("Returning documentation (${documentation.length} chars): ${documentation.take(100)}...")
            
            val range = PsiMapper.getWordRangeAtPosition(document, position)
            
            val hover = Hover(
                contents = MarkupContent(
                    kind = MarkupKind.MARKDOWN,
                    value = documentation
                ),
                range = range
            )
            
            return gson.toJsonTree(hover)
            
        } catch (e: Exception) {
            log.error("Error handling hover", e)
            return null
        }
    }

    /**
     * Get documentation using the modern backend documentation API (2023.x+).
     * This returns the exact same HTML that IntelliJ's hover shows.
     */
    private fun getDocumentationViaTargets(
        psiFile: com.intellij.psi.PsiFile,
        document: com.intellij.openapi.editor.Document,
        offsetInDocument: Int
    ): String? {
        return ReadAction.compute<String?, RuntimeException> {
            try {
                log.info("Getting documentation via DocumentationTargetProvider at offset $offsetInDocument")
                
                // Get all registered documentation target providers and collect targets from each
                val allTargets = mutableListOf<DocumentationTarget>()
                val providers = DocumentationTargetProvider.EP_NAME.extensionList
                
                log.info("Found ${providers.size} registered DocumentationTargetProvider(s)")
                
                for (provider in providers) {
                    try {
                        // Call documentationTargets with psiFile and offset
                        val targets = provider.documentationTargets(psiFile, offsetInDocument)
                        log.info("Provider ${provider.javaClass.simpleName} returned ${targets.size} target(s)")
                        allTargets.addAll(targets)
                    } catch (e: Exception) {
                        log.warn("Provider ${provider.javaClass.simpleName} failed: ${e.message}")
                    }
                }
                
                log.info("Found ${allTargets.size} documentation target(s) total")
                
                // If no targets found, try PSI-based providers directly on the element at offset
                if (allTargets.isEmpty()) {
                    log.info("No targets from file-based providers, trying PSI-based providers")
                    val leafElement = psiFile.findElementAt(offsetInDocument)
                    
                    if (leafElement != null) {
                        log.info("Found PSI element at offset: ${leafElement.javaClass.simpleName}")
                        
                        // Walk up from LeafPsiElement to find a meaningful parent
                        var element: com.intellij.psi.PsiElement = leafElement
                        if (leafElement.javaClass.simpleName == "LeafPsiElement") {
                            var parent = leafElement.parent
                            while (parent != null && parent != psiFile) {
                                val className = parent.javaClass.simpleName
                                // Stop at meaningful Kotlin/Java elements
                                if (className.startsWith("Kt") && !className.endsWith("File") ||
                                    className.startsWith("Psi") && className != "PsiFile" && className != "PsiWhiteSpace"
                                ) {
                                    log.info("Walking up to parent: $className")
                                    element = parent
                                    break
                                }
                                parent = parent.parent
                            }
                        }
                        
                        log.info("Using PSI element: ${element.javaClass.simpleName}")
                        
                        // Try to resolve references
                        val resolved = element.reference?.resolve()
                        val targetElement = if (resolved != null) {
                            log.info("Resolved reference to: ${resolved.javaClass.simpleName}")
                            resolved
                        } else {
                            log.info("No reference to resolve, using element as-is")
                            element
                        }
                        
                        val psiProviders = PsiDocumentationTargetProvider.EP_NAME.extensionList
                        log.info("Found ${psiProviders.size} PSI-based provider(s)")
                        
                        for (psiProvider in psiProviders) {
                            try {
                                // Pass both the resolved target and original element for context
                                val target = psiProvider.documentationTarget(targetElement, element)
                                if (target != null) {
                                    log.info("PSI provider ${psiProvider.javaClass.simpleName} returned a target")
                                    allTargets.add(target)
                                }
                            } catch (e: Exception) {
                                log.warn("PSI provider ${psiProvider.javaClass.simpleName} failed: ${e.message}")
                            }
                        }
                        log.info("Total targets after PSI providers: ${allTargets.size}")
                    }
                }
                
                for (target in allTargets) {
                    log.info("Processing target: ${target.javaClass.simpleName}")
                    
                    // Try to get full documentation (includes signature + KDoc/JavaDoc)
                    try {
                        val result: DocumentationResult? = target.computeDocumentation()
                        
                        if (result != null) {
                            log.info("Got DocumentationResult: ${result.javaClass.simpleName}")
                            
                            // Extract HTML from the DocumentationData structure
                            // DocumentationResult is typically a DocumentationData with content.html
                            val html = try {
                                // Use reflection to access the html field from DocumentationData
                                val contentField = result.javaClass.getDeclaredField("content")
                                contentField.isAccessible = true
                                val content = contentField.get(result)
                                
                                if (content != null) {
                                    val htmlField = content.javaClass.getDeclaredField("html")
                                    htmlField.isAccessible = true
                                    htmlField.get(content) as? String
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                log.warn("Could not extract HTML from DocumentationResult using reflection: ${e.message}")
                                // Fallback: try toString() but this will include the object structure
                                null
                            }
                            
                            if (!html.isNullOrBlank()) {
                                log.info("Got HTML documentation (${html.length} chars): ${html.take(200)}...")
                                val formattedDoc = formatDocumentation(html)
                                log.info("Returning documentation (${formattedDoc.length} chars)")
                                return@compute formattedDoc
                            } else {
                                log.warn("DocumentationResult HTML is null or blank")
                            }
                        } else {
                            log.info("computeDocumentation returned null")
                        }
                    } catch (e: Exception) {
                        log.warn("computeDocumentation failed: ${e.message}", e)
                    }
                    
                    // Fallback: try the hint if full documentation failed
                    try {
                        val hint = target.computeDocumentationHint()
                        if (hint != null && hint.isNotBlank()) {
                            log.info("Using documentation hint as fallback (${hint.length} chars)")
                            return@compute formatDocumentation(hint)
                        }
                    } catch (e: Exception) {
                        log.warn("computeDocumentationHint failed: ${e.message}")
                    }
                }
                
                log.warn("No documentation found from any target")
                null
            } catch (e: Exception) {
                log.error("Error getting documentation via targets", e)
                null
            }
        }
    }

    /**
     * Format documentation as Markdown.
     */
    private fun formatDocumentation(doc: String): String {
        val settings = LspSettings.getInstance()
        if (settings.hoverFormat == LspSettings.HoverFormat.RAW_HTML) {
            return doc
        }

        // Remove HTML tags and convert to markdown
        var formatted = doc
            .replace(Regex("<br/?>"), "\n")
            .replace(Regex("<p>"), "\n\n")
            .replace(Regex("</p>"), "")
            .replace(Regex("<code>"), "`")
            .replace(Regex("</code>"), "`")
            .replace(Regex("<pre>"), "```\n")
            .replace(Regex("</pre>"), "\n```")
            .replace(Regex("<b>"), "**")
            .replace(Regex("</b>"), "**")
            .replace(Regex("<i>"), "*")
            .replace(Regex("</i>"), "*")
            .replace(Regex("</div>"), "\n\n")
            .replace(Regex("<[^>]+>"), "") // Remove remaining HTML tags
            .trim()
        
        // Decode HTML entities
        formatted = formatted
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x2F;", "/")
            .replace(Regex("&#(\\d+);")) { matchResult ->
                // Decode numeric HTML entities (e.g., &#60; -> <)
                val code = matchResult.groupValues[1].toIntOrNull()
                if (code != null) code.toChar().toString() else matchResult.value
            }
        
        // Clean up multiple spaces and newlines
        formatted = formatted
            .replace(Regex(" +"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        
        // If it looks like code, wrap in code block
        if (!formatted.contains("```") && formatted.contains("(") && formatted.contains(")")) {
            formatted = "```\n$formatted\n```"
        }
        
        return formatted
    }
}

