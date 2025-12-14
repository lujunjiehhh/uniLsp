package com.frenchef.intellijlsp.intellij

import com.frenchef.intellijlsp.protocol.models.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * T037: 基于 IntelliJ lexer/highlighter 生成 SemanticTokens
 */
class SemanticTokensProvider(
    private val project: Project
) {
    private val log = logger<SemanticTokensProvider>()

    // Token type 到索引的映射
    private val tokenTypeIndex = mapOf(
        SemanticTokenTypes.NAMESPACE to 0,
        SemanticTokenTypes.TYPE to 1,
        SemanticTokenTypes.CLASS to 2,
        SemanticTokenTypes.ENUM to 3,
        SemanticTokenTypes.INTERFACE to 4,
        SemanticTokenTypes.STRUCT to 5,
        SemanticTokenTypes.TYPE_PARAMETER to 6,
        SemanticTokenTypes.PARAMETER to 7,
        SemanticTokenTypes.VARIABLE to 8,
        SemanticTokenTypes.PROPERTY to 9,
        SemanticTokenTypes.ENUM_MEMBER to 10,
        SemanticTokenTypes.FUNCTION to 11,
        SemanticTokenTypes.METHOD to 12,
        SemanticTokenTypes.KEYWORD to 13,
        SemanticTokenTypes.MODIFIER to 14,
        SemanticTokenTypes.COMMENT to 15,
        SemanticTokenTypes.STRING to 16,
        SemanticTokenTypes.NUMBER to 17,
        SemanticTokenTypes.OPERATOR to 18
    )

    fun getSemanticTokens(psiFile: PsiFile): SemanticTokens {
        return try {
            ReadAction.compute<SemanticTokens, Exception> {
                val document = PsiMapper.getDocument(psiFile)
                    ?: return@compute SemanticTokens(null, emptyList())
                
                val tokens = collectTokens(psiFile, document, null)
                val encodedData = encodeTokens(tokens, document)
                
                SemanticTokens(null, encodedData)
            }
        } catch (e: Exception) {
            log.error("Error getting semantic tokens", e)
            SemanticTokens(null, emptyList())
        }
    }

    fun getSemanticTokensRange(psiFile: PsiFile, range: Range): SemanticTokens {
        return try {
            ReadAction.compute<SemanticTokens, Exception> {
                val document = PsiMapper.getDocument(psiFile)
                    ?: return@compute SemanticTokens(null, emptyList())
                
                val tokens = collectTokens(psiFile, document, range)
                val encodedData = encodeTokens(tokens, document)
                
                SemanticTokens(null, encodedData)
            }
        } catch (e: Exception) {
            log.error("Error getting semantic tokens range", e)
            SemanticTokens(null, emptyList())
        }
    }

    private fun collectTokens(psiFile: PsiFile, document: Document, range: Range?): List<TokenInfo> {
        val tokens = mutableListOf<TokenInfo>()
        
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
            psiFile.language, 
            project, 
            psiFile.virtualFile
        ) ?: return tokens

        val text = document.text
        val lexer = highlighter.highlightingLexer
        lexer.start(text)

        while (lexer.tokenType != null) {
            val start = lexer.tokenStart
            val end = lexer.tokenEnd

            if (range != null) {
                val tokenStartPos = PsiMapper.offsetToPosition(document, start)
                if (tokenStartPos.line < range.start.line || 
                    (tokenStartPos.line == range.start.line && tokenStartPos.character < range.start.character)) {
                    lexer.advance()
                    continue
                }
                if (tokenStartPos.line > range.end.line ||
                    (tokenStartPos.line == range.end.line && tokenStartPos.character > range.end.character)) {
                    break
                }
            }

            val keys = highlighter.getTokenHighlights(lexer.tokenType!!)
            val semanticType = mapToSemanticType(lexer.tokenType.toString(), keys.map { it.externalName })
            
            if (semanticType != null) {
                val tokenIndex = tokenTypeIndex[semanticType] ?: 0
                tokens.add(TokenInfo(start, end - start, tokenIndex, 0))
            }

            lexer.advance()
        }

        return tokens
    }

    private fun mapToSemanticType(typeName: String, keyNames: List<String>): String? {
        val lowerTypeName = typeName.lowercase()
        val lowerKeyNames = keyNames.map { it.lowercase() }

        return when {
            lowerKeyNames.any { it.contains("keyword") } || lowerTypeName.contains("keyword") -> SemanticTokenTypes.KEYWORD
            lowerKeyNames.any { it.contains("comment") } || lowerTypeName.contains("comment") -> SemanticTokenTypes.COMMENT
            lowerKeyNames.any { it.contains("string") } || lowerTypeName.contains("string") -> SemanticTokenTypes.STRING
            lowerKeyNames.any { it.contains("number") } || lowerTypeName.contains("number") -> SemanticTokenTypes.NUMBER
            lowerKeyNames.any { it.contains("operator") } || lowerTypeName.contains("operator") -> SemanticTokenTypes.OPERATOR
            lowerKeyNames.any { it.contains("class") } -> SemanticTokenTypes.CLASS
            lowerKeyNames.any { it.contains("interface") } -> SemanticTokenTypes.INTERFACE
            lowerKeyNames.any { it.contains("function") || it.contains("method") } -> SemanticTokenTypes.FUNCTION
            lowerKeyNames.any { it.contains("parameter") } -> SemanticTokenTypes.PARAMETER
            lowerKeyNames.any { it.contains("variable") || it.contains("identifier") } -> SemanticTokenTypes.VARIABLE
            lowerKeyNames.any { it.contains("property") || it.contains("field") } -> SemanticTokenTypes.PROPERTY
            lowerKeyNames.any { it.contains("type") } -> SemanticTokenTypes.TYPE
            else -> null
        }
    }

    private fun encodeTokens(tokens: List<TokenInfo>, document: Document): List<Int> {
        if (tokens.isEmpty()) return emptyList()

        val sortedTokens = tokens.sortedBy { it.offset }
        val result = mutableListOf<Int>()
        
        var prevLine = 0
        var prevChar = 0

        for (token in sortedTokens) {
            val pos = PsiMapper.offsetToPosition(document, token.offset)
            val line = pos.line
            val char = pos.character

            val deltaLine = line - prevLine
            val deltaChar = if (deltaLine == 0) char - prevChar else char

            result.add(deltaLine)
            result.add(deltaChar)
            result.add(token.length)
            result.add(token.tokenType)
            result.add(token.tokenModifiers)

            prevLine = line
            prevChar = char
        }

        return result
    }

    private data class TokenInfo(
        val offset: Int,
        val length: Int,
        val tokenType: Int,
        val tokenModifiers: Int
    )
}
