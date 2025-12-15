# Feature Specification: LSP Advanced Features (Phase 10)

**Feature Branch**: `003-lsp-advanced`  
**Created**: 2025-12-15  
**Status**: Draft  
**Input**: 基于 future-enhancements.md 的 Phase 10 高级功能

## Implementation Notes

- **双向通信**: 需扩展 JsonRpcHandler 支持 server → client 请求
- **K2 兼容**: 继承 Phase 9 的 `<supportsKotlinPluginMode supportsK2="true">` 声明
- **依赖**: `com.intellij.java` (必需), `org.jetbrains.kotlin` (可选)

## Clarifications

> [!NOTE]
> 本规格基于 future-enhancements.md Phase 10 规划，若有需求变更请更新此文档。

### Session 2025-12-15

- Q: Hierarchy 单次请求结果数限制？ → A: 100 项（与 workspace/symbol 一致，LSP 采用单层延迟加载设计）
- Q: 客户端不支持 applyEdit 的回退策略？ → A: 仅返回 WorkspaceEdit，不主动发送 applyEdit
- Q: Rename 是否支持外部库符号？ → A: 仅支持项目内符号，prepareRename 对外部库符号返回 null

---

## User Scenarios & Testing

### User Story 1 - 重命名重构 Rename Refactoring (Priority: P1)

用户选中符号后发起重命名，跨文件安全替换所有引用。

**Why this priority**: 重命名是最常用的重构操作，直接影响日常开发效率。

**Independent Test**: 在 .kt 文件中选中类名，发送 `textDocument/rename`，验证返回包含所有引用位置的 WorkspaceEdit。

**Acceptance Scenarios**:

1. **Given** 光标在类名 `Foo` 上, **When** 发送 `textDocument/prepareRename` 请求, **Then** 返回可重命名的 Range 和
   placeholder
2. **Given** 用户确认重命名 `Foo` → `Bar`, **When** 发送 `textDocument/rename` 请求, **Then** 返回 WorkspaceEdit
   包含所有引用位置的修改
3. **Given** 重命名目标已存在同名符号, **When** 发送 rename 请求, **Then** 返回错误提示冲突
4. **Given** Java 类有 Kotlin 引用, **When** 发送 rename 请求, **Then** WorkspaceEdit 包含跨语言引用

---

### User Story 2 - 调用层次 Call Hierarchy (Priority: P2)

用户可以查看函数的调用者和被调用者层次结构。

**Why this priority**: 理解代码调用关系是分析复杂系统的必需能力。

**Independent Test**: 在方法声明处发送 `textDocument/prepareCallHierarchy`，验证返回 CallHierarchyItem。

**Acceptance Scenarios**:

1. **Given** 光标在方法 `processData()` 上, **When** 发送 `prepareCallHierarchy` 请求, **Then** 返回 CallHierarchyItem
   包含方法信息
2. **Given** 已有 CallHierarchyItem, **When** 发送 `callHierarchy/incomingCalls` 请求, **Then** 返回调用该方法的位置列表
3. **Given** 已有 CallHierarchyItem, **When** 发送 `callHierarchy/outgoingCalls` 请求, **Then** 返回该方法调用的其他方法列表
4. **Given** 方法无调用者, **When** 发送 incomingCalls 请求, **Then** 返回空数组

---

### User Story 3 - 类型层次 Type Hierarchy (Priority: P2)

用户可以查看类的继承关系（父类和子类）。

**Why this priority**: 面向对象项目中理解继承结构的常用功能。

**Independent Test**: 在类声明处发送 `textDocument/prepareTypeHierarchy`，验证返回 TypeHierarchyItem。

**Acceptance Scenarios**:

1. **Given** 光标在类 `UserService` 上, **When** 发送 `prepareTypeHierarchy` 请求, **Then** 返回 TypeHierarchyItem 包含类信息
2. **Given** 已有 TypeHierarchyItem, **When** 发送 `typeHierarchy/supertypes` 请求, **Then** 返回父类/接口列表
3. **Given** 已有 TypeHierarchyItem, **When** 发送 `typeHierarchy/subtypes` 请求, **Then** 返回子类列表
4. **Given** 接口有多个实现类, **When** 发送 subtypes 请求, **Then** 返回所有实现类（限制 ≤100）

---

### User Story 4 - 服务端发起编辑 Server-Initiated Edits (Priority: P2)

Server 可向 Client 发送 `workspace/applyEdit` 请求，应用 WorkspaceEdit。

**Why this priority**: 支持 CodeAction 执行和 Rename 等功能的服务端驱动模式。

**Independent Test**: Server 发送 `workspace/applyEdit` 请求，Client 应用编辑并返回结果。

**Acceptance Scenarios**:

1. **Given** Server 需应用自动修复, **When** 发送 `workspace/applyEdit` 请求, **Then** Client 返回 `applied: true`
2. **Given** Client 拒绝编辑, **When** 返回 `applied: false`, **Then** Server 正确处理失败
3. **Given** 请求超时, **When** 等待响应超过 30s, **Then** Server 取消 pending request 并记录日志

---

### User Story 5 - 多根工作区 Workspace Folders (Priority: P3)

