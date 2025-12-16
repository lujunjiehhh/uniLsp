package com.frenchef.intellijlsp.protocol.models

import com.google.gson.JsonElement

/** Common LSP data types used across the protocol. Based on LSP 3.17 specification. */
data class Position(val line: Int, val character: Int)

data class Range(val start: Position, val end: Position)

data class Location(val uri: String, val range: Range)

data class TextDocumentIdentifier(val uri: String)

data class VersionedTextDocumentIdentifier(val uri: String, val version: Int)

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

data class TextEdit(val range: Range, val newText: String)

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

data class DiagnosticRelatedInformation(val location: Location, val message: String)

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

data class CompletionList(val isIncomplete: Boolean, val items: List<CompletionItem>)

data class Hover(val contents: MarkupContent, val range: Range?)

data class MarkupContent(val kind: MarkupKind, val value: String)

enum class MarkupKind(val value: String) {
    PLAINTEXT("plaintext"),
    MARKDOWN("markdown")
}

data class ReferenceContext(val includeDeclaration: Boolean)

data class ReferenceParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val context: ReferenceContext
)

data class DocumentHighlight(val range: Range, val kind: Int? = null)

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

data class DynamicRegistrationCapability(val dynamicRegistration: Boolean?)

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

data class CompletionItemKindCapabilities(val valueSet: List<Int>?)

data class CodeActionClientCapabilities(
    val dynamicRegistration: Boolean?,
    val codeActionLiteralSupport: CodeActionLiteralSupport?
)

data class CodeActionLiteralSupport(val codeActionKind: CodeActionKindCapabilities)

data class CodeActionKindCapabilities(val valueSet: List<String>)

data class WorkspaceEditCapabilities(val documentChanges: Boolean?)

