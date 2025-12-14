package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.Location
import com.frenchef.intellijlsp.protocol.models.Position
import com.frenchef.intellijlsp.protocol.models.Range
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Converts between LSP types and IntelliJ PSI types.
 */
object PsiMapper {
    private val log = logger<PsiMapper>()

    /**
     * Convert LSP Position to IntelliJ offset.
     */
    fun positionToOffset(document: Document, position: Position): Int {
        return ReadAction.compute<Int, RuntimeException> {
            val line = position.line.coerceIn(0, document.lineCount - 1)
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val offset = lineStart + position.character
            
            offset.coerceIn(lineStart, lineEnd)
        }
    }

    /**
     * Convert IntelliJ offset to LSP Position.
     */
    fun offsetToPosition(document: Document, offset: Int): Position {
        return ReadAction.compute<Position, RuntimeException> {
            val line = document.getLineNumber(offset)
            val lineStart = document.getLineStartOffset(line)
            val character = offset - lineStart
            
            Position(line = line, character = character)
        }
    }

    /**
     * Convert IntelliJ TextRange to LSP Range.
     */
    fun textRangeToRange(document: Document, textRange: TextRange): Range {
        return ReadAction.compute<Range, RuntimeException> {
            val start = offsetToPosition(document, textRange.startOffset)
            val end = offsetToPosition(document, textRange.endOffset)
            
            Range(start = start, end = end)
        }
    }

    /**
     * Convert LSP Range to IntelliJ TextRange.
     */
    fun rangeToTextRange(document: Document, range: Range): TextRange {
        return ReadAction.compute<TextRange, RuntimeException> {
            val startOffset = positionToOffset(document, range.start)
            val endOffset = positionToOffset(document, range.end)
            
            TextRange(startOffset, endOffset)
        }
    }

    /**
     * Convert VirtualFile to URI string.
     */
    fun virtualFileToUri(virtualFile: VirtualFile): String {
        return "file://${virtualFile.path}"
    }

    /**
     * Convert PsiElement to LSP Location.
     */
    fun psiElementToLocation(element: PsiElement): Location? {
        return ReadAction.compute<Location?, RuntimeException> {
            val containingFile = element.containingFile ?: return@compute null
            val virtualFile = containingFile.virtualFile ?: return@compute null
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                .getDocument(virtualFile) ?: return@compute null
            
            val textRange = element.textRange ?: return@compute null
            val range = textRangeToRange(document, textRange)
            
            Location(
                uri = virtualFileToUri(virtualFile),
                range = range
            )
        }
    }

    /**
     * Get the PsiElement at a specific position in a file.
     */
    fun getPsiElementAtPosition(psiFile: PsiFile, document: Document, position: Position): PsiElement? {
        return ReadAction.compute<PsiElement?, RuntimeException> {
            val offset = positionToOffset(document, position)
            psiFile.findElementAt(offset)
        }
    }

    /**
     * Get the word range at a position.
     * Useful for hover and other features.
     */
    fun getWordRangeAtPosition(document: Document, position: Position): Range? {
        return ReadAction.compute<Range?, RuntimeException> {
            val offset = positionToOffset(document, position)
            val text = document.text
            
            if (offset < 0 || offset >= text.length) {
                return@compute null
            }
            
            // Find word boundaries
            var start = offset
            var end = offset
            
            // Move start backwards
            while (start > 0 && isWordCharacter(text[start - 1])) {
                start--
            }
            
            // Move end forwards
            while (end < text.length && isWordCharacter(text[end])) {
                end++
            }
            
            if (start >= end) {
                return@compute null
            }
            
            val startPos = offsetToPosition(document, start)
            val endPos = offsetToPosition(document, end)
            
            Range(start = startPos, end = endPos)
        }
    }

    /**
     * Check if a character is part of a word.
     */
    private fun isWordCharacter(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_'
    }

    /**
     * Get PsiFile from URI string.
     */
    fun getPsiFile(project: com.intellij.openapi.project.Project, uri: String): PsiFile? {
        return ReadAction.compute<PsiFile?, RuntimeException> {
            try {
                val path = java.net.URI(uri).path
                val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .findFileByPath(path) ?: return@compute null
                com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
            } catch (e: Exception) {
                log.warn("Failed to get PSI file for URI: $uri", e)
                null
            }
        }
    }

    /**
     * Get Document from PsiFile.
     */
    fun getDocument(psiFile: PsiFile): Document? {
        return ReadAction.compute<Document?, RuntimeException> {
            val virtualFile = psiFile.virtualFile ?: return@compute null
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                .getDocument(virtualFile)
        }
    }

    /**
     * Convert PsiElement to LSP Range within a file.
     */
    fun elementToRange(element: PsiElement, psiFile: PsiFile): Range {
        val document = getDocument(psiFile)
        if (document == null) {
            return Range(Position(0, 0), Position(0, 0))
        }
        val textRange = element.textRange
        if (textRange == null) {
            return Range(Position(0, 0), Position(0, 0))
        }
        // 直接计算，不使用 ReadAction 包装（调用者需在 ReadAction 内调用）
        val startLine = document.getLineNumber(textRange.startOffset)
        val startLineStart = document.getLineStartOffset(startLine)
        val startChar = textRange.startOffset - startLineStart
        
        val endLine = document.getLineNumber(textRange.endOffset)
        val endLineStart = document.getLineStartOffset(endLine)
        val endChar = textRange.endOffset - endLineStart
        
        return Range(
            Position(startLine, startChar),
            Position(endLine, endChar)
        )
    }

    /**
     * Convert PsiElement to LSP Location (alias for psiElementToLocation).
     */
    fun elementToLocation(element: PsiElement): Location? {
        return psiElementToLocation(element)
    }
}

