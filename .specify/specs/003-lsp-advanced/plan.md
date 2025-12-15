# Implementation Plan: LSP Advanced Features (Phase 10)

**Branch**: `003-lsp-advanced` | **Date**: 2025-12-15 | **Spec
**: [spec.md](file:///f:/code/env/IntellijLsp/.specify/specs/003-lsp-advanced/spec.md)

## Summary

Phase 10 实现 LSP 高级功能：Rename Refactoring、Call Hierarchy、Type Hierarchy、Server-Initiated Edits、Workspace
Folders、File Watching。技术方案复用 IntelliJ Platform SDK 原生能力，遵循现有 Handler/Provider 模式扩展。

## Technical Context

| 项目                   | 值                                     |
|----------------------|---------------------------------------|
| Language/Version     | Kotlin 2.1.0, JVM 21                  |
| Primary Dependencies | IntelliJ Platform SDK 2024.2, Gson    |
| Testing              | JUnit 5, IntelliJ Test Framework      |
| Target Platform      | IntelliJ IDEA 2024.2+                 |
| Performance Goals    | 标准负载下 P95 响应延迟 < 500ms                |
| Constraints          | PSI 访问在 ReadAction 中；applyEdit 超时 30s |

## Constitution Check

| Principle                     | Status | Notes                            |
|-------------------------------|--------|----------------------------------|
| I. Platform Integration First | ✅      | 复用 IntelliJ Rename/Hierarchy API |
| II. Protocol Conformance      | ✅      | 遵循 LSP 3.17 规范                   |
| III. Project Isolation        | ✅      | 每项目独立 handler 实例                 |
| IV. Graceful Degradation      | ✅      | prepareRename 返回 null 表示不可重命名    |
| V. Observability              | ✅      | 结构化日志 + 状态栏 Widget               |

---

## Proposed Changes

### Component 1: LspTypes.kt - 数据类型扩展

#### [MODIFY] [LspTypes.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/protocol/models/LspTypes.kt)

在文件末尾添加 Phase 10 新类型（约 100 行）：

- `PrepareRenameParams`, `PrepareRenameResult`, `RenameParams`, `RenameOptions`
- `CallHierarchyPrepareParams`, `CallHierarchyItem`, `CallHierarchyIncomingCall/OutgoingCall`
- `TypeHierarchyPrepareParams`, `TypeHierarchyItem`
- `ApplyWorkspaceEditParams`, `ApplyWorkspaceEditResult`
- `DidChangeWorkspaceFoldersParams`, `DidChangeWatchedFilesParams`, `FileEvent`

#### [MODIFY] [ServerCapabilities](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/protocol/models/LspTypes.kt#L198-L221)

添加新 capabilities：

```diff
+val callHierarchyProvider: Boolean?
+val typeHierarchyProvider: Boolean?
+val workspace: WorkspaceCapabilities?
```

---

### Component 2: JsonRpcHandler 双向通信

#### [NEW] [PendingRequestManager.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/protocol/PendingRequestManager.kt)

管理 server→client 请求的 pending 状态：

- `createRequest(id)` - 创建 CompletableFuture
- `completeRequest(id, result)` - 完成请求
- `failRequest(id, error)` - 请求失败
- 30s 超时自动清理

#### [MODIFY] [JsonRpcHandler.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/protocol/JsonRpcHandler.kt)

添加：

- `sendRequest(method, params)` - 发送请求并返回 Future
- `handleResponse(id, json)` - 处理响应

---

### Component 3: Handlers (5 个新文件)

| File                                                                                                                                             | Methods                                                                                           | Priority |
|--------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|----------|
| [RenameHandler.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/RenameHandler.kt) [NEW]                     | `textDocument/prepareRename`, `textDocument/rename`                                               | P1       |
| [CallHierarchyHandler.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/CallHierarchyHandler.kt) [NEW]       | `textDocument/prepareCallHierarchy`, `callHierarchy/incomingCalls`, `callHierarchy/outgoingCalls` | P2       |
| [TypeHierarchyHandler.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/TypeHierarchyHandler.kt) [NEW]       | `textDocument/prepareTypeHierarchy`, `typeHierarchy/supertypes`, `typeHierarchy/subtypes`         | P2       |
| [WorkspaceFoldersHandler.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/WorkspaceFoldersHandler.kt) [NEW] | `workspace/didChangeWorkspaceFolders`                                                             | P3       |
| [FileWatchingHandler.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/FileWatchingHandler.kt) [NEW]         | `workspace/didChangeWatchedFiles`                                                                 | P3       |

---

### Component 4: Providers & Services (4 个新文件)

| File                                                                                                                                         | Key Methods                                                          |
|----------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| [RenameProvider.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/intellij/RenameProvider.kt) [NEW]               | `prepareRename()`, `rename()`                                        |
| [CallHierarchyProvider.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/intellij/CallHierarchyProvider.kt) [NEW] | `prepareCallHierarchy()`, `getIncomingCalls()`, `getOutgoingCalls()` |
| [TypeHierarchyProvider.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/intellij/TypeHierarchyProvider.kt) [NEW] | `prepareTypeHierarchy()`, `getSupertypes()`, `getSubtypes()`         |
| [ApplyEditService.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/services/ApplyEditService.kt) [NEW]           | `applyEdit()`, capability 检测, 超时处理                                   |

---

### Component 5: LifecycleHandler Capabilities

#### [MODIFY] [LifecycleHandler.kt](file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/LifecycleHandler.kt#L137-L214)

更新 `getServerCapabilities()`:

```kotlin
renameProvider = RenameOptions(prepareProvider = true)
callHierarchyProvider = true
typeHierarchyProvider = true
workspace = WorkspaceCapabilities(
    workspaceFolders = WorkspaceFoldersServerCapabilities(
        supported = true,
        changeNotifications = true
    )
)
```

---

## Verification Plan

### Automated Tests

**现有测试框架**: `src/test/kotlin/com/frenchef/intellijlsp/`

已有测试文件:

- `protocol/MessageReaderTest.kt`
- `protocol/MessageWriterTest.kt`
- `services/PortAllocatorTest.kt`

**新增测试**:

```bash
# 运行所有测试
./gradlew test

# 运行特定测试
./gradlew test --tests "com.frenchef.intellijlsp.protocol.PendingRequestManagerTest"
./gradlew test --tests "com.frenchef.intellijlsp.handlers.RenameHandlerTest"
```

| 新测试文件                          | 测试内容                      |
|--------------------------------|---------------------------|
| `PendingRequestManagerTest.kt` | 请求创建、完成、超时                |
| `RenameHandlerTest.kt`         | prepareRename、rename 响应格式 |
| `CallHierarchyHandlerTest.kt`  | 调用层次查询                    |
| `TypeHierarchyHandlerTest.kt`  | 类型层次查询                    |

### Manual Verification

使用 `tools/lsp_test_client.py` 进行手工验证：

```bash
# 1. 启动 IntelliJ 并打开测试项目
# 2. 运行测试客户端
python tools/lsp_test_client.py

# 3. 发送测试请求
# Rename:
{"jsonrpc":"2.0","id":1,"method":"textDocument/prepareRename","params":{"textDocument":{"uri":"file:///path/to/file.kt"},"position":{"line":10,"character":5}}}

# Call Hierarchy:
{"jsonrpc":"2.0","id":2,"method":"textDocument/prepareCallHierarchy","params":{"textDocument":{"uri":"file:///path/to/file.kt"},"position":{"line":10,"character":5}}}
```

### Build Verification

```bash
./gradlew build
./gradlew buildPlugin
```

---

## Implementation Priority

| Priority | Feature                | Files   | Est. Effort |
|----------|------------------------|---------|-------------|
| P1       | Rename Refactoring     | 4 files | 2 days      |
| P2       | Call Hierarchy         | 2 files | 1.5 days    |
| P2       | Type Hierarchy         | 2 files | 1.5 days    |
| P2       | Server-Initiated Edits | 2 files | 1 day       |
| P3       | Workspace Folders      | 1 file  | 0.5 day     |
| P3       | File Watching          | 1 file  | 0.5 day     |

**Total**: ~7 days
