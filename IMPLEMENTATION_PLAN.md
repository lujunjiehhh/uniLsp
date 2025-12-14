# IntelliJ LSP Server Plugin - Implementation Plan

## Overview
This document outlines the step-by-step implementation plan for building the IntelliJ LSP server plugin. The implementation is divided into phases, each building upon the previous one.

## Project Structure

```
src/main/kotlin/com/frenchef/intellijlsp/
├── server/
│   ├── LspServer.kt                    # Main server interface
│   ├── TcpLspServer.kt                 # TCP implementation
│   ├── UdsLspServer.kt                 # Unix socket implementation
│   └── LspServerManager.kt             # Server lifecycle manager
├── protocol/
│   ├── JsonRpcHandler.kt               # JSON-RPC message handling
│   ├── MessageReader.kt                # Message parsing
│   ├── MessageWriter.kt                # Message serialization
│   └── models/
│       ├── LspRequest.kt               # LSP request models
│       ├── LspResponse.kt              # LSP response models
│       └── LspNotification.kt          # LSP notification models
├── handlers/
│   ├── LifecycleHandler.kt             # initialize, initialized, shutdown, exit
│   ├── DocumentSyncHandler.kt          # didOpen, didChange, didClose, didSave
│   ├── CompletionHandler.kt            # textDocument/completion
│   ├── HoverHandler.kt                 # textDocument/hover
│   ├── DefinitionHandler.kt            # textDocument/definition
│   ├── ReferencesHandler.kt            # textDocument/references
│   ├── DocumentHighlightHandler.kt     # textDocument/documentHighlight
│   ├── DiagnosticsHandler.kt           # publishDiagnostics
│   ├── TypeDefinitionHandler.kt        # textDocument/typeDefinition
│   ├── DocumentSymbolHandler.kt        # textDocument/documentSymbol
│   └── SemanticTokensHandler.kt        # textDocument/semanticTokens/*
├── intellij/
│   ├── DocumentManager.kt              # Document synchronization with IntelliJ
│   ├── PsiMapper.kt                    # Map PSI elements to LSP types
│   ├── DiagnosticsProvider.kt          # Extract diagnostics from IntelliJ
│   ├── CompletionProvider.kt           # Bridge to IntelliJ completion
│   ├── DocumentSymbolProvider.kt       # Extract document symbols from IntelliJ
│   └── SemanticTokensProvider.kt       # Semantic tokens (full/range)
├── config/
│   ├── LspSettings.kt                  # Plugin settings state
│   ├── LspConfigurable.kt              # Settings UI
│   └── TransportMode.kt                # Enum: TCP/UDS
├── ui/
│   ├── LspStatusWidget.kt              # Status bar widget
│   └── LspToolWindow.kt                # Optional tool window for logs
└── services/
    ├── LspProjectService.kt            # Project-level service
    └── PortAllocator.kt                # Port allocation management
```

## Phase 1: Foundation & Infrastructure

### 1.1 Project Setup
**Goal**: Configure build dependencies and plugin structure