支持客户端通知工作区文件夹变更。

**Why this priority**: 支持 monorepo 和多模块项目场景。

**Independent Test**: 发送 `workspace/didChangeWorkspaceFolders` 通知，验证 Server 更新工作区范围。

**Acceptance Scenarios**:

1. **Given** 初始化时 `workspaceFolders` 包含 2 个目录, **When** 完成 initialize, **Then** Server 记录所有工作区路径
2. **Given** 客户端添加新工作区, **When** 发送 `didChangeWorkspaceFolders` 通知, **Then** Server 更新工作区列表
3. **Given** 客户端移除工作区, **When** 发送 `didChangeWorkspaceFolders` 通知, **Then** Server 从列表中移除

---

### User Story 6 - 文件变更监听 File Watching (Priority: P3)

Server 响应客户端报告的文件系统变更。

**Why this priority**: 支持外部工具修改文件后刷新状态。

**Independent Test**: 发送 `workspace/didChangeWatchedFiles` 通知，验证 Server 刷新 VirtualFile。

**Acceptance Scenarios**:

1. **Given** 外部工具创建新文件, **When** Client 发送 `didChangeWatchedFiles` (Created), **Then** Server 刷新
   VirtualFileSystem
2. **Given** 外部工具修改文件, **When** Client 发送 `didChangeWatchedFiles` (Changed), **Then** Server 触发诊断刷新
3. **Given** 外部工具删除文件, **When** Client 发送 `didChangeWatchedFiles` (Deleted), **Then** Server 清理相关状态

---

### Edge Cases

- **Rename 不可用**: 若符号不可重命名（如内置类型），`prepareRename` 应返回 null
- **Hierarchy 循环**: 处理类型层次时需防止循环引用导致无限递归
- **双向通信超时**: pending request 应有超时机制，避免内存泄漏
- **Workspace Folders 空**: 客户端可能不支持 workspaceFolders，退回 rootUri
- **并发请求**: 多个 applyEdit 请求需队列化处理，避免冲突

## Requirements

### Functional Requirements

- **FR-001**: Server MUST 声明 `renameProvider` 和 `prepareProvider` capability
- **FR-002**: Server MUST 实现 `textDocument/prepareRename` 返回可重命名范围
- **FR-003**: Server MUST 实现 `textDocument/rename` 返回 WorkspaceEdit
- **FR-004**: Server MUST 声明 `callHierarchyProvider` capability
- **FR-005**: Server MUST 实现 `textDocument/prepareCallHierarchy` 返回 CallHierarchyItem
- **FR-006**: Server MUST 实现 `callHierarchy/incomingCalls` 和 `outgoingCalls`
- **FR-007**: Server MUST 声明 `typeHierarchyProvider` capability
- **FR-008**: Server MUST 实现 `textDocument/prepareTypeHierarchy` 返回 TypeHierarchyItem
- **FR-009**: Server MUST 实现 `typeHierarchy/supertypes` 和 `subtypes`
- **FR-010**: Server MUST 支持发送 `workspace/applyEdit` 请求并处理响应
- **FR-011**: Server MUST 维护 pending requests 映射并支持超时/取消
- **FR-012**: Server SHOULD 声明 `workspaceFolders` capability
- **FR-013**: Server SHOULD 实现 `workspace/didChangeWorkspaceFolders` handler
- **FR-014**: Server SHOULD 实现 `workspace/didChangeWatchedFiles` handler

### Key Entities

- **PrepareRenameResult**: 重命名预检结果，包含 range 和 placeholder
- **RenameParams**: 重命名参数，包含 textDocument、position、newName
- **CallHierarchyItem**: 调用层次项，包含 name、kind、uri、range、selectionRange
- **CallHierarchyIncomingCall**: 传入调用，包含 from (CallHierarchyItem) 和 fromRanges
- **CallHierarchyOutgoingCall**: 传出调用，包含 to (CallHierarchyItem) 和 fromRanges
- **TypeHierarchyItem**: 类型层次项，包含 name、kind、uri、range、selectionRange
- **WorkspaceFolder**: 工作区文件夹，包含 uri 和 name
- **FileEvent**: 文件事件，包含 uri 和 type (Created/Changed/Deleted)
- **ApplyWorkspaceEditParams**: 应用编辑参数，包含 label 和 edit
- **ApplyWorkspaceEditResult**: 应用编辑结果，包含 applied 和可选 failureReason

## Success Criteria

### Measurable Outcomes

- **SC-001**: Rename 返回的 WorkspaceEdit 应用后不破坏编译
- **SC-002**: Call Hierarchy 正确识别 Java/Kotlin 跨语言调用
- **SC-003**: Type Hierarchy 正确展示多层继承关系
- **SC-004**: ApplyEdit 请求超时（>30s）正确清理资源
- **SC-005**: Workspace Folders 变更后诊断范围正确更新
- **SC-006**: File Watching 触发后 VirtualFile 状态一致

## Assumptions

- Phase 9（Extended Features）已完成并稳定
- IntelliJ Platform API 支持 Rename、CallHierarchy、TypeHierarchy 查询
- 客户端支持 `workspace/applyEdit` 请求（Neovim/VSCode 均支持）
