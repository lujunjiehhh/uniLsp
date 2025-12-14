# Tasks: IntelliJ LSP Core

**Input**: Design documents from `.specify/specs/000-lsp-core/`  
**Prerequisites**: plan.md (✅), spec.md (✅)

**Organization**: 任务按用户故事分组，支持独立实现与测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件，无依赖）
- **[Story]**: 关联的用户故事（US1-US7）
- 包含精确文件路径

---

## Phase 1: Setup (共享基础设施)

**Purpose**: 项目初始化与基础结构

- [x] T001 更新 `build.gradle.kts`：添加 Kotlin Coroutines 依赖
- [x] T002 更新 `plugin.xml`：注册服务、StartupActivity、StatusBar Widget
- [x] T003 [P] 创建 `config/TransportMode.kt`
- [x] T004 [P] 创建 `config/LspSettings.kt`
- [x] T005 [P] 创建 `config/LspConfigurable.kt`
- [x] T006 [P] 创建 `services/PortAllocator.kt`

---

## Phase 2: 基础设施 (Server & Protocol)

**Purpose**: 传输层与协议层实现

- [x] T007 创建 `protocol/MessageReader.kt`：解析 Content-Length + JSON
- [x] T008 [P] 创建 `protocol/MessageWriter.kt`：格式化 JSON-RPC 消息
- [x] T009 [P] 创建 `protocol/models/LspTypes.kt`：LSP 数据模型
- [x] T010 创建 `protocol/JsonRpcHandler.kt`：请求路由与错误处理
- [x] T011 创建 `server/LspServer.kt`：服务端接口
- [x] T012 [P] 创建 `server/TcpLspServer.kt`：TCP 实现
- [x] T013 [P] 创建 `server/UdsLspServer.kt`：Unix Socket 实现
- [x] T014 创建 `server/LspServerManager.kt`：生命周期管理
- [x] T015 创建 `services/LspProjectService.kt`：项目级服务
- [x] T016 创建 `LspServerStartupActivity.kt`：启动时初始化

**Checkpoint**: 基础 Server 框架完成

---

## Phase 3: User Story 1 - 外部编辑器连接 (Priority: P1) 🎯 MVP

**Goal**: 客户端可完成 initialize 握手

- [x] T017 [US1] 创建 `handlers/LifecycleHandler.kt`：initialize/shutdown/exit
- [x] T018 [US1] 在 `LifecycleHandler` 中实现 rootUri 校验
- [x] T019 [US1] 在 `LifecycleHandler` 中返回 ServerCapabilities

**Checkpoint**: Neovim 可连接并完成 initialize

---

## Phase 4: User Story 2 - Hover 文档 (Priority: P1)

**Goal**: 悬停显示类型/文档信息

- [x] T020 [US2] 创建 `intellij/PsiMapper.kt`：Position ↔ offset 转换
- [x] T021 [US2] 创建 `handlers/HoverHandler.kt`：textDocument/hover

**Checkpoint**: K/gd 悬停可显示文档

---

## Phase 5: User Story 3 - 跳转到定义 (Priority: P1)

**Goal**: Go to Definition 可用

- [x] T022 [US3] 创建 `handlers/DefinitionHandler.kt`：textDocument/definition

**Checkpoint**: gd 跳转可用

---

## Phase 6: User Story 4 - 代码补全 (Priority: P1)

**Goal**: 智能补全可用

- [x] T023 [US4] 创建 `intellij/CompletionProvider.kt`：桥接 IntelliJ 补全
- [x] T024 [US4] 创建 `handlers/CompletionHandler.kt`：textDocument/completion

**Checkpoint**: 输入 . 可触发补全

---

## Phase 7: 文档同步

**Purpose**: 文档状态管理

- [x] T025 创建 `intellij/DocumentManager.kt`：URI → Document 映射
- [x] T026 创建 `handlers/DocumentSyncHandler.kt`：didOpen/didChange/didClose/didSave

