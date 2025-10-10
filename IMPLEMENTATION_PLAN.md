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
│   └── DiagnosticsHandler.kt           # publishDiagnostics
├── intellij/
│   ├── DocumentManager.kt              # Document synchronization with IntelliJ
│   ├── PsiMapper.kt                    # Map PSI elements to LSP types
│   ├── DiagnosticsProvider.kt          # Extract diagnostics from IntelliJ
│   └── CompletionProvider.kt           # Bridge to IntelliJ completion
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
- [ ] Update `build.gradle.kts`:
  - Add Kotlin coroutines dependency
  - Add any required serialization libraries (or use IntelliJ's built-in Gson)
  - Configure plugin dependencies
- [ ] Update `plugin.xml`:
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
- [ ] Create `TransportMode` enum (TCP/UDS)
- [ ] Create `LspSettings` with persistent state:
  - Transport mode
  - Starting port (default 2087)
  - Auto-start enabled
- [ ] Create `LspConfigurable` for settings UI:
  - Radio buttons for transport mode
  - Number field for starting port
  - Display current server status
- [ ] Create `PortAllocator` singleton:
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
- [ ] Create `MessageReader`:
  - Parse Content-Length header
  - Read JSON payload
  - Handle malformed messages
- [ ] Create `MessageWriter`:
  - Format messages with Content-Length header
  - Serialize JSON responses
  - Buffer management
- [ ] Create LSP data models:
  - `LspRequest` (id, method, params)
  - `LspResponse` (id, result, error)
  - `LspNotification` (method, params)
  - Common LSP types (Position, Range, TextDocumentIdentifier, etc.)
- [ ] Create `JsonRpcHandler`:
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
- [ ] Create `LspServer` interface:
  - `start()`, `stop()`, `isRunning()`
  - `getPort()` or `getSocketPath()`
  - `onClientConnected()`, `onClientDisconnected()`
- [ ] Create `TcpLspServer`:
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
- [ ] Create `UdsLspServer`:
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
- [ ] Create `LspServerManager`:
  - Factory method to create server based on transport mode
  - Start server with error handling
  - Stop server and cleanup resources
- [ ] Create `LspProjectService`:
  - Project-level service
  - Hold reference to `LspServer` instance
  - Expose server status
  - Implement `Disposable` for cleanup
- [ ] Create startup activity:
  - Implement `StartupActivity` or `ProjectActivity`
  - Start LSP server when project opens
  - Log server status and port/socket path
- [ ] Create project close listener:
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
- [ ] Create `LifecycleHandler`:
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
- `completionProvider`: with trigger characters
- `referencesProvider`: true
- `diagnosticProvider`: workspace diagnostics

**Files to create**:
- `handlers/LifecycleHandler.kt`

## Phase 4: Document Synchronization

### 4.1 Document Manager
**Goal**: Synchronize documents between LSP client and IntelliJ

**Tasks**:
- [ ] Create `DocumentManager`:
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
- [ ] Create `DocumentSyncHandler`:
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
- [ ] Create `PsiMapper`:
  - Convert LSP Position to IntelliJ offset
  - Convert IntelliJ ranges to LSP ranges
  - URI ↔ VirtualFile conversion
- [ ] Create `HoverHandler`:
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
- [ ] Create `DefinitionHandler`:
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
- [ ] Create `CompletionProvider`:
  - Bridge to IntelliJ's `CompletionService`
  - Invoke completion at position
  - Extract completion items
  - Map to LSP CompletionItem
- [ ] Create `CompletionHandler`:
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
- [ ] Create `ReferencesHandler`:
  - Get PSI element at position
  - Use `ReferencesSearch` to find usages
  - Collect all references
  - Convert to LSP Location[]
  - Support include declaration flag

**Files to create**:
- `handlers/ReferencesHandler.kt`

## Phase 6: Diagnostics

### 6.1 Diagnostics Provider
**Goal**: Extract diagnostics from IntelliJ

**Tasks**:
- [ ] Create `DiagnosticsProvider`:
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
- [ ] Create `DiagnosticsHandler`:
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
- [ ] Create `LspStatusWidget`:
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
- Workspace symbol search
- Code formatting
- Code actions (quick fixes)
- Semantic tokens
- Signature help
- Inlay hints

### Phase 10: Advanced Features
- Bidirectional document sync
- Workspace folders
- File watching
- Rename refactoring
- Call hierarchy
- Type hierarchy

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

