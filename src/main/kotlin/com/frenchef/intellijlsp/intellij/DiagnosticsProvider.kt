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
                
                // Get highlighting information from the document's markup model
                val markupModel = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(
                    document,
                    project,
                    false
                )
                
                if (markupModel != null) {
                    // Get all range highlighters (these contain error/warning information)
                    val allHighlighters = markupModel.allHighlighters
                    
                    for (highlighter in allHighlighters) {
                        // Get the HighlightInfo from the error stripe tooltip
                        val errorStripeTooltip = highlighter.errorStripeTooltip
                        
                        if (errorStripeTooltip is HighlightInfo) {
                            val diagnostic = highlightInfoToDiagnostic(errorStripeTooltip, document)
                            if (diagnostic != null) {
                                diagnostics.add(diagnostic)
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
            val severity = highlightSeverityToDiagnosticSeverity(info.severity)
            
            // Only include errors, warnings, and weak warnings
            if (severity == null) {
                return null
            }
            
            val startOffset = info.startOffset
            val endOffset = info.endOffset
            
            if (startOffset < 0 || endOffset < 0 || startOffset > document.textLength || endOffset > document.textLength) {
                return null
            }
            
            val range = PsiMapper.textRangeToRange(
                document,
                com.intellij.openapi.util.TextRange(startOffset, endOffset)
            )
            
            return Diagnostic(
                range = range,
                severity = severity,
                code = null,
                source = "IntelliJ",
                message = info.description ?: "Unknown issue"
            )
        } catch (e: Exception) {
            log.warn("Error converting highlight info to diagnostic", e)
            return null
        }
    }

    /**
     * Convert IntelliJ HighlightSeverity to LSP DiagnosticSeverity.
     */
    private fun highlightSeverityToDiagnosticSeverity(severity: HighlightSeverity): DiagnosticSeverity? {
        return when {
            severity == HighlightSeverity.ERROR -> DiagnosticSeverity.ERROR
            severity == HighlightSeverity.WARNING -> DiagnosticSeverity.WARNING
            severity == HighlightSeverity.WEAK_WARNING -> DiagnosticSeverity.INFORMATION
            severity == HighlightSeverity.INFORMATION -> DiagnosticSeverity.HINT
            else -> null // Ignore other severities
        }
    }
}

