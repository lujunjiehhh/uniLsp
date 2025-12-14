package com.frenchef.intellijlsp.protocol.models

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Common LSP data types used across the protocol.
 * Based on LSP 3.17 specification.
 */

data class Position(
    val line: Int,
    val character: Int
)

data class Range(
    val start: Position,
    val end: Position
)

data class Location(
    val uri: String,
    val range: Range
)

data class TextDocumentIdentifier(
    val uri: String
)

data class VersionedTextDocumentIdentifier(
    val uri: String,
    val version: Int
)

data class TextDocumentItem(
    val uri: String,
    val languageId: String,
    val version: Int,
    val text: String
)

data class TextDocumentPositionParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position
)

data class TextEdit(
    val range: Range,
    val newText: String
)

data class TextDocumentContentChangeEvent(
    val range: Range? = null,
    val rangeLength: Int? = null,
    val text: String
)

data class Diagnostic(
    val range: Range,
    val severity: DiagnosticSeverity?,
    val code: String?,
    val source: String?,
    val message: String,
    val relatedInformation: List<DiagnosticRelatedInformation>? = null
)

enum class DiagnosticSeverity(val value: Int) {
    ERROR(1),
    WARNING(2),
    INFORMATION(3),
    HINT(4)
}

data class DiagnosticRelatedInformation(
    val location: Location,
    val message: String
)

data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind?,
    val detail: String?,
    val documentation: MarkupContent?,
    val sortText: String?,
    val filterText: String?,
    val insertText: String?,
    val insertTextFormat: InsertTextFormat?,
    val textEdit: TextEdit?,
    val additionalTextEdits: List<TextEdit>?
)

enum class CompletionItemKind(val value: Int) {
    TEXT(1),
    METHOD(2),
    FUNCTION(3),
    CONSTRUCTOR(4),
    FIELD(5),
    VARIABLE(6),
    CLASS(7),
    INTERFACE(8),
    MODULE(9),
    PROPERTY(10),
    UNIT(11),
    VALUE(12),
    ENUM(13),
    KEYWORD(14),
    SNIPPET(15),
    COLOR(16),
    FILE(17),
    REFERENCE(18),
    FOLDER(19),
    ENUM_MEMBER(20),
    CONSTANT(21),
    STRUCT(22),
    EVENT(23),
    OPERATOR(24),
    TYPE_PARAMETER(25)
}

enum class InsertTextFormat(val value: Int) {
    PLAIN_TEXT(1),
    SNIPPET(2)
}

data class CompletionList(
    val isIncomplete: Boolean,
    val items: List<CompletionItem>
)

data class Hover(
    val contents: MarkupContent,
    val range: Range?
)

data class MarkupContent(
    val kind: MarkupKind,
    val value: String
)

enum class MarkupKind(val value: String) {
    PLAINTEXT("plaintext"),
    MARKDOWN("markdown")
}

data class ReferenceContext(
    val includeDeclaration: Boolean
)

data class ReferenceParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val context: ReferenceContext
)

data class DocumentHighlight(
    val range: Range,
    val kind: Int? = null
)

object DocumentHighlightKind {
    const val TEXT = 1
    const val READ = 2
    const val WRITE = 3
}

// Initialization types
data class ClientCapabilities(
    val workspace: WorkspaceClientCapabilities?,
    val textDocument: TextDocumentClientCapabilities?,
    val experimental: JsonElement?
)

data class WorkspaceClientCapabilities(
    val applyEdit: Boolean?,
    val workspaceEdit: WorkspaceEditCapabilities?,
    val didChangeConfiguration: DynamicRegistrationCapability?,
    val didChangeWatchedFiles: DynamicRegistrationCapability?,
    val symbol: DynamicRegistrationCapability?,
    val executeCommand: DynamicRegistrationCapability?
)

data class TextDocumentClientCapabilities(
    val synchronization: TextDocumentSyncClientCapabilities?,
    val completion: CompletionClientCapabilities?,
    val hover: DynamicRegistrationCapability?,
    val signatureHelp: DynamicRegistrationCapability?,
    val references: DynamicRegistrationCapability?,
    val documentHighlight: DynamicRegistrationCapability?,
    val documentSymbol: DynamicRegistrationCapability?,
    val formatting: DynamicRegistrationCapability?,
    val rangeFormatting: DynamicRegistrationCapability?,
    val onTypeFormatting: DynamicRegistrationCapability?,
    val definition: DynamicRegistrationCapability?,
    val codeAction: CodeActionClientCapabilities?,
    val codeLens: DynamicRegistrationCapability?,
    val documentLink: DynamicRegistrationCapability?,
    val rename: DynamicRegistrationCapability?
)