data class ServerCapabilities(
    val textDocumentSync: TextDocumentSyncOptions?,
    val hoverProvider: Boolean?,
    val completionProvider: CompletionOptions?,
    val signatureHelpProvider: SignatureHelpOptions?,
    val definitionProvider: Boolean?,
    val typeDefinitionProvider: Boolean?, // T031: abcoder 兼容
    val implementationProvider: Boolean?, // Phase 9: Go to Implementation
    val referencesProvider: Boolean?,
    val documentHighlightProvider: Boolean?,
    val documentSymbolProvider: Boolean?,
    val workspaceSymbolProvider: Boolean?,
    val codeActionProvider: CodeActionOptions?, // Phase 9+: 支持 resolve
    val codeLensProvider: CodeLensOptions?,
    val documentFormattingProvider: Boolean?,
    val documentRangeFormattingProvider: Boolean?,
    val documentOnTypeFormattingProvider: DocumentOnTypeFormattingOptions?,
    val renameProvider: RenameOptions?, // Phase 10: Rename Refactoring (T006)
    val documentLinkProvider: DocumentLinkOptions?,
    val executeCommandProvider: ExecuteCommandOptions?,
    val semanticTokensProvider: SemanticTokensOptions?, // T033: abcoder 兼容
    val inlayHintProvider: InlayHintOptions?, // Phase 9: Inlay Hints
    val callHierarchyProvider: Boolean?, // Phase 10: Call Hierarchy (T006)
    val typeHierarchyProvider: Boolean?, // Phase 10: Type Hierarchy (T006)
    val diagnosticProvider: DiagnosticOptions?, // LSP 3.17: Pull 模式诊断
    val workspace: WorkspaceCapabilities?, // Phase 10: Workspace Folders (T006)
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

data class SaveOptions(val includeText: Boolean?)

data class CompletionOptions(val resolveProvider: Boolean?, val triggerCharacters: List<String>?)

data class SignatureHelpOptions(val triggerCharacters: List<String>?)

data class CodeLensOptions(val resolveProvider: Boolean?)

data class DocumentOnTypeFormattingOptions(
    val firstTriggerCharacter: String,
    val moreTriggerCharacter: List<String>?
)

data class DocumentLinkOptions(val resolveProvider: Boolean?)

data class ExecuteCommandOptions(val commands: List<String>)

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

data class WorkspaceFolder(val uri: String, val name: String)

data class InitializeResult(val capabilities: ServerCapabilities, val serverInfo: ServerInfo?)

data class ServerInfo(val name: String, val version: String?)

// Document sync params
data class DidOpenTextDocumentParams(val textDocument: TextDocumentItem)

data class DidChangeTextDocumentParams(
    val textDocument: VersionedTextDocumentIdentifier,
    val contentChanges: List<TextDocumentContentChangeEvent>
)

data class DidCloseTextDocumentParams(val textDocument: TextDocumentIdentifier)

data class DidSaveTextDocumentParams(val textDocument: TextDocumentIdentifier, val text: String?)

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

data class CompletionContext(val triggerKind: CompletionTriggerKind, val triggerCharacter: String?)

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

/** 文档符号请求参数 */
data class DocumentSymbolParams(val textDocument: TextDocumentIdentifier)

/** 层级文档符号（LSP 3.17） */
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

/** 扁平符号信息（兼容旧客户端） */
data class SymbolInformation(
    val name: String,
    val kind: SymbolKind,
    val tags: List<Int>?,
    val deprecated: Boolean?,
    val location: Location,
    val containerName: String?
)

/** 符号类型枚举（LSP 3.17） */
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

/** SemanticTokens 服务端能力选项 */
data class SemanticTokensOptions(
    val legend: SemanticTokensLegend,
    val range: Boolean?,
    val full: SemanticTokensFullOptions?
)

/** Full tokens 选项（支持 delta） */
data class SemanticTokensFullOptions(val delta: Boolean?)

/** SemanticTokens Legend（定义 tokenTypes 和 tokenModifiers） */
data class SemanticTokensLegend(val tokenTypes: List<String>, val tokenModifiers: List<String>)

/** SemanticTokens 请求参数（full） */
data class SemanticTokensParams(val textDocument: TextDocumentIdentifier)

/** SemanticTokens 请求参数（range） */
data class SemanticTokensRangeParams(val textDocument: TextDocumentIdentifier, val range: Range)

/** SemanticTokens 响应 */
data class SemanticTokens(val resultId: String?, val data: List<Int>)

/** 标准 Token Types（LSP 3.17） */
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

/** 标准 Token Modifiers（LSP 3.17） */
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

// ============================================================================
// Phase 9: Extended Features - Signature Help
// ============================================================================

/** Signature help represents the signature of something callable. */
data class SignatureHelp(
    /** One or more signatures. */
    val signatures: List<SignatureInformation>,
    /** The active signature. */
    val activeSignature: Int? = null,
    /** The active parameter of the active signature. */
    val activeParameter: Int? = null
)

/** Represents the signature of something callable. */
data class SignatureInformation(
    /** The label of this signature. */
    val label: String,
    /** The human-readable doc-comment of this signature. */
    val documentation: String? = null,
    /** The parameters of this signature. */
    val parameters: List<ParameterInformation>? = null,
    /** The index of the active parameter. */
    val activeParameter: Int? = null
)

/** Represents a parameter of a callable-signature. */
data class ParameterInformation(
    /** The label of this parameter information. */
    val label: String,
    /** The human-readable doc-comment of this parameter. */
    val documentation: String? = null
)

/** Signature help request parameters. */
data class SignatureHelpParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val context: SignatureHelpContext? = null
)

/** Additional information about the context in which a signature help request was triggered. */
data class SignatureHelpContext(
    val triggerKind: Int,
    val triggerCharacter: String? = null,
    val isRetrigger: Boolean,
    val activeSignatureHelp: SignatureHelp? = null
)

/** Signature help trigger kinds. */
object SignatureHelpTriggerKind {
    const val INVOKED = 1
    const val TRIGGER_CHARACTER = 2
    const val CONTENT_CHANGE = 3
}

// ============================================================================
// Phase 9: Extended Features - Workspace Symbol
// ============================================================================

/** Parameters for workspace/symbol request. */
data class WorkspaceSymbolParams(
    /** A query string to filter symbols by. */
    val query: String
)

// SymbolInformation 已在上方定义，用于 documentSymbol 和 workspace/symbol

// ============================================================================
// Phase 9: Extended Features - Formatting
// ============================================================================

/** Parameters for textDocument/formatting request. */
data class DocumentFormattingParams(
    /** The document to format. */
    val textDocument: TextDocumentIdentifier,
    /** The format options. */
    val options: FormattingOptions
)

