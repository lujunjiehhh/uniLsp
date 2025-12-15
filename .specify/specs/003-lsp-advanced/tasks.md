# Tasks: LSP Advanced Features (Phase 10)

**Input**: Design documents from `/.specify/specs/003-lsp-advanced/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅

**Tests**: 单元测试内置，E2E 测试使用 Python 脚本（`tools/lsp_test_client.py`）

**Organization**: 任务按用户故事组织，每个故事可独立实现和测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件，无依赖）
- **[Story]**: 所属用户故事（US1-US6）
- 包含精确文件路径

---

## Phase 1: Setup (数据类型扩展)

**Purpose**: 定义 Phase 10 所需的 LSP 数据类型

- [ ] T001 [P] 添加 Rename 数据类型到 `src/main/kotlin/.../protocol/models/LspTypes.kt`
    - `PrepareRenameParams`, `PrepareRenameResult`, `RenameParams`, `RenameOptions`
- [ ] T002 [P] 添加 Call Hierarchy 数据类型到 `src/main/kotlin/.../protocol/models/LspTypes.kt`
    - `CallHierarchyPrepareParams`, `CallHierarchyItem`, `CallHierarchyIncomingCall`, `CallHierarchyOutgoingCall`
- [ ] T003 [P] 添加 Type Hierarchy 数据类型到 `src/main/kotlin/.../protocol/models/LspTypes.kt`
    - `TypeHierarchyPrepareParams`, `TypeHierarchyItem`
- [ ] T004 [P] 添加 ApplyEdit 数据类型到 `src/main/kotlin/.../protocol/models/LspTypes.kt`
    - `ApplyWorkspaceEditParams`, `ApplyWorkspaceEditResult`
- [ ] T005 [P] 添加 Workspace/File 数据类型到 `src/main/kotlin/.../protocol/models/LspTypes.kt`
    - `DidChangeWorkspaceFoldersParams`, `DidChangeWatchedFilesParams`, `FileEvent`, `FileChangeType`
- [ ] T006 更新 `ServerCapabilities` 添加新 capabilities 字段
    - `callHierarchyProvider`, `typeHierarchyProvider`, `workspace`

---

## Phase 2: Foundational (双向通信基础设施)

**Purpose**: 支持 Server→Client 请求的核心基础设施

**⚠️ CRITICAL**: User Story 4 (Server-Initiated Edits) 依赖此阶段完成

- [ ] T007 创建 `PendingRequestManager.kt` 在 `src/main/kotlin/.../protocol/`
    - 管理 pending requests 映射
    - 实现 30s 超时机制
    - 请求 ID 生成
- [ ] T008 扩展 `JsonRpcHandler.kt` 支持双向通信
    - 添加 `sendRequest(method, params)` 方法
    - 添加 `handleResponse(id, json)` 处理响应
    - 集成 `PendingRequestManager`
- [ ] T009 [P] 创建 `PendingRequestManagerTest.kt` 在 `src/test/kotlin/.../protocol/`
    - 测试请求创建、完成、超时

**Checkpoint**: 双向通信基础设施就绪

---

## Phase 3: User Story 1 - 重命名重构 Rename Refactoring (Priority: P1) 🎯 MVP

**Goal**: 用户选中符号后发起重命名，跨文件安全替换所有引用

**Independent Test**: 在 .kt/.java 文件中选中类名，发送 `textDocument/prepareRename`，验证返回 range 和 placeholder；发送
`textDocument/rename`，验证返回包含所有引用位置的 WorkspaceEdit

### Implementation for User Story 1

- [ ] T010 [P] [US1] 创建 `RenameProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - `prepareRename(file, position)` - 验证可重命名性，返回 range/placeholder
    - `rename(file, position, newName)` - 执行重命名，返回 WorkspaceEdit
    - 使用 `ReferencesSearch.search()` 查找引用
    - 过滤外部库符号（返回 null）
    - **检测同名符号冲突并返回错误**
- [ ] T011 [US1] 创建 `RenameHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/prepareRename` handler
    - 注册 `textDocument/rename` handler
    - 解析参数并调用 RenameProvider
- [ ] T012 [US1] 更新 `LifecycleHandler.kt` 启用 `renameProvider` capability
    - `renameProvider = RenameOptions(prepareProvider = true)`
- [ ] T013 [US1] 更新 `LspClientConnection.kt` 注册 RenameHandler
- [ ] T014 [P] [US1] 创建 `RenameHandlerTest.kt` 在 `src/test/kotlin/com/frenchef/intellijlsp/handlers/`
    - 测试 prepareRename 返回正确格式
    - 测试 rename 返回 WorkspaceEdit
    - **测试同名符号冲突检测**

