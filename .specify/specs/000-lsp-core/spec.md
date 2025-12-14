# Feature Specification: IntelliJ LSP Core

**Feature Branch**: `000-lsp-core`  
**Created**: 2025-12-14  
**Status**: In Progress  
**Input**: 迁移自原有 SPEC.md

## User Scenarios & Testing

### User Story 1 - 外部编辑器连接 LSP Server (Priority: P1)

用户在 IntelliJ 中打开项目后，使用 Neovim/Emacs/VSCode 连接到 LSP Server，获取代码智能提示。

**Why this priority**: 这是核心功能，没有连接就无法使用任何 LSP 功能。

**Independent Test**: 可通过 `vim.lsp.rpc.connect("127.0.0.1", 2087)` 独立测试连接与 initialize 握手。

**Acceptance Scenarios**:

1. **Given** 项目已在 IntelliJ 打开, **When** 外部编辑器发送 `initialize` 请求, **Then** Server 返回 capabilities
2. **Given** Server 已初始化, **When** 客户端发送 `initialized` 通知, **Then** Server 准备好处理后续请求
3. **Given** 客户端已连接, **When** 客户端断开连接, **Then** Server 继续运行，等待新连接

---

### User Story 2 - 代码悬停显示文档 (Priority: P1)

用户在外部编辑器中悬停在符号上，显示类型信息和文档。

**Why this priority**: Hover 是最常用的代码智能功能之一。

**Independent Test**: 打开任意 .kt/.java 文件，悬停在类名/方法名上验证响应。

**Acceptance Scenarios**:

1. **Given** 文档已 didOpen, **When** 发送 `textDocument/hover` 请求, **Then** 返回 Markdown 格式的文档
2. **Given** 悬停位置无符号, **When** 发送 hover 请求, **Then** 返回 null（无内容）

---

### User Story 3 - 跳转到定义 (Priority: P1)

用户在外部编辑器中跳转到符号定义位置。

**Why this priority**: Go to Definition 是导航的基础功能。

**Independent Test**: 在引用处发送 `textDocument/definition`，验证跳转到声明位置。

**Acceptance Scenarios**:

1. **Given** 光标在引用处, **When** 发送 definition 请求, **Then** 返回定义位置 Location
2. **Given** 符号定义在外部库, **When** 发送 definition 请求, **Then** 返回库文件的 Location（或反编译位置）

---

### User Story 4 - 代码补全 (Priority: P1)

用户在外部编辑器中输入代码时获得智能补全建议。

**Why this priority**: 补全显著提升编码效率。

**Independent Test**: 输入类名前缀，发送 `textDocument/completion`，验证返回候选列表。

**Acceptance Scenarios**:

1. **Given** 输入 `Stri`, **When** 发送 completion 请求, **Then** 返回包含 `String` 的 CompletionItem 列表
2. **Given** 输入 `.`, **When** 发送 completion 请求, **Then** 返回成员方法/属性列表

---

### User Story 5 - 实时诊断 (Priority: P2)

用户在保存文件后，外部编辑器显示 IntelliJ 的错误和警告。

**Why this priority**: 诊断帮助用户及时发现问题，但对初始 MVP 不是强依赖。

**Independent Test**: 在代码中引入语法错误，保存文件，验证 `publishDiagnostics` 推送。

**Acceptance Scenarios**:

1. **Given** 文件有语法错误, **When** 文件保存, **Then** Server 推送 publishDiagnostics 包含错误信息
2. **Given** 文件错误已修复, **When** 文件保存, **Then** 诊断列表清空

---

### User Story 6 - 查找所有引用 (Priority: P2)

用户查找符号在项目中的所有引用位置。

**Why this priority**: 重构前了解影响范围的必需功能。

**Independent Test**: 在符号处发送 `textDocument/references`，验证返回所有使用位置。

**Acceptance Scenarios**:

1. **Given** 光标在方法声明处, **When** 发送 references 请求, **Then** 返回所有调用位置

---

### User Story 7 - abcoder 兼容 (Priority: P2)

支持 abcoder（UniAST）对 typeDefinition、documentSymbol、semanticTokens 的强依赖。

**Why this priority**: 扩展集成场景，但核心 LSP 功能优先。

**Independent Test**: 发送 `textDocument/typeDefinition`、`textDocument/documentSymbol`、`textDocument/semanticTokens/full` 验证响应。

**Acceptance Scenarios**:

1. **Given** 变量类型为 `List<String>`, **When** 发送 typeDefinition, **Then** 返回 `List` 接口定义位置
2. **Given** 文件包含类/函数, **When** 发送 documentSymbol, **Then** 返回层级符号树
3. **Given** 文件有代码, **When** 发送 semanticTokens/full, **Then** 返回 token 编码数组

---

### Edge Cases

- **端口冲突**：如果默认端口被占用，Server SHOULD 自动递增端口。
- **多项目**：每个项目窗口运行独立 Server，端口不冲突。
- **rootUri 校验**：如果客户端 rootUri 不在项目范围内，返回 `INVALID_PARAMS` 错误。
- **IntelliJ 未完成索引**：在索引期间，部分功能可能返回空结果，不应报错。

## Requirements

### Functional Requirements

- **FR-001**: Server MUST 在项目打开时自动启动
- **FR-002**: Server MUST 支持 TCP 和 Unix Domain Socket 两种传输模式
- **FR-003**: Server MUST 实现 LSP 3.17 基础协议（initialize、initialized、shutdown、exit）
- **FR-004**: Server MUST 支持增量文档同步（didOpen、didChange、didClose、didSave）
- **FR-005**: Server MUST 提供 hover、definition、completion、references 核心功能
- **FR-006**: Server MUST 推送实时诊断（publishDiagnostics）
- **FR-007**: Server MUST 在状态栏显示运行状态、端口号、连接数
- **FR-008**: Server SHOULD 支持 typeDefinition、documentSymbol、semanticTokens（abcoder 兼容）

### Key Entities

- **LspServer**：服务端实例，管理 TCP/UDS 连接
- **JsonRpcHandler**：JSON-RPC 消息路由与分发
- **DocumentManager**：文档版本跟踪与同步
- **PsiMapper**：LSP Position ↔ IntelliJ offset 转换

## Success Criteria

### Measurable Outcomes

- **SC-001**: Server 启动后 Neovim 可在 5 秒内完成 initialize 握手
- **SC-002**: Hover 响应延迟 < 500ms（95th percentile）
- **SC-003**: Completion 返回 < 100 条结果，延迟 < 1s
- **SC-004**: 多项目场景下无端口冲突或状态污染
- **SC-005**: Server 关闭/重启无资源泄漏
