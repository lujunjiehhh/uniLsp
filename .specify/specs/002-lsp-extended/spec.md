# Feature Specification: LSP Extended Features (Phase 9)

**Feature Branch**: `001-lsp-extended`  
**Created**: 2025-12-14  
**Status**: Implemented  
**Input**: 基于 future-enhancements.md 的 Phase 9 扩展功能

## Implementation Notes

- **UAST** (Unified Abstract Syntax Tree) 用于跨语言统一处理 Java/Kotlin
- **K2 兼容**: 通过 `<supportsKotlinPluginMode supportsK2="true">` 声明
- **依赖**: `com.intellij.java` (必需), `org.jetbrains.kotlin` (可选)

## Clarifications

### Session 2025-12-14

- Q: Phase 9 应支持哪些 CodeAction 类型？ → A: 仅 QuickFix（修复诊断问题）

## User Scenarios & Testing

### User Story 1 - 函数参数提示 Signature Help (Priority: P1)

用户在调用函数时，编辑器自动显示参数信息（参数名、类型、当前参数位置）。

**Why this priority**: Signature Help 是编码体验提升最显著的功能，减少查看文档的频率。

**Independent Test**: 在 .kt/.java 文件中输入 `listOf(`，验证显示 `listOf(elements: T...)` 签名信息。

**Acceptance Scenarios**:

1. **Given** 用户输入函数调用 `(`, **When** 发送 `textDocument/signatureHelp` 请求, **Then** 返回 SignatureInformation
   包含参数列表
2. **Given** 用户输入逗号移动到下一个参数, **When** 发送 signatureHelp 请求, **Then** activeParameter 正确指向当前参数
3. **Given** 函数有多个重载, **When** 发送 signatureHelp 请求, **Then** 返回多个 SignatureInformation 供用户选择

---

### User Story 2 - 工作区符号搜索 Workspace Symbols (Priority: P2)

用户可以在整个项目中按名称搜索类、函数、变量等符号。

**Why this priority**: 大项目导航必需，是全局跳转的基础能力。

**Independent Test**: 输入 `@Foo` 触发工作区符号搜索，验证返回匹配的类/方法列表。

**Acceptance Scenarios**:

1. **Given** 项目包含多个类, **When** 发送 `workspace/symbol` 请求 query="User", **Then** 返回名称包含 "User" 的符号列表
2. **Given** 查询结果超过 100 条, **When** 发送请求, **Then** 返回 ≤100 条结果（限制避免性能问题）
3. **Given** 用户取消请求, **When** 发送 `$/cancelRequest`, **Then** Server 停止搜索

---

### User Story 3 - 代码格式化 Formatting (Priority: P2)

用户请求格式化文档或选区，编辑器自动应用 IntelliJ 的代码风格。

**Why this priority**: 团队协作标准化代码风格的必需功能。

**Independent Test**: 打开格式混乱的 .kt 文件，发送 `textDocument/formatting`，验证返回 TextEdit[] 修正缩进和空格。

**Acceptance Scenarios**:

1. **Given** 文档包含未格式化代码, **When** 发送 `textDocument/formatting` 请求, **Then** 返回 TextEdit 数组
2. **Given** 选中部分代码, **When** 发送 `textDocument/rangeFormatting` 请求, **Then** 仅返回该范围的 TextEdit
3. **Given** 文档已是正确格式, **When** 发送 formatting 请求, **Then** 返回空数组

---

### User Story 4 - 代码操作 Code Actions (Priority: P3)

用户在代码位置请求可用的快速修复建议（仅 QuickFix，不含 Refactor/Source Actions）。

**Why this priority**: 快速修复提效。Phase 9 仅支持 QuickFix 类型，Refactor 和 Source Actions 留待后续扩展。

**Independent Test**: 在有 warning 的代码处（如未使用变量）发送 `textDocument/codeAction`，验证返回修复建议。

**Acceptance Scenarios**:

1. **Given** 代码有诊断问题, **When** 发送 codeAction 请求, **Then** 返回与该诊断关联的 QuickFix
2. **Given** 代码无问题, **When** 发送 codeAction 请求, **Then** 返回通用重构建议（如 Extract Method）
3. **Given** 用户选择某个 CodeAction, **When** 返回 WorkspaceEdit, **Then** 客户端可应用修改

---

### User Story 5 - 跳转到实现 Go to Implementation (Priority: P3)

用户可以从接口方法跳转到具体实现类的方法。

**Why this priority**: 面向对象项目常用的导航功能。

**Independent Test**: 在接口方法声明处发送 `textDocument/implementation`，验证返回实现类的位置。