/** Parameters for textDocument/rangeFormatting request. */
data class DocumentRangeFormattingParams(
    /** The document to format. */
    val textDocument: TextDocumentIdentifier,
    /** The range to format. */
    val range: Range,
    /** The format options. */
    val options: FormattingOptions
)

/** Value-object describing what options formatting should use. */
data class FormattingOptions(
    /** Size of a tab in spaces. */
    val tabSize: Int,
    /** Prefer spaces over tabs. */
    val insertSpaces: Boolean,
    /** Trim trailing whitespace on a line. */
    val trimTrailingWhitespace: Boolean? = null,
    /** Insert a newline character at the end of the file if one does not exist. */
    val insertFinalNewline: Boolean? = null,
    /** Trim all newlines after the final newline at the end of the file. */
    val trimFinalNewlines: Boolean? = null
)

// ============================================================================
// Phase 9: Extended Features - Code Actions
// ============================================================================

/** Parameters for textDocument/codeAction request. */
data class CodeActionParams(
    /** The document in which the command was invoked. */
    val textDocument: TextDocumentIdentifier,
    /** The range for which the command was invoked. */
    val range: Range,
    /** Context carrying additional information. */
    val context: CodeActionContext
)

/** Contains additional diagnostic information about the context in which a code action is run. */
data class CodeActionContext(
    /** An array of diagnostics known on the client side overlapping the range. */
    val diagnostics: List<Diagnostic>,
    /** Requested kind of actions to return. */
    val only: List<String>? = null,
    /** The reason why code actions were requested. */
    val triggerKind: Int? = null
)

/** A code action represents a change that can be performed in code. */
data class CodeAction(
    /** A short, human-readable, title for this code action. */
    val title: String,
    /** The kind of the code action. */
    val kind: String? = null,
    /** The diagnostics that this code action resolves. */
    val diagnostics: List<Diagnostic>? = null,
    /** Marks this as a preferred action. */
    val isPreferred: Boolean? = null,
    /** The workspace edit this code action performs. */
    val edit: WorkspaceEdit? = null,
    /** A command this code action executes. */
    val command: Command? = null,
    /** 用于 codeAction/resolve 的数据，存储恢复上下文所需信息 */
    val data: CodeActionData? = null
)

/** CodeAction 的额外数据，用于 codeAction/resolve 存储恢复 IntentionAction 上下文所需的信息 */
data class CodeActionData(
    /** 文件 URI */
    val uri: String,
    /** 光标偏移量 */
    val offset: Int,
    /** IntentionAction 的标题（用于匹配） */
    val actionTitle: String,
    /** 代码操作的类型 */
    val actionType: String = "intention" // "intention" 或 "quickfix"
)

/** Code action kinds (subset for Phase 9: only QuickFix). */
object CodeActionKinds {
    const val QUICK_FIX = "quickfix"
    const val REFACTOR = "refactor"
    const val REFACTOR_EXTRACT = "refactor.extract"
    const val REFACTOR_INLINE = "refactor.inline"
    const val REFACTOR_REWRITE = "refactor.rewrite"
    const val SOURCE = "source"
    const val SOURCE_ORGANIZE_IMPORTS = "source.organizeImports"
}

/** Code action options for server capabilities. */
data class CodeActionOptions(
    /** CodeActionKinds that this server may return. */
    val codeActionKinds: List<String>? = null,
    /** Whether code action supports resolve. */
    val resolveProvider: Boolean? = null
)

/** A workspace edit represents changes to many resources managed in the workspace. */
data class WorkspaceEdit(
    /** Holds changes to existing resources. Map from URI to list of TextEdits. */
    val changes: Map<String, List<TextEdit>>? = null,
    /** Depending on the client capability, document changes may be preferred. */
    val documentChanges: List<TextDocumentEdit>? = null
)

/** Describes textual changes on a single text document. */
data class TextDocumentEdit(
    /** The text document to change. */
    val textDocument: OptionalVersionedTextDocumentIdentifier,
    /** The edits to be applied. */
    val edits: List<TextEdit>
)

