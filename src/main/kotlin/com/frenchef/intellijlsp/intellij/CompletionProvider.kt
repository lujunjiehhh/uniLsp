package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Provides code completion by bridging to IntelliJ's completion system.
 * 
 * Note: This is a simplified implementation that provides basic completions.
 * A full implementation would integrate more deeply with IntelliJ's completion system.
 */
class CompletionProvider(private val project: Project) {
    private val log = logger<CompletionProvider>()

    /**
     * Get completion items at a specific position.
     * 
     * This is a basic implementation that returns simple completions.
     * For production use, this should integrate with IntelliJ's completion system.
     */
    fun getCompletions(
        psiFile: PsiFile,
        offset: Int
    ): List<CompletionItem> {
        return ReadAction.compute<List<CompletionItem>, RuntimeException> {
            try {
                val element = psiFile.findElementAt(offset)
                if (element == null) {
                    return@compute emptyList()
                }
                
                // Get basic completions from the PSI context
                getBasicCompletions(element)
            } catch (e: Exception) {
                log.warn("Error getting completions", e)
                emptyList()
            }
        }
    }

    /**
     * Get basic completions from the PSI context.
     * This is a simplified implementation.
     */
    private fun getBasicCompletions(element: PsiElement): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()
        
        try {
            // Get references from the element's scope
            val references = element.references
            
            for (reference in references) {
                val variants = reference.variants
                
                for (variant in variants.take(50)) { // Limit to 50 items per reference
                    if (variant is LookupElement) {
                        items.add(lookupElementToCompletionItem(variant))
                    } else if (variant is PsiElement) {
                        items.add(psiElementToCompletionItem(variant))
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Error getting basic completions", e)
        }
        
        return items.take(100) // Limit total to 100 items
    }

    /**
     * Convert IntelliJ LookupElement to LSP CompletionItem.
     */
    private fun lookupElementToCompletionItem(element: LookupElement): CompletionItem {
        val lookupString = element.lookupString
        
        // Try to get type text if available (it's in the presentation)
        val typeText = try {
            val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
            element.renderElement(presentation)
            presentation.typeText
        } catch (e: Exception) {
            null
        }
        
        return CompletionItem(
            label = lookupString,
            kind = inferCompletionKind(element.psiElement),
            detail = typeText,
            documentation = null,
            sortText = null,
            filterText = lookupString,
            insertText = lookupString,
            insertTextFormat = InsertTextFormat.PLAIN_TEXT,
            textEdit = null,
            additionalTextEdits = null
        )
    }

    /**
     * Convert PSI element to LSP CompletionItem.
     */
    private fun psiElementToCompletionItem(element: PsiElement): CompletionItem {
        val text = element.text?.take(50) ?: "unknown"
        
        return CompletionItem(
            label = text,
            kind = inferCompletionKind(element),
            detail = null,
            documentation = null,
            sortText = null,
            filterText = text,
            insertText = text,
            insertTextFormat = InsertTextFormat.PLAIN_TEXT,
            textEdit = null,
            additionalTextEdits = null
        )
    }

    /**
     * Infer the completion item kind from the PSI element.
     */
    private fun inferCompletionKind(element: PsiElement?): CompletionItemKind {
        if (element == null) {
            return CompletionItemKind.TEXT
        }
        
        return when (element.javaClass.simpleName) {
            "KtClass", "KtObjectDeclaration" -> CompletionItemKind.CLASS
            "KtFunction", "KtNamedFunction" -> CompletionItemKind.FUNCTION
            "KtProperty" -> CompletionItemKind.PROPERTY
            "KtParameter" -> CompletionItemKind.VARIABLE
            "PsiClass" -> CompletionItemKind.CLASS
            "PsiMethod" -> CompletionItemKind.METHOD
            "PsiField" -> CompletionItemKind.FIELD
            "PsiVariable", "PsiLocalVariable" -> CompletionItemKind.VARIABLE
            "PsiPackage" -> CompletionItemKind.MODULE
            else -> CompletionItemKind.TEXT
        }
    }
}

