# IntelliJ LSP Server Plugin - Technical Specification

## 1. Project Overview

### Purpose

Create an IntelliJ IDEA plugin that acts as a Language Server Protocol (LSP) server, enabling external editors (primarily Neovim) to access IntelliJ's code intelligence capabilities.

### Key Goals

- Provide LSP server functionality over TCP or Unix Domain Sockets (UDS)
- Bridge external LSP clients with IntelliJ's internal language services
- Support multiple concurrent project windows, each with its own LSP server instance
- Auto-start/stop with project lifecycle

## 2. Architecture

### 2.1 High-Level Design

```
┌─────────────┐         JSON-RPC over TCP/UDS        ┌──────────────────┐
│   Neovim    │ ◄──────────────────────────────────► │  IntelliJ LSP    │
│  (Client)   │                                       │  Server Plugin   │
└─────────────┘                                       └────────┬─────────┘
                                                               │
                                                               ▼
                                                      ┌──────────────────┐
                                                      │  IntelliJ PSI    │
                                                      │  Services API    │
                                                      └──────────────────┘
```

### 2.2 Core Components

1. **LSP Server Manager**

   - Manages server lifecycle per project
   - Handles port allocation and socket creation
   - Coordinates server startup/shutdown

2. **Transport Layer**

   - TCP Socket Server (configurable, default starts at port 2087)
   - Unix Domain Socket Server
   - JSON-RPC message handling
   - Connection management for multiple clients

3. **LSP Protocol Handler**

   - Parses and dispatches LSP requests
   - Formats LSP responses
   - Implements LSP 3.17 specification

4. **IntelliJ Integration Layer**

    - Maps LSP requests to IntelliJ PSI (Program Structure Interface) operations
   - Manages document synchronization
   - Handles diagnostics publishing

5. **Configuration Service**
   - Stores transport mode preference (TCP/UDS)
   - Displays current server status and port
   - Persists settings per project

## 3. Transport Layer Specification

### 3.1 TCP Mode

- **Default Port**: 2087
- **Port Selection**: Auto-increment to find available port (range: 2087-65535)
- **Binding**: localhost only (127.0.0.1)
- **Protocol**: JSON-RPC 2.0 over TCP
- **Message Format**: Content-Length header + JSON payload

### 3.2 Unix Domain Socket Mode

- **Socket Path**: `~/.intellij-lsp/project-{projectHash}.sock`
- **Permissions**: User-only (0600)
- **Protocol**: JSON-RPC 2.0 over UDS
- **Message Format**: Content-Length header + JSON payload

### 3.3 JSON-RPC Message Format

```
Content-Length: {length}\r\n
\r\n
{JSON payload}
```

## 4. LSP Capabilities - Core Features

### 4.1 Base Protocol (Priority: Critical)

- `initialize` - Server initialization with capability negotiation
- `initialized` - Confirmation after initialization
- `shutdown` - Graceful shutdown request
- `exit` - Server termination

#### RootUri Validation

During the `initialize` request, the server validates the client's `rootUri` parameter to ensure it matches or is a subfolder of the IntelliJ project path. This validation provides:

- **Security**: Ensures LSP operations stay within the intended project scope
- **Correctness**: Prevents PSI operations on files outside the project context
- **Clarity**: Explicitly defines the working directory relationship

**Validation Rules:**

- Client's `rootUri` must equal the IntelliJ project base path OR be a subfolder within it
- Paths are normalized (absolute, canonical separators) before comparison
- Only `file://` URI scheme is supported
- If validation fails, the server returns an `INVALID_PARAMS` error (-32602)

**Error Response Example:**

```json
{
  "code": -32602,
  "message": "Invalid rootUri: must be equal to or a subfolder of project path"
}
```

### 4.2 Document Synchronization (Priority: Critical)

- `textDocument/didOpen` - Document opened in client
- `textDocument/didChange` - Incremental document changes
- `textDocument/didClose` - Document closed in client
- `textDocument/didSave` - Document saved in client

**Note**: Change synchronization from Neovim → IntelliJ is lower priority. IntelliJ will pick up changes from disk on save.

### 4.3 Language Features (Priority: High)

- `textDocument/hover` - Show documentation/type information on hover
- `textDocument/definition` - Go to definition
- `textDocument/completion` - Code completion with intellisense
- `textDocument/references` - Find all references
- `textDocument/documentHighlight` - Highlight all occurrences of symbol in file

### 4.4 Diagnostics (Priority: High)

- `textDocument/publishDiagnostics` - Push errors/warnings to client
- Real-time error reporting from IntelliJ's code analysis

### 4.5 Extended Features (Phase 9) ✅

- `workspace/symbol` - Workspace-wide symbol search
- `textDocument/formatting` - Full document code formatting
- `textDocument/rangeFormatting` - Range code formatting
- `textDocument/codeAction` - Quick fixes and refactorings
- `codeAction/resolve` - Lazy resolution of code action edits
- `textDocument/semanticTokens/full` - Semantic highlighting for entire document
- `textDocument/semanticTokens/range` - Semantic highlighting for selected range
- `textDocument/signatureHelp` - Function signature information while typing
- `textDocument/implementation` - Go to interface implementations
- `textDocument/inlayHint` - Inline type and parameter hints

### 4.6 Advanced Features (Phase 10) ✅