/** A text document identifier to optionally denote a specific version of a text document. */
data class OptionalVersionedTextDocumentIdentifier(val uri: String, val version: Int?)

/** A generic command. */
data class Command(
    /** Title of the command, like `save`. */
    val title: String,
    /** The identifier of the actual command handler. */
    val command: String,
    /** Arguments that the command handler should be invoked with. */
    val arguments: List<Any>? = null
)

// ============================================================================
// Phase 9: Extended Features - Inlay Hints
// ============================================================================

/** A parameter literal used in inlay hint requests. */
data class InlayHintParams(
    /** The text document. */
    val textDocument: TextDocumentIdentifier,
    /** The visible document range for which inlay hints should be computed. */
    val range: Range
)

/** Inlay hint information. */
data class InlayHint(
    /** The position of this hint. */
    val position: Position,
    /** The label of this hint. A human readable string. */
    val label: String,
    /** The kind of this hint. */
    val kind: Int? = null,
    /** Optional text edits that are performed when accepting this inlay hint. */
    val textEdits: List<TextEdit>? = null,
    /** The tooltip text when you hover over this item. */
    val tooltip: String? = null,
    /** Render padding before the hint. */
    val paddingLeft: Boolean? = null,
    /** Render padding after the hint. */
    val paddingRight: Boolean? = null
)

/** Inlay hint kinds. */
object InlayHintKinds {
    /** An inlay hint that for a type annotation. */
    const val TYPE = 1

    /** An inlay hint that is for a parameter. */
    const val PARAMETER = 2
}

/** Inlay hint options for server capabilities. */
data class InlayHintOptions(
    /** The server provides support to resolve additional information for an inlay hint item. */
    val resolveProvider: Boolean? = null
)

// ============================================================================
// Phase 10: Rename Refactoring (T001)
// ============================================================================

/** Parameters for textDocument/prepareRename request. */
data class PrepareRenameParams(val textDocument: TextDocumentIdentifier, val position: Position)

/** Result of prepareRename request. */
data class PrepareRenameResult(val range: Range, val placeholder: String)

/** Parameters for textDocument/rename request. */
data class RenameParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val newName: String
)

/** Rename options for server capabilities. */
data class RenameOptions(val prepareProvider: Boolean? = null)

// ============================================================================
// Phase 10: Call Hierarchy (T002)
// ============================================================================

/** Parameters for textDocument/prepareCallHierarchy request. */
data class CallHierarchyPrepareParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position
)

/** Represents a call hierarchy item. */
data class CallHierarchyItem(
    val name: String,
    val kind: SymbolKind,
    val tags: List<Int>? = null,
    val detail: String? = null,
    val uri: String,
    val range: Range,
    val selectionRange: Range,
    val data: JsonElement? = null
)

/** Parameters for callHierarchy/incomingCalls request. */
data class CallHierarchyIncomingCallsParams(val item: CallHierarchyItem)

/** Represents an incoming call. */
data class CallHierarchyIncomingCall(val from: CallHierarchyItem, val fromRanges: List<Range>)

/** Parameters for callHierarchy/outgoingCalls request. */
data class CallHierarchyOutgoingCallsParams(val item: CallHierarchyItem)

/** Represents an outgoing call. */
data class CallHierarchyOutgoingCall(val to: CallHierarchyItem, val fromRanges: List<Range>)

// ============================================================================
// Phase 10: Type Hierarchy (T003)
// ============================================================================

/** Parameters for textDocument/prepareTypeHierarchy request. */
data class TypeHierarchyPrepareParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position
)

/** Represents a type hierarchy item. */
data class TypeHierarchyItem(
    val name: String,
    val kind: SymbolKind,
    val tags: List<Int>? = null,
    val detail: String? = null,
    val uri: String,
    val range: Range,
    val selectionRange: Range,
    val data: JsonElement? = null
)

/** Parameters for typeHierarchy/supertypes request. */
data class TypeHierarchySupertypesParams(val item: TypeHierarchyItem)

/** Parameters for typeHierarchy/subtypes request. */
data class TypeHierarchySubtypesParams(val item: TypeHierarchyItem)

// ============================================================================
// Phase 10: Server-Initiated Edits (T004)
// ============================================================================

