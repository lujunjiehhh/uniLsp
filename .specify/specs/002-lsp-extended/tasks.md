# Tasks: LSP Extended Features (Phase 9)

**Input**: Design documents from `/specs/001-lsp-extended/`  
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅

**Tests**: 未明确要求测试，本任务列表不包含测试任务。如需添加测试，请单独请求。

**Organization**: 任务按用户故事组织，每个故事可独立实现和测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件，无依赖）
- **[Story]**: 任务所属用户故事（US1-US6）
- 描述中包含精确文件路径

---

## Phase 1: Setup (共享基础设施)

**Purpose**: 项目初始化和数据模型定义

- [x] T001 添加 Signature Help 数据模型到 `src/main/kotlin/com/frenchef/intellijlsp/protocol/models/LspTypes.kt`
- [x] T002 [P] 添加 Workspace Symbol 数据模型到 `LspTypes.kt`
- [x] T003 [P] 添加 Formatting 数据模型到 `LspTypes.kt`
- [x] T004 [P] 添加 Code Action 数据模型到 `LspTypes.kt`
- [x] T005 [P] 添加 Inlay Hints 数据模型到 `LspTypes.kt`
- [x] T006 [P] 添加 WorkspaceEdit 补充模型到 `LspTypes.kt`

---

## Phase 2: Foundational (阻塞性前置条件)

**Purpose**: 声明新 capabilities，所有用户故事依赖此阶段

**⚠️ CRITICAL**: 用户故事实现前必须完成此阶段

- [x] T007 更新 `LifecycleHandler.kt` 添加 `signatureHelpProvider` capability
- [x] T008 [P] 更新 `LifecycleHandler.kt` 添加 `workspaceSymbolProvider = true`
- [x] T009 [P] 更新 `LifecycleHandler.kt` 添加 `documentFormattingProvider = true`
- [x] T010 [P] 更新 `LifecycleHandler.kt` 添加 `documentRangeFormattingProvider = true`
- [x] T011 [P] 更新 `LifecycleHandler.kt` 添加 `codeActionProvider` capability
- [x] T012 [P] 更新 `LifecycleHandler.kt` 添加 `implementationProvider = true`
- [x] T013 [P] 更新 `LifecycleHandler.kt` 添加 `inlayHintProvider` capability

**Checkpoint**: Capabilities 声明完成 - 可开始用户故事实现

---

## Phase 3: User Story 1 - 函数参数提示 Signature Help (Priority: P1) 🎯 MVP

**Goal**: 用户在调用函数时，编辑器自动显示参数信息

**Independent Test**: 在 .kt/.java 文件中输入 `listOf(`，验证显示签名信息

### Implementation for User Story 1

- [x] T014 [US1] 创建 `SignatureHelpProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - 使用 PSI 分析获取函数签名
    - 计算 activeParameter 位置
- [x] T015 [US1] 创建 `SignatureHelpHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/signatureHelp` 处理器
    - 调用 SignatureHelpProvider
- [x] T016 [US1] 在 `LspServerStartupActivity.kt` 注册 SignatureHelpHandler
- [x] T017 [US1] 添加日志记录到 SignatureHelpHandler（Constitution V: Observability）

**Checkpoint**: US1 完成 - Signature Help 可独立测试

---

## Phase 4: User Story 2 - 工作区符号搜索 Workspace Symbols (Priority: P2)

**Goal**: 用户可在整个项目中按名称搜索类、函数、变量

**Independent Test**: 执行 `workspace/symbol` 请求 query="User"，验证返回符号列表

### Implementation for User Story 2

- [x] T018 [US2] 创建 `WorkspaceSymbolProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - 使用 PsiShortNamesCache API
    - 限制返回 ≤00 条结果
- [x] T019 [US2] 创建 `WorkspaceSymbolHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `workspace/symbol` 处理器
    - 支持取消请求
- [x] T020 [US2] 在 `LspServerStartupActivity.kt` 注册 WorkspaceSymbolHandler
- [x] T021 [US2] 添加日志记录到 WorkspaceSymbolHandler

**Checkpoint**: US2 完成 - Workspace Symbol 可独立测试

---

## Phase 5: User Story 3 - 代码格式化 Formatting (Priority: P2)

**Goal**: 用户请求格式化文档或选区，应用 IntelliJ 代码风格

**Independent Test**: 发送 `textDocument/formatting`，验证返回 TextEdit[]

### Implementation for User Story 3

- [x] T022 [US3] 创建 `FormattingProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - 使用 CodeStyleManager.reformatText()
    - 在副本上执行并计算 diff
- [x] T023 [US3] 创建 `FormattingHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/formatting` 处理器
    - 注册 `textDocument/rangeFormatting` 处理器
- [x] T024 [US3] 在 `LspServerStartupActivity.kt` 注册 FormattingHandler
- [x] T025 [US3] 添加日志记录到 FormattingHandler

**Checkpoint**: US3 完成 - Formatting 可独立测试

---

## Phase 6: User Story 4 - 代码操作 Code Actions (Priority: P3)

**Goal**: 用户请求可用的快速修复建议（仅 QuickFix）

**Independent Test**: 在有 warning 代码处发送 `codeAction`，验证返回 QuickFix

### Implementation for User Story 4