data class DynamicRegistrationCapability(
    val dynamicRegistration: Boolean?
)

data class TextDocumentSyncClientCapabilities(
    val dynamicRegistration: Boolean?,
    val willSave: Boolean?,
    val willSaveWaitUntil: Boolean?,
    val didSave: Boolean?
)

data class CompletionClientCapabilities(
    val dynamicRegistration: Boolean?,
    val completionItem: CompletionItemCapabilities?,
    val completionItemKind: CompletionItemKindCapabilities?,
    val contextSupport: Boolean?
)

data class CompletionItemCapabilities(
    val snippetSupport: Boolean?,
    val commitCharactersSupport: Boolean?,
    val documentationFormat: List<MarkupKind>?,
    val deprecatedSupport: Boolean?,
    val preselectSupport: Boolean?
)

data class CompletionItemKindCapabilities(
    val valueSet: List<Int>?
)

data class CodeActionClientCapabilities(
    val dynamicRegistration: Boolean?,
    val codeActionLiteralSupport: CodeActionLiteralSupport?
)

data class CodeActionLiteralSupport(
    val codeActionKind: CodeActionKindCapabilities
)

data class CodeActionKindCapabilities(
    val valueSet: List<String>
)

data class WorkspaceEditCapabilities(
    val documentChanges: Boolean?
)

data class ServerCapabilities(
    val textDocumentSync: TextDocumentSyncOptions?,
    val hoverProvider: Boolean?,
    val completionProvider: CompletionOptions?,
    val signatureHelpProvider: SignatureHelpOptions?,
    val definitionProvider: Boolean?,
    val typeDefinitionProvider: Boolean?,  // T031: abcoder 兼容
    val referencesProvider: Boolean?,
    val documentHighlightProvider: Boolean?,
    val documentSymbolProvider: Boolean?,
    val workspaceSymbolProvider: Boolean?,
    val codeActionProvider: Boolean?,
    val codeLensProvider: CodeLensOptions?,
    val documentFormattingProvider: Boolean?,
    val documentRangeFormattingProvider: Boolean?,
    val documentOnTypeFormattingProvider: DocumentOnTypeFormattingOptions?,
    val renameProvider: Boolean?,
    val documentLinkProvider: DocumentLinkOptions?,
    val executeCommandProvider: ExecuteCommandOptions?,
    val semanticTokensProvider: SemanticTokensOptions?,  // T033: abcoder 兼容
    val experimental: JsonElement?
)

data class TextDocumentSyncOptions(
    val openClose: Boolean?,
    val change: TextDocumentSyncKind?,
    val willSave: Boolean?,
    val willSaveWaitUntil: Boolean?,
    val save: SaveOptions?
)

enum class TextDocumentSyncKind(val value: Int) {
    NONE(0),
    FULL(1),
    INCREMENTAL(2)
}

data class SaveOptions(
    val includeText: Boolean?
)

data class CompletionOptions(
    val resolveProvider: Boolean?,
    val triggerCharacters: List<String>?
)

data class SignatureHelpOptions(
    val triggerCharacters: List<String>?
)

data class CodeLensOptions(
    val resolveProvider: Boolean?
)

data class DocumentOnTypeFormattingOptions(
    val firstTriggerCharacter: String,
    val moreTriggerCharacter: List<String>?
)

data class DocumentLinkOptions(
    val resolveProvider: Boolean?
)

data class ExecuteCommandOptions(
    val commands: List<String>
)

// Initialize params
data class InitializeParams(
    val processId: Int?,
    val rootPath: String?,
    val rootUri: String?,
    val initializationOptions: JsonElement?,
    val capabilities: ClientCapabilities,
    val trace: String?,
    val workspaceFolders: List<WorkspaceFolder>?
)

data class WorkspaceFolder(
    val uri: String,
    val name: String
)

data class InitializeResult(
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo?
)

data class ServerInfo(
    val name: String,
    val version: String?
)

// Document sync params
data class DidOpenTextDocumentParams(
    val textDocument: TextDocumentItem
)

data class DidChangeTextDocumentParams(
    val textDocument: VersionedTextDocumentIdentifier,
    val contentChanges: List<TextDocumentContentChangeEvent>
)

data class DidCloseTextDocumentParams(
    val textDocument: TextDocumentIdentifier
)

data class DidSaveTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
    val text: String?
)

data class PublishDiagnosticsParams(
    val uri: String,
    val version: Int?,
    val diagnostics: List<Diagnostic>
)

data class CompletionParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val context: CompletionContext?
)

data class CompletionContext(
    val triggerKind: CompletionTriggerKind,
    val triggerCharacter: String?
)

