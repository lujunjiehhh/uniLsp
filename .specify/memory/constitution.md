<!--
Sync Impact Report:
- Version change: 0.0.0 → 1.0.0 (initial ratification)
- Added: 5 core principles, 2 additional sections
- Templates: constitution.md (✅ filled)
- Pending: spec.md, plan.md, tasks.md migrations
-->

# IntelliJ LSP Server Constitution

## Core Principles

### I. Platform Integration First

**描述**：所有 LSP 功能 MUST 优先复用 IntelliJ Platform SDK 的原生能力（PSI、索引、导航、重构），而非自行实现语言特定逻辑。

**理由**：保证跨语言/跨框架的一致性与正确性，减少维护负担，继承 IntelliJ 的持续优化。

### II. Protocol Conformance

**描述**：LSP 协议实现 MUST 严格遵循 [LSP 3.17 规范](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)。所有 handler 的输入/输出模型 MUST 与规范定义一致。

**理由**：确保与主流客户端（Neovim、Emacs、VSCode）的兼容性，降低客户端配置复杂度。

### III. Project Isolation

**描述**：每个打开的 IntelliJ 项目 MUST 运行独立的 LSP Server 实例（独立端口/Socket、独立文档状态）。跨项目请求 MUST NOT 干扰彼此。

**理由**：支持多项目并行开发场景，避免状态污染与资源冲突。

### IV. Graceful Degradation

**描述**：当某项 LSP 功能无法获取完整数据时，Server SHOULD 返回部分结果或空结果，而非错误。所有 handler MUST 捕获异常并记录日志，不得导致 Server 崩溃。

**理由**：提高客户端体验稳定性，便于问题诊断。

### V. Observability

**描述**：所有核心操作 MUST 输出结构化日志（含请求 ID、耗时、结果状态）。Server 状态变更 MUST 通过状态栏 Widget 实时反馈。

**理由**：便于调试与问题排查，提升开发者体验。

## Technical Constraints

- **传输层**：仅支持 TCP（localhost）和 Unix Domain Socket，MUST 保证仅本地访问（安全边界）。
- **线程模型**：网络 I/O 使用 Kotlin Coroutines；PSI 访问 MUST 在 ReadAction 中执行。
- **依赖**：MUST 使用 IntelliJ 内置 Gson/Jackson，避免引入外部 JSON 库。

## Development Workflow

- **Feature 开发**：遵循 speckit 流程（spec → plan → tasks → implement → verify）。
- **代码审查**：所有 PR MUST 附带对应 spec/task 引用。
- **发布**：使用 `./gradlew buildPlugin` 构建，版本号跟随 MAJOR.MINOR.PATCH。

## Governance

- 本 Constitution 优先于所有其他实践。
- 修订需记录变更内容、版本号递增、并更新 `LAST_AMENDED_DATE`。
- 运行时开发指南参见 `README.md`。

**Version**: 1.0.0 | **Ratified**: 2025-12-14 | **Last Amended**: 2025-12-14
