package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.FormattingOptions
import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.frenchef.intellijlsp.protocol.models.TextEdit
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * Provides code formatting functionality.
 *
 * This provider uses IntelliJ's CodeStyleManager to format code and computes the diff to return as
 * TextEdits.
 */
class FormattingProvider(private val project: Project) {
    private val log = logger<FormattingProvider>()

    /**
     * Format the entire document.
     *
     * @param psiFile The PSI file to format
     * @param options Formatting options (tab size, insert spaces, etc.)
     * @return List of text edits to apply
     */
    fun formatDocument(psiFile: PsiFile, options: FormattingOptions): List<TextEdit> {
        return try {
            val (document, originalText) = ReadAction.compute<Pair<com.intellij.openapi.editor.Document, String>?, RuntimeException> {
                val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    ?: return@compute null
                Pair(doc, doc.text)
            } ?: return emptyList()

            // Create a copy for formatting
            val formattedText = formatText(psiFile, originalText, 0, originalText.length)

            if (formattedText == originalText) {
                log.debug("No formatting changes needed")
                return emptyList()
            }

            // Compute diff and return as single replace edit
            ReadAction.compute<List<TextEdit>, RuntimeException> {
                computeTextEdits(originalText, formattedText, document)
            }
        } catch (e: Exception) {
            log.warn("Error formatting document", e)
            emptyList()
        }
    }

    /**
     * Format a range of the document.
     *
     * @param psiFile The PSI file to format
     * @param range The range to format
     * @param options Formatting options
     * @return List of text edits to apply
     */
    fun formatRange(psiFile: PsiFile, range: Range, options: FormattingOptions): List<TextEdit> {
        return try {
            val (document, originalText, startOffset, endOffset) = ReadAction.compute<FormatRangeContext?, RuntimeException> {
                val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    ?: return@compute null
                val start = PsiMapper.positionToOffset(doc, range.start)
                val end = PsiMapper.positionToOffset(doc, range.end)
                FormatRangeContext(doc, doc.text, start, end)
            } ?: return emptyList()

            val formattedText = formatText(psiFile, originalText, startOffset, endOffset)

            if (formattedText == originalText) {
                log.debug("No formatting changes needed in range")
                return emptyList()
            }

            ReadAction.compute<List<TextEdit>, RuntimeException> {
                computeTextEdits(originalText, formattedText, document)
            }
        } catch (e: Exception) {
            log.warn("Error formatting range", e)
            emptyList()
        }
    }

    /** Helper data class for formatRange context. */
    private data class FormatRangeContext(
        val document: com.intellij.openapi.editor.Document,
        val originalText: String,
        val startOffset: Int,
        val endOffset: Int
    )

    /** Format text using IntelliJ's code style. */
    private fun formatText(
        psiFile: PsiFile,
        text: String,
        startOffset: Int,
        endOffset: Int
    ): String {
        return ReadAction.compute<String, RuntimeException> {
            try {
                // Create a copy of the file for formatting
                val psiFileFactory = PsiFileFactory.getInstance(project)
                val copyFile =
                    psiFileFactory.createFileFromText(
                        "temp_${psiFile.name}",
                        psiFile.fileType,
                        text
                    )

                // Format the copy
                val codeStyleManager = CodeStyleManager.getInstance(project)
                WriteAction.compute<Unit, RuntimeException> {
                    CommandProcessor.getInstance()
                        .executeCommand(
                            project,
                            {
                                codeStyleManager.reformatText(
                                    copyFile,
                                    startOffset,
                                    endOffset
                                )
                            },
                            "Format Code",
                            null
                        )
                }

                copyFile.text
            } catch (e: Exception) {
                log.warn("Error during formatting", e)
                text
            }
        }
    }

    /**
     * Compute text edits from original to formatted text. For simplicity, returns a single edit
     * that replaces the entire content.
     */
    private fun computeTextEdits(
        original: String,
        formatted: String,
        document: com.intellij.openapi.editor.Document
    ): List<TextEdit> {
        if (original == formatted) {
            return emptyList()
        }

        val lineCount = document.lineCount
        val lastLine = if (lineCount > 0) lineCount - 1 else 0
        val lastChar =
            if (lineCount > 0) {
                document.getLineEndOffset(lastLine) - document.getLineStartOffset(lastLine)
            } else {
                0
            }

        return listOf(
            TextEdit(
                range =
                    Range(
                        start = Position(line = 0, character = 0),
                        end = Position(line = lastLine, character = lastChar)
                    ),
                newText = formatted
            )
        )
    }
}
