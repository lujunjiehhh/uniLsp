# Future Enhancements: IntelliJ LSP Core

**状态**: 规划中  
**依赖**: 核心功能（Phase 1-8）与 abcoder 兼容（Phase 10）完成后再实施

本文档记录原 IMPLEMENTATION_PLAN.md 中 Phase 9-10 的扩展功能详细规划。

---

## Phase 9: Extended Features（扩展功能）

### 9.1 Workspace Symbols (`workspace/symbol`)

**Goal**: 工作区级符号搜索

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `WorkspaceSymbolParams`、`SymbolInformation` 模型
- [ ] 更新 `LifecycleHandler`：声明 `workspaceSymbolProvider = true`
- [ ] 创建 `intellij/WorkspaceSymbolProvider.kt`：
  - 基于 IntelliJ 索引/搜索能力按 query 搜索符号
  - 限制返回数量（如 100），支持取消避免卡死
- [ ] 创建 `handlers/WorkspaceSymbolHandler.kt`
- [ ] 注册 handler

**Implementation Notes**:

- 优先使用 IntelliJ 平台索引，而非遍历 PSI
- 对不同语言做保守映射（class/function/variable/module）

---

### 9.2 Go to Implementation (`textDocument/implementation`)

**Goal**: 跳转到接口/抽象方法的实现

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `implementationProvider`
- [ ] 更新 `LifecycleHandler`：声明 capability
- [ ] 创建 `handlers/ImplementationHandler.kt`：
  - 复用 IntelliJ 继承/重写搜索能力
  - 保证 Java ↔ Kotlin 互操作

---

### 9.3 Code Formatting (`textDocument/formatting` / `rangeFormatting`)

**Goal**: 提供格式化 edits（不直接修改文件）

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `DocumentFormattingParams`、`FormattingOptions`
- [ ] 更新 `LifecycleHandler`：声明 formatting providers
- [ ] 创建 `intellij/FormattingProvider.kt`：
  - 在副本/临时 PSI 上执行格式化
  - 计算差异并返回 `TextEdit[]`
- [ ] 创建 `handlers/DocumentFormattingHandler.kt`
- [ ] 创建 `handlers/DocumentRangeFormattingHandler.kt` (optional)

**Implementation Notes**:

- 文档同步是"客户端 → 服务端"，格式化返回 edits 由客户端应用
- 后续可支持 `workspace/applyEdit` 实现 server-driven

---

### 9.4 Code Actions (`textDocument/codeAction`)

**Goal**: 暴露 IntelliJ quick fixes/intentions

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `CodeActionParams`、`CodeAction`、`Command`
- [ ] 更新 `LifecycleHandler`：声明 `codeActionProvider`
- [ ] 创建 `intellij/CodeActionProvider.kt`：
  - 收集当前 range 的候选 action
  - 初版仅提供可安全转为 `WorkspaceEdit` 的 action
- [ ] 创建 `handlers/CodeActionHandler.kt`
- [ ] 创建 `handlers/ExecuteCommandHandler.kt` (optional)

**Implementation Notes**:

- 难点：IntelliJ intention 多为直接修改 PSI 的写操作
- 建议优先实现"可转成 WorkspaceEdit"的类型

---

### 9.5 Signature Help (`textDocument/signatureHelp`)

**Goal**: 显示函数参数信息

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `SignatureHelp`、`SignatureInformation`、`ParameterInformation`
- [ ] 更新 `LifecycleHandler`：声明 `signatureHelpProvider` + triggerCharacters
- [ ] 创建 `intellij/SignatureHelpProvider.kt`：
  - 复用 IntelliJ 参数信息能力（可能需 Editor 上下文）
- [ ] 创建 `handlers/SignatureHelpHandler.kt`

**Implementation Notes**:

- 若需 Editor，创建临时 editor 并确保释放

---

### 9.6 Inlay Hints (`textDocument/inlayHint`)