/** Parameters for workspace/applyEdit request (Server → Client). */
data class ApplyWorkspaceEditParams(val label: String? = null, val edit: WorkspaceEdit)

/** Result of workspace/applyEdit request. */
data class ApplyWorkspaceEditResult(
    val applied: Boolean,
    val failureReason: String? = null,
    val failedChange: Int? = null
)

// ============================================================================
// Phase 10: Workspace Folders & File Watching (T005)
// ============================================================================

/** Parameters for workspace/didChangeWorkspaceFolders notification. */
data class DidChangeWorkspaceFoldersParams(val event: WorkspaceFoldersChangeEvent)

/** Workspace folders change event. */
data class WorkspaceFoldersChangeEvent(
    val added: List<WorkspaceFolder>,
    val removed: List<WorkspaceFolder>
)

/** Parameters for workspace/didChangeWatchedFiles notification. */
data class DidChangeWatchedFilesParams(val changes: List<FileEvent>)

/** Represents a file event. */
data class FileEvent(val uri: String, val type: Int)

/** File change types. */
object FileChangeType {
    const val CREATED = 1
    const val CHANGED = 2
    const val DELETED = 3
}

/** Workspace capabilities for server. */
data class WorkspaceCapabilities(val workspaceFolders: WorkspaceFoldersServerCapabilities? = null)

/** Workspace folders server capabilities. */
data class WorkspaceFoldersServerCapabilities(
    val supported: Boolean? = null,
    val changeNotifications: Boolean? = null
)

// ============================================================================
// LSP 3.17: Pull 模式诊断 (textDocument/diagnostic)
// ============================================================================

/** textDocument/diagnostic 请求参数 */
data class DocumentDiagnosticParams(
    /** 要获取诊断的文档 */
    val textDocument: TextDocumentIdentifier,
    /** 可选的请求标识符，用于增量更新 */
    val identifier: String? = null,
    /** 上一次请求的 resultId，用于增量更新 */
    val previousResultId: String? = null
)

/** textDocument/diagnostic 响应 - 完整报告 */
data class FullDocumentDiagnosticReport(
    /** 报告类型：固定为 "full" */
    val kind: String = DocumentDiagnosticReportKind.FULL,
    /** 可选的结果标识符，用于后续的增量请求 */
    val resultId: String? = null,
    /** 诊断列表 */
    val items: List<Diagnostic>
)

/** textDocument/diagnostic 响应 - 未改变报告 */
data class UnchangedDocumentDiagnosticReport(
    /** 报告类型：固定为 "unchanged" */
    val kind: String = DocumentDiagnosticReportKind.UNCHANGED,
    /** 结果标识符 */
    val resultId: String
)

/** 诊断报告类型 */
object DocumentDiagnosticReportKind {
    const val FULL = "full"
    const val UNCHANGED = "unchanged"
}

/** workspace/diagnostic 请求参数 */
data class WorkspaceDiagnosticParams(
    /** 可选的请求标识符 */
    val identifier: String? = null,
    /** 之前返回的部分结果 tokens */
    val previousResultIds: List<PreviousResultId>? = null
)

/** 关联文档 URI 和 resultId */
data class PreviousResultId(
    /** 文档 URI */
    val uri: String,
    /** 上一次的 resultId */
    val value: String
)

/** workspace/diagnostic 响应 */
data class WorkspaceDiagnosticReport(
    /** 按文档分组的诊断报告列表 */
    val items: List<WorkspaceDocumentDiagnosticReport>
)

/** 工作区诊断的文档级报告 */
data class WorkspaceDocumentDiagnosticReport(
    /** 报告类型 */
    val kind: String,
    /** 文档 URI */
    val uri: String,
    /** 文档版本 */
    val version: Int? = null,
    /** 结果标识符 */
    val resultId: String? = null,
    /** 诊断列表（仅当 kind == "full" 时） */
    val items: List<Diagnostic>? = null
)

/** 诊断服务端能力选项 */
data class DiagnosticOptions(
    /** 可选的服务端标识符 */
    val identifier: String? = null,
    /** 是否支持跨文档关联诊断 */
    val interFileDependencies: Boolean = false,
    /** 是否支持工作区级诊断 */
    val workspaceDiagnostics: Boolean = false
)
