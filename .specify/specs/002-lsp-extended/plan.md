# Implementation Plan: LSP Extended Features (Phase 9)

**Branch**: `001-lsp-extended` | **Date**: 2025-12-14 | **Spec
**: [spec.md](file:///f:/code/env/IntellijLsp/specs/001-lsp-extended/spec.md)
**Input**: Phase 9 扩展功能规格说明

## Summary

为 IntelliJ LSP Server 实现 6 个 LSP 扩展功能：Signature Help、Workspace Symbols、Code Formatting、Code Actions（仅
QuickFix）、Go to Implementation、Inlay Hints。所有功能遵循 Constitution 原则，复用 IntelliJ Platform SDK 原生能力。

## Technical Context

**Language/Version**: Kotlin 2.1.0 + JVM 21  
**Primary Dependencies**: IntelliJ Platform SDK 2025.1.4.1, Kotlin Coroutines 1.8.1  
**Storage**: N/A（无持久化需求）  
**Testing**: JUnit 5 (jupiter 5.10.2)  
**Target Platform**: IntelliJ IDEA 2025.1+（IC 版本）  
**Project Type**: IntelliJ Plugin（单项目）  
**Performance Goals**: Signature Help < 300ms, Workspace Symbol < 2s (95th percentile)  
**Constraints**: 仅本地访问（TCP/UDS），PSI 访问在 ReadAction 中执行  
**Scale/Scope**: 6 个新 Handler + 4 个新 Provider

## Constitution Check

_GATE: 所有功能必须通过 Constitution 检查_

| Principle                     | Status | Compliance                       |
|-------------------------------|--------|----------------------------------|
| I. Platform Integration First | ✅ Pass | 所有功能复用 IntelliJ Platform SDK API |
| II. Protocol Conformance      | ✅ Pass | 遵循 LSP 3.17 规范定义                 |
| III. Project Isolation        | ✅ Pass | Handler 绑定到 Project 实例           |
| IV. Graceful Degradation      | ✅ Pass | 无法获取数据时返回空结果                     |
| V. Observability              | ✅ Pass | 所有操作输出结构化日志                      |

## Project Structure

### Documentation (this feature)

```text
specs/001-lsp-extended/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/com/frenchef/intellijlsp/
├── handlers/                     # LSP request/notification handlers
│   ├── LifecycleHandler.kt       # [MODIFY] 添加新 capabilities
│   ├── SignatureHelpHandler.kt   # [NEW]
│   ├── WorkspaceSymbolHandler.kt # [NEW]
│   ├── FormattingHandler.kt      # [NEW]
│   ├── CodeActionHandler.kt      # [NEW]
│   ├── ImplementationHandler.kt  # [NEW]
│   └── InlayHintsHandler.kt      # [NEW]
├── intellij/                     # IntelliJ Platform integration
│   ├── SignatureHelpProvider.kt  # [NEW]
│   ├── WorkspaceSymbolProvider.kt# [NEW]
│   ├── FormattingProvider.kt     # [NEW]
│   └── CodeActionProvider.kt     # [NEW]
└── protocol/models/
    └── LspTypes.kt               # [MODIFY] 添加新数据模型

src/test/kotlin/com/frenchef/intellijlsp/
└── handlers/
    ├── SignatureHelpHandlerTest.kt   # [NEW]
    ├── WorkspaceSymbolHandlerTest.kt # [NEW]
    ├── FormattingHandlerTest.kt      # [NEW]
    ├── CodeActionHandlerTest.kt      # [NEW]
    ├── ImplementationHandlerTest.kt  # [NEW]
    └── InlayHintsHandlerTest.kt      # [NEW]
```

**Structure Decision**: 沿用现有 Handler/Provider 分离模式，Handler 负责 JSON-RPC 协议层，Provider 封装 IntelliJ Platform
API 调用。

---

## Phase 0: Research

### R1. IntelliJ Platform API 研究

**Signature Help**:

- 使用 `ParameterInfoHandler` API 获取函数签名
- 需要创建临时 Editor 上下文（参考 `EditorFactory.createDocument`）
- 参考 `com.intellij.lang.parameterInfo.ParameterInfoUtils`

**Workspace Symbols**:

- 使用 `GotoClassContributor` / `GotoSymbolContributor` API
- 搜索通过 `DefaultChooseByNameItemProvider` 实现
- 限制返回数量使用 `maxResults` 参数

**Code Formatting**:

- 使用 `CodeStyleManager.reformatText()` 在副本上执行
- 计算差异使用 `DocumentUtil` 或 diff 算法
- 返回 `TextEdit` 数组供客户端应用

**Code Actions (QuickFix)**:

- 使用 `ShowIntentionActionsHandler` 获取当前位置的 intentions
- 过滤 `IntentionAction.familyName` 匹配 QuickFix 类型
- 排除直接修改 PSI 的 action

**Go to Implementation**:

- 复用 `DefinitionsScopedSearch` API
- 支持接口/抽象类的实现查找
- 处理 Java ↔ Kotlin 互操作

**Inlay Hints**:

- 使用 `InlayHintsProvider` API (2020.3+)
- 收集 `InlayPresentation` 转换为 LSP `InlayHint`
- 支持 Type hints 和 Parameter hints

### R2. 技术决策

| Decision            | Choice           | Rationale             |
|---------------------|------------------|-----------------------|
| Signature Help 触发字符 | `(` 和 `,`        | LSP 标准触发，匹配 VSCode 行为 |
| Workspace Symbol 限制 | 100 条            | 避免大项目性能问题             |
| Formatting 实现       | 副本执行 + diff      | 不直接修改文件，符合 LSP 模型     |
| CodeAction 范围       | 仅 QuickFix       | 规格澄清结果，避免复杂重构         |
| Inlay Hints 初始支持    | Type + Parameter | 覆盖最常见场景               |

---

## Phase 1: Data Model

### 新增 LSP 类型

```kotlin
// Signature Help
data class SignatureHelp(
    val signatures: List<SignatureInformation>,
    val activeSignature: Int? = null,
    val activeParameter: Int? = null
)

data class SignatureInformation(
    val label: String,
    val documentation: String? = null,
    val parameters: List<ParameterInformation>? = null
)

data class ParameterInformation(
    val label: Any, // String or [Int, Int]
    val documentation: String? = null
)

// Workspace Symbol
data class WorkspaceSymbolParams(
    val query: String
)

// Formatting
data class DocumentFormattingParams(
    val textDocument: TextDocumentIdentifier,
    val options: FormattingOptions
)

data class FormattingOptions(
    val tabSize: Int,
    val insertSpaces: Boolean
)

// Code Action
data class CodeActionParams(
    val textDocument: TextDocumentIdentifier,
    val range: Range,
    val context: CodeActionContext
)

data class CodeActionContext(
    val diagnostics: List<Diagnostic>,
    val only: List<String>? = null
)

data class CodeAction(
    val title: String,
    val kind: String? = null,
    val edit: WorkspaceEdit? = null,
    val command: Command? = null
)

// Inlay Hints
data class InlayHintParams(
    val textDocument: TextDocumentIdentifier,
    val range: Range
)

data class InlayHint(
    val position: Position,
    val label: Any, // String or InlayHintLabelPart[]
    val kind: Int? = null,
    val paddingLeft: Boolean? = null,
    val paddingRight: Boolean? = null
)
```

---

## Verification Plan

### 单元测试

现有测试结构使用 JUnit 5，测试文件位于 `src/test/kotlin/`。

**运行命令**:

```bash
./gradlew test
```

**新增测试**:

| Handler                | 测试文件                            | 测试内容                             |
|------------------------|---------------------------------|----------------------------------|
| SignatureHelpHandler   | `SignatureHelpHandlerTest.kt`   | 验证 trigger 响应、activeParameter 切换 |
| WorkspaceSymbolHandler | `WorkspaceSymbolHandlerTest.kt` | 验证搜索结果、100 条限制                   |
| FormattingHandler      | `FormattingHandlerTest.kt`      | 验证 TextEdit 正确性                  |
| CodeActionHandler      | `CodeActionHandlerTest.kt`      | 验证 QuickFix 过滤                   |
| ImplementationHandler  | `ImplementationHandlerTest.kt`  | 验证接口实现查找                         |
| InlayHintsHandler      | `InlayHintsHandlerTest.kt`      | 验证 Type/Parameter hints          |

### 手动验证

**前置条件**:

1. 在 IntelliJ 中运行插件（Run Plugin 配置）
2. 使用 Neovim 连接 LSP Server

**验证步骤**:

1. **Signature Help**: 打开 .kt 文件，输入 `listOf(`，确认显示函数签名
2. **Workspace Symbol**: 在 Neovim 中执行 `:lua vim.lsp.buf.workspace_symbol("Foo")`，确认返回符号列表
3. **Formatting**: 执行 `:lua vim.lsp.buf.format()`，确认代码格式化
4. **Code Action**: 在有警告处执行 `:lua vim.lsp.buf.code_action()`，确认显示 QuickFix
5. **Implementation**: 在接口方法处执行 `:lua vim.lsp.buf.implementation()`，确认跳转
6. **Inlay Hints**: 执行 `:lua vim.lsp.inlay_hint.enable(true)`，确认显示提示

---

## Complexity Tracking

> 无 Constitution 违规需要说明

---

## Implementation Order

按优先级和依赖关系排序：

1. **LspTypes.kt** - 添加所有新数据模型（无依赖）
2. **LifecycleHandler.kt** - 声明新 capabilities
3. **SignatureHelpHandler/Provider** (P1)
4. **WorkspaceSymbolHandler/Provider** (P2)
5. **FormattingHandler/Provider** (P2)
6. **CodeActionHandler/Provider** (P3)
7. **ImplementationHandler** (P3, 复用现有 PsiMapper)
8. **InlayHintsHandler/Provider** (P4)