**Goal**: 显示类型/参数名提示

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `InlayHint`、`InlayHintLabelPart`
- [ ] 更新 `LifecycleHandler`：声明 `inlayHintProvider`
- [ ] 创建 `intellij/InlayHintsProvider.kt`：
  - 复用 IntelliJ inlay hints 计算结果
- [ ] 创建 `handlers/InlayHintsHandler.kt`

**Implementation Notes**:

- 不同语言插件实现差异大，先从 Java/Kotlin 常见 hints 开始

---

## Phase 10: Advanced Features（高级功能）

### 10.1 双向能力：服务端发起请求

**Goal**: 支持 server → client requests（如 `workspace/applyEdit`）

**Tasks**:

- [ ] 扩展 `JsonRpcHandler`：处理 response（无 method）的分发
- [ ] 维护 pending requests（id → deferred）并支持超时/取消
- [ ] 在 TCP/UDS 连接层提供 `sendRequest()` 并等待响应
- [ ] 添加 `workspace/applyEdit` helper

---

### 10.2 Workspace Folders (`workspace/didChangeWorkspaceFolders`)

**Goal**: 支持多根工作区

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `WorkspaceFolder`、`DidChangeWorkspaceFoldersParams`
- [ ] 更新 `LifecycleHandler`：`initialize` 支持 `workspaceFolders`，调整 root 校验
- [ ] 创建 `handlers/WorkspaceFoldersHandler.kt`

---

### 10.3 File Watching (`workspace/didChangeWatchedFiles`)

**Goal**: 响应外部文件变更

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `DidChangeWatchedFilesParams`、`FileEvent`
- [ ] 创建 `handlers/WatchedFilesHandler.kt`：刷新 VirtualFile，触发诊断

---

### 10.4 Rename Refactoring (`textDocument/rename`)

**Goal**: 跨项目重命名

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `PrepareRenameParams`、`RenameParams`
- [ ] 更新 `LifecycleHandler`：声明 `renameProvider` + `prepareProvider`
- [ ] 创建 `intellij/RenameProvider.kt`：
  - 基于 IntelliJ 重构能力生成 `WorkspaceEdit`
- [ ] 创建 `handlers/PrepareRenameHandler.kt`
- [ ] 创建 `handlers/RenameHandler.kt`

**Implementation Notes**:

- 核心是正确性而非文本替换，必须复用 IntelliJ 重构能力

---

### 10.5 Call Hierarchy (`textDocument/prepareCallHierarchy`)

**Goal**: 调用/被调用关系导航

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `CallHierarchyItem`、incoming/outgoing params
- [ ] 更新 `LifecycleHandler`：声明 `callHierarchyProvider`
- [ ] 创建 `intellij/CallHierarchyProvider.kt`
- [ ] 创建 `handlers/CallHierarchyHandler.kt`

---

### 10.6 Type Hierarchy (`textDocument/prepareTypeHierarchy`)

**Goal**: 继承关系导航

**Tasks**:

- [ ] 更新 `LspTypes.kt`：添加 `TypeHierarchyItem`、supertypes/subtypes params
- [ ] 更新 `LifecycleHandler`：声明 `typeHierarchyProvider`
- [ ] 创建 `intellij/TypeHierarchyProvider.kt`：
  - Supertypes：向上枚举继承链
  - Subtypes：查找子类（注意性能限制）
- [ ] 创建 `handlers/TypeHierarchyHandler.kt`

---

## Implementation Priority（实施优先级）

基于用户需求和影响面排序：

1. **P1 - Signature Help**: 编码体验显著提升
2. **P2 - Workspace Symbols**: 大项目导航必需
3. **P2 - Code Formatting**: 团队协作标准化
4. **P3 - Code Actions**: 快速修复提效
5. **P3 - Go to Implementation**: 面向对象项目常用
6. **P4 - Rename**: 重构支持（但风险较高）
7. **P4 - Inlay Hints**: 视觉辅助（非必需）
8. **P5 - Call/Type Hierarchy**: 架构分析场景

---

## Notes

- 所有扩展功能标记为 `[ ]` 待实现
- 推荐在核心功能稳定后逐步添加
- 优先实现用户反馈需求最强的功能
