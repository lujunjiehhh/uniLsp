package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.Diagnostic
import com.frenchef.intellijlsp.protocol.models.DiagnosticSeverity
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * Extracts diagnostics (errors, warnings, etc.) from IntelliJ's code analysis.
 */
class DiagnosticsProvider(private val project: Project) {
    private val log = logger<DiagnosticsProvider>()

    /**
     * Get diagnostics for a file.
     */
    fun getDiagnostics(psiFile: PsiFile, document: Document): List<Diagnostic> {
        return ReadAction.compute<List<Diagnostic>, RuntimeException> {
            try {
                val diagnostics = mutableListOf<Diagnostic>()
                // Track seen diagnostics to avoid duplicates (key: startLine:startChar:endLine:endChar:message:severity)
                val seen = mutableSetOf<String>()
                
                // Get highlighting information from the document's markup model
                val markupModel = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(
                    document,
                    project,
                    false
                )
                
                if (markupModel != null) {
                    // Get all range highlighters (these contain error/warning information)
                    val allHighlighters = markupModel.allHighlighters

                    log.debug("MarkupModel 返回 ${allHighlighters.size} 个 highlighters")
                    
                    for (highlighter in allHighlighters) {
                        // Get the HighlightInfo from the error stripe tooltip
                        val errorStripeTooltip = highlighter.errorStripeTooltip
                        
                        if (errorStripeTooltip is HighlightInfo) {
                            val diagnostic = highlightInfoToDiagnostic(errorStripeTooltip, document)
                            if (diagnostic != null) {
                                // Create deduplication key
                                val key = "${diagnostic.range.start.line}:${diagnostic.range.start.character}:" +
                                        "${diagnostic.range.end.line}:${diagnostic.range.end.character}:" +
                                        "${diagnostic.message}:${diagnostic.severity?.value}"
                                if (seen.add(key)) {
                                    diagnostics.add(diagnostic)
                                } else {
                                    log.debug("过滤重复诊断: L${diagnostic.range.start.line + 1} - ${diagnostic.message}")
                                }
                            }
                        }
                    }
                }
                
                diagnostics
            } catch (e: Exception) {
                log.warn("Error getting diagnostics", e)
                emptyList()
            }
        }
    }

    /**
     * Get diagnostics for a file by URI.
     */
    fun getDiagnosticsByUri(uri: String, virtualFile: VirtualFile, document: Document): List<Diagnostic> {
        return ReadAction.compute<List<Diagnostic>, RuntimeException> {
            try {
                if (!virtualFile.isValid) {
                    return@compute emptyList()
                }
                val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null) {
                    getDiagnostics(psiFile, document)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                log.warn("Error getting diagnostics for $uri", e)
                emptyList()
            }
        }
    }

    /**
     * Convert IntelliJ HighlightInfo to LSP Diagnostic.
     */
    private fun highlightInfoToDiagnostic(info: HighlightInfo, document: Document): Diagnostic? {
        try {
            val severity = highlightSeverityToDiagnosticSeverity(info.severity) ?: return null
            
            // Only include errors, warnings, and weak warnings

            val startOffset = info.startOffset
            val endOffset = info.endOffset
            
            if (startOffset < 0 || endOffset < 0 || startOffset > document.textLength || endOffset > document.textLength) {
                return null
            }
            
            val range = PsiMapper.textRangeToRange(
                document,
                com.intellij.openapi.util.TextRange(startOffset, endOffset)
            )

            // Extract message: prefer description, fallback to toolTip with HTML stripped
            val message = extractMessage(info)
            if (message.isNullOrBlank()) {
                return null // Skip diagnostics with no meaningful message
            }
            
            return Diagnostic(
                range = range,
                severity = severity,
                code = null,
                source = "IntelliJ",
                message = message
            )
        } catch (e: Exception) {
            log.warn("Error converting highlight info to diagnostic", e)
            return null
        }
    }

    /**
     * Extract a user-readable message from HighlightInfo.
     * Tries description first, then toolTip (with HTML stripped).
     */
    private fun extractMessage(info: HighlightInfo): String? {
        // First, try description
        val description = info.description
        if (!description.isNullOrBlank()) {
            return description
        }

        // Fallback to toolTip, strip HTML tags
        val toolTip = info.toolTip
        if (!toolTip.isNullOrBlank()) {
            return toolTip.replace(Regex("<[^>]*>"), "").trim()
        }

        return null
    }

    /**
     * Convert IntelliJ HighlightSeverity to LSP DiagnosticSeverity.
     * Note: HighlightSeverity.INFORMATION is excluded as it typically contains
     * editor UI hints (e.g., "Open in browser") rather than actual diagnostics.
     */
    private fun highlightSeverityToDiagnosticSeverity(severity: HighlightSeverity): DiagnosticSeverity? {
        return when {
            severity == HighlightSeverity.ERROR -> DiagnosticSeverity.ERROR
            severity == HighlightSeverity.WARNING -> DiagnosticSeverity.WARNING
            severity == HighlightSeverity.WEAK_WARNING -> DiagnosticSeverity.INFORMATION
            // INFORMATION severity is excluded - it contains editor hints, not diagnostics
            else -> null // Ignore other severities
        }
    }
}