**Acceptance Scenarios**:

1. **Given** 光标在接口方法上, **When** 发送 implementation 请求, **Then** 返回所有实现该方法的类位置
2. **Given** 光标在普通方法上（非接口/抽象）, **When** 发送请求, **Then** 返回空或方法自身位置
3. **Given** Java 接口有 Kotlin 实现, **When** 发送请求, **Then** 正确返回跨语言实现

---

### User Story 6 - 内嵌提示 Inlay Hints (Priority: P4)

编辑器显示类型推断、参数名等内嵌提示，增强代码可读性。

**Why this priority**: 视觉辅助功能，非核心但提升阅读体验。

**Independent Test**: 打开使用类型推断的 Kotlin 代码，发送 `textDocument/inlayHint`，验证返回类型注解提示。

**Acceptance Scenarios**:

1. **Given** 变量使用类型推断 `val x = foo()`, **When** 发送 inlayHint 请求, **Then** 返回类型提示 `: ReturnType`
2. **Given** 函数调用有多个参数, **When** 发送请求, **Then** 返回参数名提示 `name:`, `age:`
3. **Given** 用户禁用某类提示, **When** Server 配置相应过滤, **Then** 该类提示不返回

---

### Edge Cases

- **Editor 上下文缺失**：Signature Help 需要 Editor 上下文，若无法获取应返回空结果而非报错
- **索引未完成**：Workspace Symbol 依赖索引，索引期间返回空或 partial 结果
- **格式化冲突**：用户项目无 Code Style 配置时，使用 IntelliJ 默认风格
- **CodeAction 不可转换**：部分 IntelliJ Intention 直接修改 PSI，无法转为 WorkspaceEdit，应跳过这些 action
- **跨语言实现**：Java ↔ Kotlin 互操作需特别处理

## Requirements

### Functional Requirements

- **FR-001**: Server MUST 声明 `signatureHelpProvider` capability 并指定 triggerCharacters `(` 和 `,`
- **FR-002**: Server MUST 实现 `textDocument/signatureHelp` 返回 SignatureInformation 列表
- **FR-003**: Server MUST 声明 `workspaceSymbolProvider` capability
- **FR-004**: Server MUST 实现 `workspace/symbol` 返回 SymbolInformation 列表，限制 ≤100 条
- **FR-005**: Server MUST 声明 `documentFormattingProvider` capability
- **FR-006**: Server MUST 实现 `textDocument/formatting` 返回 TextEdit 数组
- **FR-007**: Server SHOULD 声明 `documentRangeFormattingProvider` 并实现 `textDocument/rangeFormatting`
- **FR-008**: Server MUST 声明 `codeActionProvider` capability
- **FR-009**: Server MUST 实现 `textDocument/codeAction` 返回 QuickFix 类型的 CodeAction 列表（不含 Refactor/Source
  Actions）
- **FR-010**: Server MUST 声明 `implementationProvider` capability
- **FR-011**: Server MUST 实现 `textDocument/implementation` 返回 Location 列表
- **FR-012**: Server SHOULD 声明 `inlayHintProvider` capability
- **FR-013**: Server SHOULD 实现 `textDocument/inlayHint` 返回 InlayHint 列表

### Key Entities

- **SignatureInformation**: 函数签名信息，包含 label、documentation、parameters
- **ParameterInformation**: 参数信息，包含 label（参数名）和可选 documentation
- **SymbolInformation**: 符号信息，包含 name、kind、location、containerName
- **TextEdit**: 文本编辑操作，包含 range 和 newText
- **CodeAction**: 代码操作，包含 title、kind、edit（WorkspaceEdit）
- **InlayHint**: 内嵌提示，包含 position、label、kind（Type/Parameter）

## Success Criteria

### Measurable Outcomes

- **SC-001**: Signature Help 响应延迟 < 300ms（95th percentile）
- **SC-002**: Workspace Symbol 搜索 1000+ 符号项目时响应 < 2s
- **SC-003**: Formatting 返回的 TextEdit 应用后代码符合 IntelliJ Code Style
- **SC-004**: Code Actions 包含至少一个与当前诊断相关的 QuickFix（若有诊断）
- **SC-005**: Go to Implementation 正确识别 Java/Kotlin 跨语言实现
- **SC-006**: Inlay Hints 覆盖常见类型推断和参数名场景

## Assumptions

- 核心 LSP 功能（Phase 1-8）和 abcoder 兼容（Phase 10 部分）已完成
- IntelliJ 平台 API 可用于获取 Signature、Symbol、Formatting 等信息
- 部分功能（如 CodeAction）可能需要创建临时 Editor 上下文