- [x] T026 [US4] 创建 `CodeActionProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - 使用 IntentionManager 获取 intentions
    - 过滤仅返回 QuickFix 类型
    - 转换为 WorkspaceEdit
- [x] T027 [US4] 创建 `CodeActionHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/codeAction` 处理器
- [x] T028 [US4] 在 `LspServerStartupActivity.kt` 注册 CodeActionHandler
- [x] T029 [US4] 添加日志记录到 CodeActionHandler

**Checkpoint**: US4 完成 - Code Actions 可独立测试

---

## Phase 7: User Story 5 - 跳转到实现 Go to Implementation (Priority: P3)

**Goal**: 用户可从接口方法跳转到具体实现类

**Independent Test**: 在接口方法处发送 `textDocument/implementation`，验证返回实现位置

### Implementation for User Story 5

- [x] T030 [US5] 创建 `ImplementationHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/implementation` 处理器
    - 使用 DefinitionsScopedSearch API
    - 复用现有 PsiMapper 进行位置转换
- [x] T031 [US5] 在 `LspServerStartupActivity.kt` 注册 ImplementationHandler
- [x] T032 [US5] 添加日志记录到 ImplementationHandler

**Checkpoint**: US5 完成 - Go to Implementation 可独立测试

---

## Phase 8: User Story 6 - 内嵌提示 Inlay Hints (Priority: P4)

**Goal**: 编辑器显示类型推断、参数名等内嵌提示

**Independent Test**: 发送 `textDocument/inlayHint`，验证返回类型/参数提示

### Implementation for User Story 6

- [x] T033 [US6] 创建 `InlayHintsProvider.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/intellij/`
    - PSI 遍历收集类型推断位置
    - 收集函数调用参数名
- [x] T034 [US6] 创建 `InlayHintsHandler.kt` 在 `src/main/kotlin/com/frenchef/intellijlsp/handlers/`
    - 注册 `textDocument/inlayHint` 处理器
- [x] T035 [US6] 在 `LspServerStartupActivity.kt` 注册 InlayHintsHandler
- [x] T036 [US6] 添加日志记录到 InlayHintsHandler

**Checkpoint**: US6 完成 - Inlay Hints 可独立测试

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 跨故事改进和文档更新

- [ ] T037 [P] 代码审查和重构
- [ ] T038 [P] 更新 README.md 添加新功能文档
- [ ] T039 验证所有功能符合 Constitution 原则
- [ ] T040 运行完整手动验证（使用 Neovim 连接测试）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖 - 可立即开始
- **Foundational (Phase 2)**: 依赖 Phase 1 - 阻塞所有用户故事
- **User Stories (Phase 3-8)**: 依赖 Phase 2 完成后可并行
- **Polish (Phase 9)**: 依赖所有用户故事完成

### User Story Dependencies

| Story    | 依赖      | 可并行 |
|----------|---------|-----|
| US1 (P1) | Phase 2 | ✅   |
| US2 (P2) | Phase 2 | ✅   |
| US3 (P2) | Phase 2 | ✅   |
| US4 (P3) | Phase 2 | ✅   |
| US5 (P3) | Phase 2 | ✅   |
| US6 (P4) | Phase 2 | ✅   |

### Within Each User Story

1. Provider 实现（封装 IntelliJ API）
2. Handler 实现（JSON-RPC 协议层）
3. 注册 Handler
4. 添加日志

### Parallel Opportunities

```text
# Phase 1 - 所有数据模型可并行添加
T001, T002, T003, T004, T005, T006 (同文件，建议顺序执行)

# Phase 2 - 所有 capabilities 声明可并行
T007, T008, T009, T010, T011, T012, T013 (同文件，建议顺序执行)

# User Stories - 不同故事可完全并行
Phase 3 (US1) || Phase 4 (US2) || Phase 5 (US3) || Phase 6 (US4) || Phase 7 (US5) || Phase 8 (US6)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. ✅ Complete Phase 1: Setup (T001-T006)
2. ✅ Complete Phase 2: Foundational (T007-T013)
3. ✅ Complete Phase 3: User Story 1 (T014-T017)
4. **STOP and VALIDATE**: 使用 Neovim 测试 Signature Help
5. 若 MVP 可用则部署

### Incremental Delivery

| Milestone | Stories  | 累计功能                           |
|-----------|----------|--------------------------------|
| MVP       | US1      | Signature Help                 |
| v1.1      | US2, US3 | + Workspace Symbol, Formatting |
| v1.2      | US4, US5 | + Code Actions, Implementation |
| v1.3      | US6      | + Inlay Hints                  |

---

## Task Summary

| Phase        | Tasks  | Files                                                 |
|--------------|--------|-------------------------------------------------------|
| Setup        | 6      | LspTypes.kt                                           |
| Foundational | 7      | LifecycleHandler.kt                                   |
| US1 (P1)     | 4      | SignatureHelpProvider.kt, SignatureHelpHandler.kt     |
| US2 (P2)     | 4      | WorkspaceSymbolProvider.kt, WorkspaceSymbolHandler.kt |
| US3 (P2)     | 4      | FormattingProvider.kt, FormattingHandler.kt           |
| US4 (P3)     | 4      | CodeActionProvider.kt, CodeActionHandler.kt           |
| US5 (P3)     | 3      | ImplementationHandler.kt                              |
| US6 (P4)     | 4      | InlayHintsProvider.kt, InlayHintsHandler.kt           |
| Polish       | 4      | -                                                     |
| **Total**    | **40** | **10 new + 2 modified**                               |

---

## Notes

- 所有 PSI 操作必须在 `ReadAction.compute {}` 中执行
- Handler 必须绑定到 Project 实例（Constitution III）
- 无法获取数据时返回空结果而非错误（Constitution IV）
- 所有操作输出结构化日志（Constitution V）
