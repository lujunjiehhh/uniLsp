# Implementation Plan: IntelliJ LSP Core

**Branch**: `000-lsp-core` | **Date**: 2025-12-14 | **Spec**: [spec.md](file:///f:/code/env/IntellijLsp/.specify/specs/000-lsp-core/spec.md)
**Input**: 迁移自原有 IMPLEMENTATION_PLAN.md

## Summary

构建 IntelliJ IDEA 插件，将 IntelliJ 的代码智能能力通过 LSP 协议暴露给外部编辑器（Neovim/Emacs/VSCode）。采用 TCP/UDS 传输层 + JSON-RPC 协议 + PSI 集成的技术栈。

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17+)  
**Primary Dependencies**: IntelliJ Platform SDK 2025.1+, Kotlin Coroutines 1.8+  
**Storage**: N/A（无持久化存储，状态在内存管理）  
**Testing**: JUnit 5 + IntelliJ Platform TestFramework  
**Target Platform**: IntelliJ IDEA（作为插件运行）  
**Project Type**: IntelliJ Plugin  
**Performance Goals**: Hover/Definition 响应 < 500ms, Completion < 1s  
**Constraints**: 仅 localhost 访问，PSI 访问需 ReadAction  
**Scale/Scope**: 单 IDE 实例多项目支持

## Constitution Check

| Principle                     | Status | Notes                                       |
| ----------------------------- | ------ | ------------------------------------------- |
| I. Platform Integration First | ✅     | 所有 LSP 功能复用 PSI/DocumentationProvider |
| II. Protocol Conformance      | ✅     | 严格遵循 LSP 3.17 规范                      |
| III. Project Isolation        | ✅     | 每项目独立 Server 实例                      |
| IV. Graceful Degradation      | ✅     | Handler 异常捕获，返回空结果                |
| V. Observability              | ✅     | 状态栏 Widget + 结构化日志                  |

## Project Structure

### Documentation (this feature)

```text
.specify/specs/000-lsp-core/
├── spec.md              # 功能规范
├── plan.md              # 本文件
└── tasks.md             # 任务清单
```

### Source Code (repository root)

```text
src/main/kotlin/com/frenchef/intellijlsp/
├── server/              # 服务端实现 (TCP/UDS)
│   ├── LspServer.kt
│   ├── TcpLspServer.kt
│   ├── UdsLspServer.kt
│   └── LspServerManager.kt
├── protocol/            # JSON-RPC 协议层
│   ├── JsonRpcHandler.kt
│   ├── MessageReader.kt
│   ├── MessageWriter.kt
│   └── models/          # LSP 数据模型
├── handlers/            # LSP 请求处理器
│   ├── LifecycleHandler.kt
│   ├── DocumentSyncHandler.kt
│   ├── HoverHandler.kt
│   ├── DefinitionHandler.kt
│   ├── CompletionHandler.kt
│   ├── ReferencesHandler.kt
│   ├── DocumentHighlightHandler.kt
│   ├── DiagnosticsHandler.kt
│   ├── TypeDefinitionHandler.kt
│   ├── DocumentSymbolHandler.kt
│   └── SemanticTokensHandler.kt
├── intellij/            # IntelliJ 集成层
│   ├── DocumentManager.kt
│   ├── PsiMapper.kt
│   ├── DiagnosticsProvider.kt
│   ├── CompletionProvider.kt
│   ├── DocumentSymbolProvider.kt
│   └── SemanticTokensProvider.kt
├── config/              # 配置与设置
│   ├── LspSettings.kt
│   ├── LspConfigurable.kt
│   └── TransportMode.kt
├── ui/                  # 用户界面
│   └── LspStatusWidget.kt
└── services/            # 项目级服务
    ├── LspProjectService.kt
    └── PortAllocator.kt

src/main/resources/META-INF/
└── plugin.xml           # 插件配置
```

**Structure Decision**: 采用标准 IntelliJ 插件结构，按职责分层（server/protocol/handlers/intellij/config/ui/services）。

## Key Design Decisions

### 传输层

- **TCP**：默认模式，端口从 2087 开始自动递增
- **UDS**：Socket 文件路径 `~/.intellij-lsp/project-{hash}.sock`
- 消息格式：`Content-Length: {len}\r\n\r\n{JSON}`

### 线程模型

- 网络 I/O：Kotlin Coroutines (`Dispatchers.IO`)
- PSI 访问：`ReadAction.compute { ... }`
- 诊断推送：防抖 500ms

### 错误处理

- Handler 异常捕获并记录日志
- 返回 LSP 标准错误码
- 客户端断开不影响 Server

## Phases Overview

| Phase | 描述                        | 状态      |
| ----- | --------------------------- | --------- |
| 1     | Foundation & Infrastructure | ✅ 完成   |
| 2     | Server Infrastructure       | ✅ 完成   |
| 3     | LSP Base Protocol           | ✅ 完成   |
| 4     | Document Synchronization    | ✅ 完成   |
| 5     | Core Language Features      | 🔄 进行中 |
| 6     | Diagnostics                 | ✅ 完成   |
| 7     | UI & Status                 | ✅ 完成   |
| 8     | Testing & Polish            | ⏳ 待开始 |
| 9     | Extended Features           | ⏳ 规划中 |
| 10    | Advanced Features           | ⏳ 规划中 |

## Verification Plan

### Automated Tests

```bash
./gradlew test           # 单元测试
./gradlew runIde         # 启动测试 IDE 实例
```

### Manual Verification

1. **Neovim 连接测试**：配置 `vim.lsp.rpc.connect("127.0.0.1", 2087)` 验证 initialize
2. **Hover 测试**：悬停在符号上验证文档显示
3. **Go to Definition**：`gd` 跳转验证
4. **Completion**：输入 `.` 验证补全弹出
5. **多项目**：同时打开两个项目，验证端口分配

## Complexity Tracking

> 当前无 Constitution 违规需记录