---

## Phase 8: User Story 5 - 诊断推送 (Priority: P2)

**Goal**: 实时错误/警告推送

- [x] T027 [US5] 创建 `intellij/DiagnosticsProvider.kt`：从 HighlightInfo 提取诊断
- [x] T028 [US5] 创建 `handlers/DiagnosticsHandler.kt`：publishDiagnostics

**Checkpoint**: 保存文件后诊断推送

---

## Phase 9: User Story 6 - 查找引用 (Priority: P2)

**Goal**: Find References 可用

- [x] T029 [US6] 创建 `handlers/ReferencesHandler.kt`：textDocument/references
- [x] T030 [P] [US6] 创建 `handlers/DocumentHighlightHandler.kt`：textDocument/documentHighlight

---

## Phase 10: User Story 7 - abcoder 兼容 (Priority: P2)

**Goal**: 支持 typeDefinition、documentSymbol、semanticTokens

### 协议模型补齐

- [x] T031 [P] [US7] 更新 `LspTypes.kt`：添加 TypeDefinition 参数
- [x] T032 [P] [US7] 更新 `LspTypes.kt`：添加 DocumentSymbol/SymbolKind 模型
- [x] T033 [P] [US7] 更新 `LspTypes.kt`：添加 SemanticTokens/Legend 模型

### Handler 实现

- [x] T034 [US7] 创建 `handlers/TypeDefinitionHandler.kt`
- [x] T035 [US7] 创建 `intellij/DocumentSymbolProvider.kt`
- [x] T036 [US7] 创建 `handlers/DocumentSymbolHandler.kt`
- [x] T037 [US7] 创建 `intellij/SemanticTokensProvider.kt`
- [x] T038 [US7] 创建 `handlers/SemanticTokensHandler.kt`

### Capability 声明

- [x] T039 [US7] 更新 `LifecycleHandler`：声明 typeDefinitionProvider
- [x] T040 [US7] 更新 `LifecycleHandler`：声明 documentSymbolProvider
- [x] T041 [US7] 更新 `LifecycleHandler`：声明 semanticTokensProvider + legend

**Checkpoint**: ✅ abcoder 可完成 initialize 并使用扩展功能

---

## Phase 11: UI 状态显示

**Purpose**: 状态栏实时反馈

- [x] T042 创建 `ui/LspStatusWidget.kt`：显示端口/连接数/状态

---

## Phase 12: 测试与打磨

**Purpose**: 质量保证

- [x] T043 [P] 单元测试：MessageReader/MessageWriter
- [x] T044 [P] 单元测试：PortAllocator
- [ ] T045 端到端测试：Neovim 连接全流程
- [x] T046 文档更新：README.md 使用说明

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1-2**: 顺序完成，后续依赖基础框架
- **Phase 3-6** (US1-4): 依赖 Phase 2 完成后可并行
- **Phase 7**: 与 Phase 3-6 并行
- **Phase 8-10** (US5-7): 依赖 Phase 7 文档同步
- **Phase 11**: 可与 Phase 3+ 并行
- **Phase 12**: 最后执行

### Parallel Opportunities

- T003-T006 可并行
- T007-T009 可并行
- T012-T013 可并行
- T031-T033 可并行
- T043-T044 可并行

---

## Implementation Strategy

### MVP First (User Stories 1-4)

1. 完成 Phase 1-2：基础框架
2. 完成 Phase 3-6：核心功能（initialize/hover/definition/completion）
3. **验证**：Neovim 连接并使用基本功能
4. 可部署 MVP

### Incremental Delivery

- Phase 8: 添加诊断推送
- Phase 9: 添加引用查找
- Phase 10: abcoder 兼容

---

## Notes

- 已完成任务标记 `[x]`
- 待完成任务标记 `[ ]`
- ✅ Phase 10 (abcoder 兼容) 已完成
- 当前重点：Phase 12 (测试与打磨)