enum class CompletionTriggerKind(val value: Int) {
    INVOKED(1),
    TRIGGER_CHARACTER(2),
    TRIGGER_FOR_INCOMPLETE_COMPLETIONS(3)
}

// ============================================================================
// T031: TypeDefinition 参数 (abcoder 兼容)
// ============================================================================

// TypeDefinitionParams 与 TextDocumentPositionParams 相同，可复用

// ============================================================================
// T032: DocumentSymbol 类型 (abcoder 兼容)
// ============================================================================

/**
 * 文档符号请求参数
 */
data class DocumentSymbolParams(
    val textDocument: TextDocumentIdentifier
)

/**
 * 层级文档符号（LSP 3.17）
 */
data class DocumentSymbol(
    val name: String,
    val detail: String?,
    val kind: SymbolKind,
    val tags: List<Int>?,
    val deprecated: Boolean?,
    val range: Range,
    val selectionRange: Range,
    val children: List<DocumentSymbol>?
)

/**
 * 扁平符号信息（兼容旧客户端）
 */
data class SymbolInformation(
    val name: String,
    val kind: SymbolKind,
    val tags: List<Int>?,
    val deprecated: Boolean?,
    val location: Location,
    val containerName: String?
)

/**
 * 符号类型枚举（LSP 3.17）
 */
enum class SymbolKind(val value: Int) {
    FILE(1),
    MODULE(2),
    NAMESPACE(3),
    PACKAGE(4),
    CLASS(5),
    METHOD(6),
    PROPERTY(7),
    FIELD(8),
    CONSTRUCTOR(9),
    ENUM(10),
    INTERFACE(11),
    FUNCTION(12),
    VARIABLE(13),
    CONSTANT(14),
    STRING(15),
    NUMBER(16),
    BOOLEAN(17),
    ARRAY(18),
    OBJECT(19),
    KEY(20),
    NULL(21),
    ENUM_MEMBER(22),
    STRUCT(23),
    EVENT(24),
    OPERATOR(25),
    TYPE_PARAMETER(26)
}

// ============================================================================
// T033: SemanticTokens 类型 (abcoder 兼容)
// ============================================================================

/**
 * SemanticTokens 服务端能力选项
 */
data class SemanticTokensOptions(
    val legend: SemanticTokensLegend,
    val range: Boolean?,
    val full: SemanticTokensFullOptions?
)

/**
 * Full tokens 选项（支持 delta）
 */
data class SemanticTokensFullOptions(
    val delta: Boolean?
)

/**
 * SemanticTokens Legend（定义 tokenTypes 和 tokenModifiers）
 */
data class SemanticTokensLegend(
    val tokenTypes: List<String>,
    val tokenModifiers: List<String>
)

/**
 * SemanticTokens 请求参数（full）
 */
data class SemanticTokensParams(
    val textDocument: TextDocumentIdentifier
)

/**
 * SemanticTokens 请求参数（range）
 */
data class SemanticTokensRangeParams(
    val textDocument: TextDocumentIdentifier,
    val range: Range
)

/**
 * SemanticTokens 响应
 */
data class SemanticTokens(
    val resultId: String?,
    val data: List<Int>
)

/**
 * 标准 Token Types（LSP 3.17）
 */
object SemanticTokenTypes {
    const val NAMESPACE = "namespace"
    const val TYPE = "type"
    const val CLASS = "class"
    const val ENUM = "enum"
    const val INTERFACE = "interface"
    const val STRUCT = "struct"
    const val TYPE_PARAMETER = "typeParameter"
    const val PARAMETER = "parameter"
    const val VARIABLE = "variable"
    const val PROPERTY = "property"
    const val ENUM_MEMBER = "enumMember"
    const val EVENT = "event"
    const val FUNCTION = "function"
    const val METHOD = "method"
    const val MACRO = "macro"
    const val KEYWORD = "keyword"
    const val MODIFIER = "modifier"
    const val COMMENT = "comment"
    const val STRING = "string"
    const val NUMBER = "number"
    const val REGEXP = "regexp"
    const val OPERATOR = "operator"
    const val DECORATOR = "decorator"
}

/**
 * 标准 Token Modifiers（LSP 3.17）
 */
object SemanticTokenModifiers {
    const val DECLARATION = "declaration"
    const val DEFINITION = "definition"
    const val READONLY = "readonly"
    const val STATIC = "static"
    const val DEPRECATED = "deprecated"
    const val ABSTRACT = "abstract"
    const val ASYNC = "async"
    const val MODIFICATION = "modification"
    const val DOCUMENTATION = "documentation"
    const val DEFAULT_LIBRARY = "defaultLibrary"
}