**Checkpoint**: Rename 功能可独立测试 (`tools/lsp_test_client.py`)

---

## Phase 4: User Story 2 - 调用层次 Call Hierarchy (Priority: P2)

**Goal**: 用户可以查看函数的调用者和被调用者层次结构

**Independent Test**: 在方法声明处发送 `textDocument/prepareCallHierarchy`，验证返回 CallHierarchyItem；发送
`callHierarchy/incomingCalls`，验证返回调用者列表

### Implementation for User Story 2

- [ ] T015 [P] [US2] 创建 `CallHierarchyProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - `prepareCallHierarchy(file, position)` - 返回 CallHierarchyItem 列表
    - `getIncomingCalls(item)` - 使用 ReferencesSearch 获取调用者
    - `getOutgoingCalls(item)` - 遍历方法体获取被调用者
    - 结果数限制 100 项
    - **递归调用检测防止无限循环**
- [ ] T016 [US2] 创建 `CallHierarchyHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/prepareCallHierarchy` handler
    - 注册 `callHierarchy/incomingCalls` handler
    - 注册 `callHierarchy/outgoingCalls` handler
- [ ] T017 [US2] 更新 `LifecycleHandler.kt` 启用 `callHierarchyProvider` capability
- [ ] T018 [US2] 更新 `LspClientConnection.kt` 注册 CallHierarchyHandler
- [ ] T019 [P] [US2] 创建 `CallHierarchyHandlerTest.kt` 在 `src/test/kotlin/.../handlers/`

**Checkpoint**: Call Hierarchy 功能可独立测试

---

## Phase 5: User Story 3 - 类型层次 Type Hierarchy (Priority: P2)

**Goal**: 用户可以查看类的继承关系（父类和子类）

**Independent Test**: 在类声明处发送 `textDocument/prepareTypeHierarchy`，验证返回 TypeHierarchyItem；发送
`typeHierarchy/supertypes`，验证返回父类列表

### Implementation for User Story 3

- [ ] T020 [P] [US3] 创建 `TypeHierarchyProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - `prepareTypeHierarchy(file, position)` - 返回 TypeHierarchyItem 列表
    - `getSupertypes(item)` - 使用 PsiClass.superClass/interfaces
    - `getSubtypes(item)` - 使用 ClassInheritorsSearch.search()
    - 结果数限制 100 项
    - **循环继承检测（visited set）防止无限递归**
- [ ] T021 [US3] 创建 `TypeHierarchyHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/prepareTypeHierarchy` handler
    - 注册 `typeHierarchy/supertypes` handler
    - 注册 `typeHierarchy/subtypes` handler
- [ ] T022 [US3] 更新 `LifecycleHandler.kt` 启用 `typeHierarchyProvider` capability
- [ ] T023 [US3] 更新 `LspClientConnection.kt` 注册 TypeHierarchyHandler
- [ ] T024 [P] [US3] 创建 `TypeHierarchyHandlerTest.kt` 在 `src/test/kotlin/.../handlers/`

**Checkpoint**: Type Hierarchy 功能可独立测试

---

## Phase 6: User Story 4 - 服务端发起编辑 Server-Initiated Edits (Priority: P2)

**Goal**: Server 可向 Client 发送 `workspace/applyEdit` 请求，应用 WorkspaceEdit

**Independent Test**: 调用 Server 内部 applyEdit 方法，验证请求格式正确；模拟 Client 响应，验证结果处理

### Implementation for User Story 4

- [ ] T025 [US4] 创建 `ApplyEditService.kt` 在 `src/main/kotlin/.../services/`
    - `applyEdit(label, edit)` - 发送 workspace/applyEdit 请求
    - 处理 ApplyWorkspaceEditResult 响应
    - 检查 clientCapabilities.workspace.applyEdit 支持
    - 不支持时返回 null，不发送请求
- [ ] T026 [US4] 集成 ApplyEditService 到 RenameHandler（可选的服务端驱动模式）
- [ ] T027 [P] [US4] 创建 `ApplyEditServiceTest.kt` 在 `src/test/kotlin/.../services/`

**Checkpoint**: ApplyEdit 服务可独立测试

---

## Phase 7: User Story 5 - 多根工作区 Workspace Folders (Priority: P3)

**Goal**: 支持客户端通知工作区文件夹变更

**Independent Test**: 发送 `workspace/didChangeWorkspaceFolders` 通知，验证 Server 更新工作区列表

### Implementation for User Story 5