- `textDocument/prepareRename` - Validate rename at position
- `textDocument/rename` - Safe cross-file rename refactoring with conflict detection
- `textDocument/prepareCallHierarchy` - Prepare call hierarchy at position
- `callHierarchy/incomingCalls` - Find methods calling this method
- `callHierarchy/outgoingCalls` - Find methods called by this method
- `textDocument/prepareTypeHierarchy` - Prepare type hierarchy at position
- `typeHierarchy/supertypes` - Find parent classes and interfaces
- `typeHierarchy/subtypes` - Find child classes and implementations
- `workspace/didChangeWorkspaceFolders` - Multi-root workspace support
- `workspace/didChangeWatchedFiles` - External file change notifications

**Language Support**: All features use UAST (Unified Abstract Syntax Tree) for cross-language support (Java, Kotlin,
Groovy).

**Safety Features**:

- Rename conflict detection
- Hierarchy traversal with cycle prevention (visited sets)
- Result limits (100 items) for hierarchy requests
- Project-scoped operations only (no external libraries)

## 5. IntelliJ Integration Points

### 5.1 Project Lifecycle Management

- **Startup Hook**: `ProjectActivity` or `StartupActivity` to initialize server when project opens
- **Shutdown Hook**: `ProjectCloseListener` or `ProjectManagerListener` to cleanup on project close
- **Service Level**: Project-level service (one instance per project)

### 5.2 PSI Integration

- **Document Management**: Use `PsiDocumentManager` for file synchronization
- **Code Intelligence**:
  - `PsiReference` for references
  - `PsiElement` for definitions
  - `CompletionContributor` for completions
  - `DocumentationProvider` for hover information
- **Document Highlighting**:
  - Use `ReferencesSearch` to find all occurrences within file scope
  - Support highlighting of variables, functions, classes, and other identifiers
  - Return ranges for all occurrences with optional kind (Text, Read, Write)

### 5.3 Diagnostics Integration

- **Error Reporting**: Monitor `DaemonCodeAnalyzer` for real-time diagnostics
- **Problem Holder**: Subscribe to problem updates via `WolfTheProblemSolver`
- **Highlighting**: Use `HighlightInfo` to extract error/warning information

## 6. Multi-Project Support

### 6.1 Project Isolation

- Each open project window runs its own LSP server instance
- Separate port/socket per project
- Independent document state management

### 6.2 Port Allocation Strategy

- First project: Try port 2087
- Subsequent projects: Try 2088, 2089, etc.
- Track allocated ports globally to avoid conflicts
- Display current port in IntelliJ status bar or tool window

### 6.3 Project Identification

- Use project base path hash for socket naming
- Use project instance ID for internal tracking

## 7. Configuration & Settings

### 7.1 Plugin Settings

- **Transport Mode**: Radio selection (TCP / Unix Domain Socket)
- **Starting Port**: Configurable, default 2087
- **Auto-start**: Enabled by default
- **Server Status**: Display current status, port, connection count

### 7.2 Status Display

- Status bar widget showing:
  - Server state (Starting/Running/Stopped)
  - Active port (TCP mode)
  - Socket path (UDS mode)
  - Connected client count

## 8. Error Handling & Resilience

### 8.1 Connection Handling

- Gracefully handle client disconnections
- Support reconnection without server restart
- Multiple concurrent client connections per project

### 8.2 Port Conflicts

- Auto-increment port on conflict
- Log selected port for user reference
- Fallback to UDS if TCP fails

### 8.3 Error Reporting

- Log errors to IntelliJ's idea.log
- Notify user on critical failures
- Graceful degradation when features unavailable

## 9. Performance Considerations

### 9.1 Threading

- Async handling of LSP requests
- Non-blocking I/O for network operations
- Use IntelliJ's application thread pool for PSI operations

### 9.2 Caching

- Cache frequently accessed PSI elements
- Maintain document version tracking
- Efficient incremental updates

### 9.3 Resource Management

- Proper cleanup on server shutdown
- Bounded thread pools
- Connection timeouts

## 10. Security

### 10.1 Network Security

- TCP: Bind to localhost only (127.0.0.1)
- No authentication required (localhost-only access)
- Future: Optional token-based authentication

### 10.2 File System Security

- UDS: User-only permissions (0600)
- Socket files in user-specific directory
- Automatic cleanup on exit

## 11. Testing Strategy

### 11.1 Unit Tests

- LSP protocol handler
- Message parsing/serialization
- Port allocation logic

### 11.2 Integration Tests

- IntelliJ PSI integration
- Document synchronization
- End-to-end LSP feature tests

### 11.3 Manual Testing

- Neovim integration testing
- Multi-project scenarios
- Error recovery scenarios

## 12. Dependencies

### 12.1 Required Libraries

- IntelliJ Platform SDK (already included)
- Kotlin coroutines for async operations
- JSON library (use IntelliJ's built-in Gson/Jackson)

### 12.2 IntelliJ Platform APIs

- `com.intellij.openapi.project.Project`
- `com.intellij.psi.*`
- `com.intellij.codeInsight.*`
- `com.intellij.openapi.editor.*`
- `com.intellij.lang.documentation.*`

## 13. Future Enhancements

### 13.1 Future Features

- Configuration change notifications
- Progress reporting for long operations
- Partial result support for large queries
- Bidirectional sync (IntelliJ → Client)
- On-type formatting
- Document links and code lens
- Folding range and selection range
- Debugging protocol integration (DAP)

## 14. References

- [LSP Specification 3.17](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)
- [IntelliJ Platform SDK Documentation](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
