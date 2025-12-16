package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.config.LspSettings
import com.frenchef.intellijlsp.intellij.DocumentManager
import com.frenchef.intellijlsp.intellij.LspDecompiledUriResolver
import com.frenchef.intellijlsp.intellij.PsiMapper
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.LspGson
import com.frenchef.intellijlsp.protocol.models.Hover
import com.frenchef.intellijlsp.protocol.models.MarkupContent
import com.frenchef.intellijlsp.protocol.models.MarkupKind
import com.frenchef.intellijlsp.protocol.models.TextDocumentPositionParams
import com.frenchef.intellijlsp.util.LspLogger
import com.google.gson.JsonElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider

/**
 * Handles textDocument/hover requests.
 * Provides documentation and type information when hovering over code.
 */
class HoverHandler(
    private val project: Project,
    private val jsonRpcHandler: JsonRpcHandler,
    private val documentManager: DocumentManager
) {
    companion object {
        private const val TAG = "Hover"
    }
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

            LspLogger.info(TAG, "Hover requested for $uri at line ${position.line}, char ${position.character}")

            // 使用统一的 URI 解析器：缓存文件映射回 jar PSI，但使用缓存文件文本算 offset
            val resolved = LspDecompiledUriResolver.resolveForRequest(project, uri) ?: run {
                LspLogger.warn(TAG, "URI 解析失败: $uri")
                return null
            }

            val psiFile = resolved.analysisPsiFile
            val fileText = resolved.fileText

            val offset = PsiMapper.positionToOffset(fileText, position)
            val element = ReadAction.compute<com.intellij.psi.PsiElement?, RuntimeException> {
                psiFile.findElementAt(offset)
            }

            if (element == null) {
                LspLogger.info(TAG, "No PSI element found at position")
                return null
            }

            LspLogger.info(TAG, "Found PSI element: ${element.javaClass.simpleName} - '${element.text?.take(50)}'")

            // Get documentation using the modern backend API
            val documentation = getDocumentationViaTargets(psiFile, offset)

            if (documentation.isNullOrBlank()) {
                LspLogger.info(TAG, "No documentation found for element: ${element.javaClass.simpleName}")
                return null
            }

            LspLogger.info(
                TAG,
                "Returning documentation (${documentation.length} chars): ${documentation.take(100)}..."
            )

            val range = PsiMapper.getWordRangeAtPosition(fileText, position)

            val hover = Hover(
                contents = MarkupContent(
                    kind = MarkupKind.MARKDOWN,
                    value = documentation
                ),
                range = range
            )

            return gson.toJsonTree(hover)

        } catch (e: Exception) {
            LspLogger.error(TAG, "Error handling hover: ${e.message}")
            return null
        }
    }

    /**
     * Get documentation using the modern backend documentation API (2023.x+).
     * This returns the exact same HTML that IntelliJ's hover shows.
     */
    private fun getDocumentationViaTargets(
        psiFile: com.intellij.psi.PsiFile,
        offsetInDocument: Int
    ): String? {
        return ReadAction.compute<String?, RuntimeException> {
            try {
                LspLogger.info(TAG, "Getting documentation via DocumentationTargetProvider at offset $offsetInDocument")
                
                // Get all registered documentation target providers and collect targets from each
                val allTargets = mutableListOf<DocumentationTarget>()
                val providers = DocumentationTargetProvider.EP_NAME.extensionList

                LspLogger.info(TAG, "Found ${providers.size} registered DocumentationTargetProvider(s)")
                
                for (provider in providers) {
                    try {
                        // Call documentationTargets with psiFile and offset
                        val targets = provider.documentationTargets(psiFile, offsetInDocument)
                        LspLogger.info(
                            TAG,
                            "Provider ${provider.javaClass.simpleName} returned ${targets.size} target(s)"
                        )
                        allTargets.addAll(targets)
                    } catch (e: Exception) {
                        LspLogger.warn(TAG, "Provider ${provider.javaClass.simpleName} failed: ${e.message}")
                    }
                }

                LspLogger.info(TAG, "Found ${allTargets.size} documentation target(s) total")
                
                // If no targets found, try PSI-based providers directly on the element at offset
                if (allTargets.isEmpty()) {
                    LspLogger.info(TAG, "No targets from file-based providers, trying PSI-based providers")
                    val leafElement = psiFile.findElementAt(offsetInDocument)
                    
                    if (leafElement != null) {
                        LspLogger.info(TAG, "Found PSI element at offset: ${leafElement.javaClass.simpleName}")
                        
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
                                    LspLogger.info(TAG, "Walking up to parent: $className")
                                    element = parent
                                    break
                                }
                                parent = parent.parent
                            }
                        }

                        LspLogger.info(TAG, "Using PSI element: ${element.javaClass.simpleName}")
                        
                        // Try to resolve references
                        val resolved = element.reference?.resolve()
                        val targetElement = if (resolved != null) {
                            LspLogger.info(TAG, "Resolved reference to: ${resolved.javaClass.simpleName}")
                            resolved
                        } else {
                            LspLogger.info(TAG, "No reference to resolve, using element as-is")
                            element
                        }
                        
                        val psiProviders = PsiDocumentationTargetProvider.EP_NAME.extensionList
                        LspLogger.info(TAG, "Found ${psiProviders.size} PSI-based provider(s)")
                        
                        for (psiProvider in psiProviders) {
                            try {
                                // Pass both the resolved target and original element for context
                                val target = psiProvider.documentationTarget(targetElement, element)
                                if (target != null) {
                                    LspLogger.info(
                                        TAG,
                                        "PSI provider ${psiProvider.javaClass.simpleName} returned a target"
                                    )
                                    allTargets.add(target)
                                }
                            } catch (e: Exception) {
                                LspLogger.warn(
                                    TAG,
                                    "PSI provider ${psiProvider.javaClass.simpleName} failed: ${e.message}"
                                )
                            }
                        }
                        LspLogger.info(TAG, "Total targets after PSI providers: ${allTargets.size}")
                    }
                }
                
                for (target in allTargets) {
                    LspLogger.info(TAG, "Processing target: ${target.javaClass.simpleName}")
                    
                    // Try to get full documentation (includes signature + KDoc/JavaDoc)
                    try {
                        val result: DocumentationResult? = target.computeDocumentation()
                        
                        if (result != null) {
                            LspLogger.info(TAG, "Got DocumentationResult: ${result.javaClass.simpleName}")
                            
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
                                LspLogger.warn(
                                    TAG,
                                    "Could not extract HTML from DocumentationResult using reflection: ${e.message}"
                                )
                                // Fallback: try toString() but this will include the object structure
                                null
                            }
                            
                            if (!html.isNullOrBlank()) {
                                LspLogger.info(
                                    TAG,
                                    "Got HTML documentation (${html.length} chars): ${html.take(200)}..."
                                )
                                val formattedDoc = formatDocumentation(html)
                                LspLogger.info(TAG, "Returning documentation (${formattedDoc.length} chars)")
                                return@compute formattedDoc
                            } else {
                                LspLogger.warn(TAG, "DocumentationResult HTML is null or blank")
                            }
                        } else {
                            LspLogger.info(TAG, "computeDocumentation returned null")
                        }
                    } catch (e: Exception) {
                        LspLogger.warn(TAG, "computeDocumentation failed: ${e.message}")
                    }
                    
                    // Fallback: try the hint if full documentation failed
                    try {
                        val hint = target.computeDocumentationHint()
                        if (hint != null && hint.isNotBlank()) {
                            LspLogger.info(TAG, "Using documentation hint as fallback (${hint.length} chars)")
                            return@compute formatDocumentation(hint)
                        }
                    } catch (e: Exception) {
                        LspLogger.warn(TAG, "computeDocumentationHint failed: ${e.message}")
                    }
                }

                LspLogger.warn(TAG, "No documentation found from any target")
                null
            } catch (e: Exception) {
                LspLogger.error(TAG, "Error getting documentation via targets: ${e.message}")
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