- [ ] T028 [P] [US5] 创建 `WorkspaceFoldersHandler.kt` 在 `src/main/kotlin/.../handlers/`
    - 维护 `workspaceFolders: List<WorkspaceFolder>` 状态
    - 处理 `workspace/didChangeWorkspaceFolders` 通知
    - 更新添加/移除的工作区
- [ ] T029 [US5] 更新 `LifecycleHandler.kt` 启用 workspace.workspaceFolders capability
    - 添加 `WorkspaceCapabilities`, `WorkspaceFoldersServerCapabilities` 到 LspTypes
- [ ] T030 [US5] 更新 `LspClientConnection.kt` 注册 WorkspaceFoldersHandler

**Checkpoint**: Workspace Folders 功能可独立测试

---

## Phase 8: User Story 6 - 文件变更监听 File Watching (Priority: P3)

**Goal**: Server 响应客户端报告的文件系统变更

**Independent Test**: 发送 `workspace/didChangeWatchedFiles` 通知，验证 Server 刷新 VirtualFile

### Implementation for User Story 6

- [ ] T031 [P] [US6] 创建 `FileWatchingHandler.kt` 在 `src/main/kotlin/.../handlers/`
    - 处理 `workspace/didChangeWatchedFiles` 通知
    - 根据 FileChangeType 执行相应操作
    - Created: VirtualFileSystem.refresh()
    - Changed: 触发诊断刷新
    - Deleted: 清理相关状态
- [ ] T032 [US6] 更新 `LspClientConnection.kt` 注册 FileWatchingHandler

**Checkpoint**: File Watching 功能可独立测试

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 最终完善和集成验证

- [ ] T033 [P] 更新 `README.md` 添加 Phase 10 功能文档
- [ ] T034 [P] 更新 `SPEC.md` 添加 Phase 10 capabilities
- [ ] T035 运行 `./gradlew build` 验证编译
- [ ] T036 运行 `./gradlew test` 验证所有测试通过
- [ ] T037 使用 `tools/lsp_test_client.py` 进行 E2E 验证

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖 - 可立即开始
- **Phase 2 (Foundational)**: 依赖 Phase 1 - 阻塞 US4
- **Phase 3-6 (US1-US4)**: 依赖 Phase 1；US4 额外依赖 Phase 2
- **Phase 7-8 (US5-US6)**: 依赖 Phase 1
- **Phase 9 (Polish)**: 依赖所有用户故事完成

### User Story Dependencies

| Story                   | Depends On       | Can Parallel With       |
|-------------------------|------------------|-------------------------|
| US1 (Rename)            | Phase 1          | US2, US3, US5, US6      |
| US2 (Call Hierarchy)    | Phase 1          | US1, US3, US5, US6      |
| US3 (Type Hierarchy)    | Phase 1          | US1, US2, US5, US6      |
| US4 (ApplyEdit)         | Phase 1, Phase 2 | US5, US6                |
| US5 (Workspace Folders) | Phase 1          | US1, US2, US3, US4, US6 |
| US6 (File Watching)     | Phase 1          | US1, US2, US3, US4, US5 |

### Parallel Opportunities

```text
# Phase 1 可完全并行 (T001-T005)
Task: T001 [P] Rename types
Task: T002 [P] Call Hierarchy types
Task: T003 [P] Type Hierarchy types
Task: T004 [P] ApplyEdit types
Task: T005 [P] Workspace/File types

# Phase 3 内部并行
Task: T010 [P] RenameProvider
Task: T014 [P] RenameHandlerTest

# US1-US3 可完全并行实现
Task: Phase 3 (US1)
Task: Phase 4 (US2)
Task: Phase 5 (US3)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T006)
2. Complete Phase 3: User Story 1 - Rename (T010-T014)
3. **STOP and VALIDATE**: 使用 `lsp_test_client.py` 测试 Rename
4. Demo MVP

### Incremental Delivery

```text
Setup (T001-T006) → US1 Rename → US2 Call Hierarchy → US3 Type Hierarchy
                  → Phase 2 Foundational → US4 ApplyEdit
                  → US5 Workspace Folders → US6 File Watching
                  → Polish
```

---

## Summary

| Metric                       | Count    |
|------------------------------|----------|
| Total Tasks                  | 37       |
| Phase 1 (Setup)              | 6        |
| Phase 2 (Foundational)       | 3        |
| US1 (Rename) - P1            | 5        |
| US2 (Call Hierarchy) - P2    | 5        |
| US3 (Type Hierarchy) - P2    | 5        |
| US4 (ApplyEdit) - P2         | 3        |
| US5 (Workspace Folders) - P3 | 3        |
| US6 (File Watching) - P3     | 2        |
| Phase 9 (Polish)             | 5        |
| Parallelizable Tasks         | 17 (46%) |