**Tasks**:
- [x] Update `build.gradle.kts`:
  - Add Kotlin coroutines dependency
  - Add any required serialization libraries (or use IntelliJ's built-in Gson)
  - Configure plugin dependencies
- [x] Update `plugin.xml`:
  - Define project service: `LspProjectService`
  - Register startup activity: `LspServerStartupActivity`
  - Register settings: `LspConfigurable`
  - Add status bar widget factory
  - Declare required IntelliJ modules

**Files to modify**:
- `build.gradle.kts`
- `src/main/resources/META-INF/plugin.xml`

### 1.2 Configuration & Settings
**Goal**: Create settings infrastructure

**Tasks**:
- [x] Create `TransportMode` enum (TCP/UDS)
- [x] Create `LspSettings` with persistent state:
  - Transport mode
  - Starting port (default 2087)
  - Auto-start enabled
- [x] Create `LspConfigurable` for settings UI:
  - Radio buttons for transport mode
  - Number field for starting port
  - Display current server status
- [x] Create `PortAllocator` singleton:
  - Track allocated ports across all projects
  - Find next available port starting from configured port

**Files to create**:
- `config/TransportMode.kt`
- `config/LspSettings.kt`
- `config/LspConfigurable.kt`
- `services/PortAllocator.kt`

### 1.3 JSON-RPC Protocol Layer
**Goal**: Implement JSON-RPC 2.0 message handling

**Tasks**:
- [x] Create `MessageReader`:
  - Parse Content-Length header
  - Read JSON payload
  - Handle malformed messages
- [x] Create `MessageWriter`:
  - Format messages with Content-Length header
  - Serialize JSON responses
  - Buffer management
- [x] Create LSP data models:
  - `LspRequest` (id, method, params)
  - `LspResponse` (id, result, error)
  - `LspNotification` (method, params)
  - Common LSP types (Position, Range, TextDocumentIdentifier, etc.)
- [x] Create `JsonRpcHandler`:
  - Route requests to appropriate handlers
  - Handle errors and generate error responses
  - Support batch requests

**Files to create**:
- `protocol/MessageReader.kt`
- `protocol/MessageWriter.kt`
- `protocol/models/LspRequest.kt`
- `protocol/models/LspResponse.kt`
- `protocol/models/LspNotification.kt`
- `protocol/models/LspTypes.kt`
- `protocol/JsonRpcHandler.kt`

## Phase 2: Server Infrastructure

### 2.1 TCP Server Implementation
**Goal**: Create TCP-based LSP server

**Tasks**:
- [x] Create `LspServer` interface:
  - `start()`, `stop()`, `isRunning()`
  - `getPort()` or `getSocketPath()`
  - `onClientConnected()`, `onClientDisconnected()`
- [x] Create `TcpLspServer`:
  - Bind to localhost on allocated port
  - Accept client connections
  - Handle multiple concurrent clients
  - Use coroutines for async I/O
  - Integrate `MessageReader` and `MessageWriter`
  - Route messages to `JsonRpcHandler`

**Files to create**:
- `server/LspServer.kt`
- `server/TcpLspServer.kt`

### 2.2 Unix Domain Socket Server Implementation
**Goal**: Create UDS-based LSP server

**Tasks**:
- [x] Create `UdsLspServer`:
  - Create socket file in `~/.intellij-lsp/`
  - Set proper permissions (0600)
  - Accept client connections
  - Similar structure to `TcpLspServer`
  - Clean up socket file on shutdown

**Files to create**:
- `server/UdsLspServer.kt`

### 2.3 Server Lifecycle Management
**Goal**: Manage server per project lifecycle

**Tasks**:
- [x] Create `LspServerManager`:
  - Factory method to create server based on transport mode
  - Start server with error handling
  - Stop server and cleanup resources
- [x] Create `LspProjectService`:
  - Project-level service
  - Hold reference to `LspServer` instance
  - Expose server status
  - Implement `Disposable` for cleanup
- [x] Create startup activity:
  - Implement `StartupActivity` or `ProjectActivity`
  - Start LSP server when project opens
  - Log server status and port/socket path
- [x] Create project close listener (or rely on `Disposable` cleanup):
  - Stop server when project closes
  - Release port allocation
  - Clean up resources

**Files to create**:
- `server/LspServerManager.kt`
- `services/LspProjectService.kt`
- `LspServerStartupActivity.kt`

## Phase 3: LSP Base Protocol Handlers

### 3.1 Lifecycle Handler
**Goal**: Implement initialize, initialized, shutdown, exit

**Tasks**:
- [x] Create `LifecycleHandler`:
  - Handle `initialize` request:
    - Parse client capabilities
    - Return server capabilities
    - Store initialization params
  - Handle `initialized` notification
  - Handle `shutdown` request:
    - Prepare for shutdown
    - Return null result
  - Handle `exit` notification:
    - Stop server
    - Dispose resources

**Server Capabilities to advertise**:
- `textDocumentSync`: Incremental
- `hoverProvider`: true
- `definitionProvider`: true
- `typeDefinitionProvider`: true (abcoder 强依赖)
- `completionProvider`: with trigger characters
- `referencesProvider`: true
- `documentHighlightProvider`: true
- `documentSymbolProvider`: true (abcoder 强依赖)
- `semanticTokensProvider`: legend(tokenTypes/tokenModifiers 必须), range 可选但建议支持 (abcoder 强依赖)

**Files to create**:
- `handlers/LifecycleHandler.kt`

### 3.2 与 abcoder（UniAST）兼容性（必须）
**Goal**: 满足 abcoder 在 `initialize` 阶段对 capabilities 的强校验，避免“初始化直接失败”。

**abcoder 强校验的最小可用清单（本项目现状）**:
- [x] `initialize` / `initialized`
- [x] `textDocument/didOpen`
- [x] `textDocument/definition`
- [ ] `textDocument/typeDefinition`
- [ ] `textDocument/documentSymbol`
- [ ] `textDocument/semanticTokens/full`
- [ ] `textDocument/semanticTokens/range`
- [x] `textDocument/references`

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `ServerCapabilities.typeDefinitionProvider`
  - Add `ServerCapabilities.semanticTokensProvider` (with `legend.tokenTypes`/`legend.tokenModifiers`)
  - Add DocumentSymbol 相关模型与参数（`DocumentSymbol`/`SymbolKind`/`DocumentSymbolParams` 等）
  - Add SemanticTokens 相关模型与参数（`SemanticTokens`/`SemanticTokensLegend`/`SemanticTokensParams`/`SemanticTokensRangeParams` 等）
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `documentSymbolProvider = true`
  - Advertise `typeDefinitionProvider = true`
  - Advertise `semanticTokensProvider`（legend 必须；range 建议支持）

## Phase 4: Document Synchronization

### 4.1 Document Manager
**Goal**: Synchronize documents between LSP client and IntelliJ

**Tasks**:
- [x] Create `DocumentManager`:
  - Maintain map of URI → Document version
  - Open document: Store URI and initial content
  - Track document versions
  - Apply incremental changes
  - Close document: Remove from tracking
  - Save document: Trigger IntelliJ file refresh

**Files to create**:
- `intellij/DocumentManager.kt`

### 4.2 Document Sync Handler
**Goal**: Implement didOpen, didChange, didClose, didSave

**Tasks**:
- [x] Create `DocumentSyncHandler`:
  - Handle `textDocument/didOpen`:
    - Register document with `DocumentManager`
    - Load or create PSI file in IntelliJ
    - Trigger initial diagnostics
  - Handle `textDocument/didChange`:
    - Apply incremental changes
    - Update document version
    - Note: Lower priority to sync to IntelliJ immediately
  - Handle `textDocument/didClose`:
    - Unregister document
    - Clean up resources
  - Handle `textDocument/didSave`:
    - Trigger IntelliJ VFS refresh
    - Wait for IntelliJ to re-parse file
    - Publish updated diagnostics

**Files to create**:
- `handlers/DocumentSyncHandler.kt`

## Phase 5: Core Language Features

### 5.1 Hover Support
**Goal**: Implement textDocument/hover

**Tasks**:
- [x] Create `PsiMapper`:
  - Convert LSP Position to IntelliJ offset
  - Convert IntelliJ ranges to LSP ranges
  - URI ↔ VirtualFile conversion
- [x] Create `HoverHandler`:
  - Get PSI element at position
  - Use `DocumentationProvider` to get documentation
  - Extract quick info (type, signature)
  - Format as Markdown
  - Return `Hover` response

**Files to create**:
- `intellij/PsiMapper.kt`
- `handlers/HoverHandler.kt`

### 5.2 Go to Definition
**Goal**: Implement textDocument/definition

**Tasks**:
- [x] Create `DefinitionHandler`:
  - Get PSI element at position
  - Resolve references using `PsiReference`
  - Get target element location
  - Convert to LSP Location
  - Handle multiple definitions

**Files to create**:
- `handlers/DefinitionHandler.kt`

### 5.3 Code Completion
**Goal**: Implement textDocument/completion

**Tasks**:
- [x] Create `CompletionProvider`:
  - Bridge to IntelliJ's `CompletionService`
  - Invoke completion at position
  - Extract completion items
  - Map to LSP CompletionItem
- [x] Create `CompletionHandler`:
  - Handle `textDocument/completion` request
  - Use `CompletionProvider`
  - Return CompletionList or CompletionItem[]
  - Support trigger characters (., ::, etc.)

**Files to create**:
- `intellij/CompletionProvider.kt`
- `handlers/CompletionHandler.kt`

### 5.4 Find References
**Goal**: Implement textDocument/references

**Tasks**:
- [x] Create `ReferencesHandler`:
  - Get PSI element at position
  - Use `ReferencesSearch` to find usages
  - Collect all references
  - Convert to LSP Location[]
  - Support include declaration flag

**Files to create**:
- `handlers/ReferencesHandler.kt`

### 5.5 Document Highlight
**Goal**: Implement textDocument/documentHighlight

**Tasks**:
- [x] Create `DocumentHighlightHandler`:
  - Get PSI element at position
  - Resolve reference target when possible
  - Search references in current file (LocalSearchScope)
  - Convert to LSP `DocumentHighlight[]`

**Files to create**:
- `handlers/DocumentHighlightHandler.kt`

### 5.6 Type Definition（abcoder 必须）
**Goal**: Implement textDocument/typeDefinition

**Tasks**:
- [ ] Create `TypeDefinitionHandler`:
  - Get PSI element at position
  - 解析“符号的类型”并跳转到类型定义位置（优先复用 IntelliJ 现有的 *Go to Type Declaration* 逻辑）
  - Convert to LSP Location / Location[]
  - 处理多目标（例如类型别名/多重上界等）
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建并 `register()` `TypeDefinitionHandler`

**Implementation Notes**:
- 优先走 IntelliJ 平台的导航/解析能力（而不是自行解析类型系统），保证跨语言/跨框架可用性。
- 如果导航 API 需要 `Editor`，可基于 `DocumentManager.getIntellijDocument()` 创建临时 `Editor` 并确保释放。

**Files to create**:
- `handlers/TypeDefinitionHandler.kt`

### 5.7 Document Symbols（abcoder 必须）
**Goal**: Implement textDocument/documentSymbol

**Tasks**:
- [ ] Create `DocumentSymbolProvider`:
  - 从 IntelliJ 的结构视图/PSI 生成层级 `DocumentSymbol` 树
  - 生成 `range` 与 `selectionRange`（优先使用 nameIdentifier 的 TextRange）
  - 对不支持结构视图的语言降级为扁平列表
- [ ] Create `DocumentSymbolHandler`:
  - Handle `textDocument/documentSymbol`
  - Convert to LSP `DocumentSymbol[]`（或兼容返回 `SymbolInformation[]`）
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建 `DocumentSymbolProvider` 并注入 `DocumentSymbolHandler`
  - 调用 `DocumentSymbolHandler.register()`

**Files to create**:
- `intellij/DocumentSymbolProvider.kt`
- `handlers/DocumentSymbolHandler.kt`

### 5.8 Semantic Tokens（abcoder 必须）
**Goal**: Implement textDocument/semanticTokens/full + textDocument/semanticTokens/range

**Tasks**:
- [ ] Define a stable `SemanticTokensLegend`:
  - `tokenTypes`: 使用 LSP 标准 token types 子集（保持跨语言一致）
  - `tokenModifiers`: 使用常用 modifiers（`declaration`/`definition`/`readonly` 等）
- [ ] Create `SemanticTokensProvider`:
  - 基于 IntelliJ 的 lexer/highlighter + PSI（best-effort）生成 tokens
  - 以 document version 为 key 缓存 full tokens，并支持按 range 切片
  - 输出遵循 LSP 的 delta-encoding `data: IntArray`
- [ ] Create `SemanticTokensHandler`:
  - Handle `textDocument/semanticTokens/full`
  - Handle `textDocument/semanticTokens/range`
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建 `SemanticTokensProvider` 并注入 `SemanticTokensHandler`
  - 调用 `SemanticTokensHandler.register()`

**Files to create**:
- `intellij/SemanticTokensProvider.kt`
- `handlers/SemanticTokensHandler.kt`

## Phase 6: Diagnostics

### 6.1 Diagnostics Provider
**Goal**: Extract diagnostics from IntelliJ

**Tasks**:
- [x] Create `DiagnosticsProvider`:
  - Subscribe to `DaemonCodeAnalyzer` updates
  - Query `WolfTheProblemSolver` for problems
  - Extract `HighlightInfo` for file
  - Convert severity (ERROR, WARNING, INFO)
  - Map to LSP Diagnostic

**Files to create**:
- `intellij/DiagnosticsProvider.kt`

### 6.2 Diagnostics Handler
**Goal**: Publish diagnostics to client

**Tasks**:
- [x] Create `DiagnosticsHandler`:
  - Listen to file analysis completion
  - Get diagnostics from `DiagnosticsProvider`
  - Send `textDocument/publishDiagnostics` notification
  - Debounce updates to avoid spam
  - Clear diagnostics on file close

**Files to create**:
- `handlers/DiagnosticsHandler.kt`

## Phase 7: UI & Status

### 7.1 Status Bar Widget
**Goal**: Display server status in IntelliJ

**Tasks**:
- [x] Create `LspStatusWidget`:
  - Implement `StatusBarWidget`
  - Display server state (Running/Stopped)
  - Show port number (TCP mode)
  - Show socket path (UDS mode)
  - Show connected client count
  - Click to open settings or tool window

**Files to create**:
- `ui/LspStatusWidget.kt`

### 7.2 Optional Tool Window
**Goal**: Show logs and connection details

**Tasks**:
- [ ] Create `LspToolWindow` (optional):
  - Display server logs
  - Show connected clients
  - Manual start/stop button
  - Copy port/socket path to clipboard

**Files to create**:
- `ui/LspToolWindow.kt` (optional)

## Phase 8: Testing & Polish

### 8.1 Unit Tests
**Goal**: Test core components

**Tasks**:
- [ ] Test `MessageReader` and `MessageWriter`
- [ ] Test `JsonRpcHandler` routing
- [ ] Test `PortAllocator` logic
- [ ] Test LSP data model serialization

### 8.2 Integration Tests
**Goal**: Test IntelliJ integration

**Tasks**:
- [ ] Test document synchronization
- [ ] Test hover with sample code
- [ ] Test completion with sample code
- [ ] Test definition/references with sample code

### 8.3 Manual Testing with Neovim
**Goal**: End-to-end testing

**Tasks**:
- [ ] Configure Neovim LSP client
- [ ] Test all implemented features
- [ ] Test multiple project windows
- [ ] Test reconnection scenarios
- [ ] Test error handling

### 8.4 Documentation
**Goal**: Update README and plugin description

**Tasks**:
- [ ] Write README with usage instructions
- [ ] Document Neovim configuration
- [ ] Update plugin.xml description
- [ ] Add troubleshooting guide

## Implementation Notes

### Threading Considerations
- Use IntelliJ's `ApplicationManager.getApplication().executeOnPooledThread()` for background tasks
- Use `ReadAction.run()` for PSI access
- Use coroutines for async I/O operations
- Ensure proper synchronization for shared state

### Error Handling
- Wrap all handler logic in try-catch
- Return LSP error responses with appropriate codes
- Log errors to IntelliJ's log
- Don't crash server on handler errors

### Performance
- Debounce diagnostics updates (e.g., 500ms)
- Limit completion results (e.g., 100 items)
- Cancel long-running operations on new requests
- Use caching where appropriate

### Dependencies to Add

**build.gradle.kts**:
```kotlin
dependencies {
    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")
    
    // Use IntelliJ's built-in Gson for JSON
    // No additional JSON library needed
}
```

## Timeline Estimate

- **Phase 1**: 2-3 days (Foundation)
- **Phase 2**: 3-4 days (Server infrastructure)
- **Phase 3**: 1-2 days (Base protocol)
- **Phase 4**: 2-3 days (Document sync)
- **Phase 5**: 4-5 days (Core features)
- **Phase 6**: 2-3 days (Diagnostics)
- **Phase 7**: 1-2 days (UI)
- **Phase 8**: 2-3 days (Testing & polish)

**Total**: ~17-25 days of focused development

## Future Enhancements (Post-MVP)

### Phase 9: Extended Features

#### 9.1 Workspace Symbols（`workspace/symbol`）
**Goal**: Implement workspace-wide symbol search

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `WorkspaceSymbolParams`（`query` 等）
  - Add `SymbolInformation`（或 `WorkspaceSymbol`）相关模型
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `workspaceSymbolProvider = true`
- [ ] Create `WorkspaceSymbolProvider`:
  - 基于 IntelliJ 索引/搜索能力按 `query` 搜索符号（best-effort）
  - 将结果映射到 LSP `SymbolInformation`（`name/kind/location/containerName`）
  - 限制返回数量，并支持取消（避免全工程扫描卡死）
- [ ] Create `WorkspaceSymbolHandler`:
  - Handle `workspace/symbol`
  - Return `SymbolInformation[]`（优先做兼容性最好的返回类型）
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建 `WorkspaceSymbolProvider` 并注入 `WorkspaceSymbolHandler`
  - 调用 `WorkspaceSymbolHandler.register()`

**Implementation Notes**:
- 优先使用 IntelliJ 平台的索引/搜索入口（而不是自己遍历 PSI）。
- 如果不同语言的 symbol kind 映射差异较大，可先做保守映射（例如 class/function/variable/module）。

**Files to create**:
- `intellij/WorkspaceSymbolProvider.kt`
- `handlers/WorkspaceSymbolHandler.kt`

#### 9.2 Go to Implementation（`textDocument/implementation`）
**Goal**: Implement “Go to Implementation” navigation

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`:
  - Add `ServerCapabilities.implementationProvider`
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `implementationProvider = true`
- [ ] Create `ImplementationHandler`:
  - Get PSI element at position
  - 对 interface/abstract member 走“查找实现”的搜索逻辑（复用 IntelliJ 的搜索/继承体系能力）
  - Convert to `Location[]`
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建并 `register()` `ImplementationHandler`

**Implementation Notes**:
- 建议优先走 IntelliJ 自带的继承/重写搜索能力，保证 Java ↔ Kotlin 的互操作。

**Files to create**:
- `handlers/ImplementationHandler.kt`

#### 9.3 Code Formatting（`textDocument/formatting` / `textDocument/rangeFormatting`）
**Goal**: Provide formatting edits without直接修改工程文件

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `DocumentFormattingParams` / `DocumentRangeFormattingParams`
  - Add `FormattingOptions`
  - Ensure `TextEdit`/`Range`/`Position` 等模型齐全
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `documentFormattingProvider = true`
  - Advertise `documentRangeFormattingProvider = true`
- [ ] Create `FormattingProvider`:
  - 获取 `VirtualFile`/`Document`/`PsiFile`
  - 在“副本/临时 PSI”上执行格式化（避免直接改动工程）
  - 计算格式化前后文本差异并输出 LSP `TextEdit[]`（最简可先返回“整文件替换”单个 edit）
- [ ] Create `DocumentFormattingHandler`:
  - Handle `textDocument/formatting`
- [ ] Create `DocumentRangeFormattingHandler` (optional):
  - Handle `textDocument/rangeFormatting`
- [ ] Register handlers in startup:
  - 在 `LspServerStartupActivity` 中创建 provider 并注册 handler

**Implementation Notes**:
- 由于本项目的文档同步目前是“客户端 -> 服务端”为主，格式化建议先以“返回 edits”方式实现。
- 若后续支持服务端向客户端 `workspace/applyEdit`，可进一步把格式化/重构变成 server-driven。

**Files to create**:
- `intellij/FormattingProvider.kt`
- `handlers/DocumentFormattingHandler.kt`
- `handlers/DocumentRangeFormattingHandler.kt` (optional)

#### 9.4 Code Actions（`textDocument/codeAction`）
**Goal**: Expose IntelliJ quick fixes / intentions as LSP CodeAction

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `CodeActionParams` / `CodeActionContext`
  - Add `CodeAction` / `Command`
  - Add `WorkspaceEdit` 相关模型（供“直接返回 edits”的 code action 使用）
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `codeActionProvider = true`
  - （可选）若采用 command 执行意图：Advertise `executeCommandProvider`
- [ ] Create `CodeActionProvider`:
  - 基于 IntelliJ intention/quickfix 体系收集当前 range 的候选 action（best-effort）
  - 映射到 LSP `CodeAction[]`
  - 初版建议：先提供“可安全表达为 WorkspaceEdit 的 action”；复杂 action 先不暴露
- [ ] Create `CodeActionHandler`:
  - Handle `textDocument/codeAction`
- [ ] Create `ExecuteCommandHandler` (optional):
  - Handle `workspace/executeCommand`（用于执行部分 server-side command）
- [ ] Register handlers in startup:
  - 在 `LspServerStartupActivity` 中创建 provider 并注册 handler

**Implementation Notes**:
- codeAction 落地难点在于：IntelliJ 的“意图/快速修复”很多是“直接修改 PSI/Document”的写操作。
- 为保证 LSP 一致性，建议优先实现“可转成 WorkspaceEdit 并由客户端应用”的那一类。

**Files to create**:
- `intellij/CodeActionProvider.kt`
- `handlers/CodeActionHandler.kt`
- `handlers/ExecuteCommandHandler.kt` (optional)

#### 9.5 Signature Help（`textDocument/signatureHelp`）
**Goal**: Provide parameter info at call site

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `SignatureHelp` / `SignatureInformation` / `ParameterInformation`
  - Add `SignatureHelpParams`
  - Add `SignatureHelpOptions`（triggerCharacters 等）
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `signatureHelpProvider`（含 triggerCharacters）
- [ ] Create `SignatureHelpProvider`:
  - 复用 IntelliJ 的参数信息能力（可能需要 `Editor` 上下文）
  - 将参数列表/当前参数索引映射到 LSP
- [ ] Create `SignatureHelpHandler`:
  - Handle `textDocument/signatureHelp`
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建 provider 并注册 handler

**Implementation Notes**:
- 若必须依赖 `Editor`，可创建临时 editor 并确保释放（避免泄漏）。

**Files to create**:
- `intellij/SignatureHelpProvider.kt`
- `handlers/SignatureHelpHandler.kt`

#### 9.6 Inlay Hints（`textDocument/inlayHint`）
**Goal**: Provide inlay hints (types/parameter names) in editor

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `InlayHint` / `InlayHintLabelPart` / `InlayHintParams`
  - Add `InlayHintOptions`
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `inlayHintProvider`
- [ ] Create `InlayHintsProvider`:
  - best-effort 复用 IntelliJ 的 inlay hints 计算结果
  - 映射到 LSP `InlayHint[]`
- [ ] Create `InlayHintsHandler`:
  - Handle `textDocument/inlayHint`
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建 provider 并注册 handler

**Implementation Notes**:
- inlay hints 在不同语言插件实现差异较大，建议先从 Java/Kotlin 的常见 hints 子集开始。

**Files to create**:
- `intellij/InlayHintsProvider.kt`
- `handlers/InlayHintsHandler.kt`

### Phase 10: Advanced Features

#### 10.1 双向能力：服务端发起请求（foundation）
**Goal**: Support server -> client requests（例如 `workspace/applyEdit` / 动态 capability 注册）

**Tasks**:
- [ ] Extend protocol layer to support outgoing requests:
  - `JsonRpcHandler` 增加对“response（无 method）”的处理与分发
  - 维护 pending requests（`id -> deferred/future`）并支持超时/取消
  - 生成全局唯一请求 id
- [ ] Extend server transport to send requests per connection:
  - 在 TCP/UDS 连接层提供 `sendRequest()` 并等待响应
- [ ] Add minimal client API surface:
  - `workspace/applyEdit` helper（用于重构/格式化等场景主动推 edits 给客户端）

**Implementation Notes**:
- 当前代码里对 response 的处理是“收到即忽略”，这会阻塞未来所有 server->client request 能力。

#### 10.2 Workspace Folders（`workspace/didChangeWorkspaceFolders`）
**Goal**: Support multi-root workspaces

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `WorkspaceFolder` / `DidChangeWorkspaceFoldersParams`
- [ ] Update `LifecycleHandler`:
  - `initialize` 支持 `workspaceFolders`
  - 调整 root 校验逻辑（允许 multi-root 或显式白名单）
- [ ] Create `WorkspaceFoldersHandler`:
  - Handle `workspace/didChangeWorkspaceFolders`
  - 更新工程根/可访问路径集合（影响 URI 校验与文件定位）
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建并 `register()` `WorkspaceFoldersHandler`

**Files to create**:
- `handlers/WorkspaceFoldersHandler.kt`

#### 10.3 File Watching（`workspace/didChangeWatchedFiles`）
**Goal**: React to external file changes to refresh caches/diagnostics

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `DidChangeWatchedFilesParams` / `FileEvent` / `FileChangeType`
- [ ] Create `WatchedFilesHandler`:
  - Handle `workspace/didChangeWatchedFiles`
  - 刷新 `VirtualFile`，必要时触发重新诊断/清缓存
- [ ] Register handler in startup:
  - 在 `LspServerStartupActivity` 中创建并 `register()` `WatchedFilesHandler`

**Files to create**:
- `handlers/WatchedFilesHandler.kt`

#### 10.4 Rename Refactoring（`textDocument/prepareRename` / `textDocument/rename`）
**Goal**: Provide rename edits across project

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `PrepareRenameParams` / `RenameParams`
  - Ensure `WorkspaceEdit` / `TextDocumentEdit` / `VersionedTextDocumentIdentifier` 等齐全
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `renameProvider`（建议支持 `prepareProvider = true`）
- [ ] Create `RenameProvider`:
  - Get PSI element at position
  - 校验是否允许 rename，并给出 prepareRename 的 range/placeholder
  - 查找所有 usages 并生成 `WorkspaceEdit`
- [ ] Create `PrepareRenameHandler`:
  - Handle `textDocument/prepareRename`
- [ ] Create `RenameHandler`:
  - Handle `textDocument/rename`
- [ ] Register handlers in startup:
  - 在 `LspServerStartupActivity` 中创建 provider 并注册 handler

**Implementation Notes**:
- rename 的核心是“正确性”而不是“文本替换”：应尽量复用 IntelliJ 的重构/引用解析能力。

**Files to create**:
- `intellij/RenameProvider.kt`
- `handlers/PrepareRenameHandler.kt`
- `handlers/RenameHandler.kt`

#### 10.5 Call Hierarchy（`textDocument/prepareCallHierarchy` / `callHierarchy/*`）
**Goal**: Provide caller/callee navigation

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `CallHierarchyItem` / `CallHierarchyPrepareParams`
  - Add `CallHierarchyIncomingCallsParams` / `CallHierarchyOutgoingCallsParams`
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `callHierarchyProvider = true`
- [ ] Create `CallHierarchyProvider`:
  - Prepare：从当前位置构造 `CallHierarchyItem`
  - Incoming/Outgoing：基于 IntelliJ 搜索/引用能力构造调用关系（best-effort）
- [ ] Create handlers:
  - Handle `textDocument/prepareCallHierarchy`
  - Handle `callHierarchy/incomingCalls`
  - Handle `callHierarchy/outgoingCalls`
- [ ] Register handlers in startup

**Files to create**:
- `intellij/CallHierarchyProvider.kt`
- `handlers/CallHierarchyHandler.kt`

#### 10.6 Type Hierarchy（`textDocument/prepareTypeHierarchy` / `typeHierarchy/*`）
**Goal**: Provide supertypes/subtypes navigation

**Tasks**:
- [ ] Update `protocol/models/LspTypes.kt`（补齐协议模型）:
  - Add `TypeHierarchyItem` / `TypeHierarchyPrepareParams`
  - Add `TypeHierarchySupertypesParams` / `TypeHierarchySubtypesParams`
- [ ] Update `LifecycleHandler.getServerCapabilities()`:
  - Advertise `typeHierarchyProvider = true`
- [ ] Create `TypeHierarchyProvider`:
  - Prepare：定位 class/interface 并构造 `TypeHierarchyItem`
  - Supertypes：向上枚举继承/实现链
  - Subtypes：查找所有子类/实现（注意限制数量与性能）
- [ ] Create handlers:
  - Handle `textDocument/prepareTypeHierarchy`
  - Handle `typeHierarchy/supertypes`
  - Handle `typeHierarchy/subtypes`
- [ ] Register handlers in startup

**Files to create**:
- `intellij/TypeHierarchyProvider.kt`
- `handlers/TypeHierarchyHandler.kt`

## Success Criteria

The plugin is considered complete when:
1. ✓ Server starts automatically when project opens
2. ✓ Multiple projects can run concurrent servers
3. ✓ Neovim can connect via TCP or UDS
4. ✓ All core LSP features work (hover, definition, completion, references)
5. ✓ Diagnostics are published in real-time
6. ✓ Status bar shows server status and port
7. ✓ Settings allow transport mode configuration
8. ✓ Server stops cleanly on project close
9. ✓ No resource leaks or crashes
10. ✓ Works with any IntelliJ project type

