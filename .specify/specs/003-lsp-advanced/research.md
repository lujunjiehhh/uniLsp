# Research: LSP Advanced Features (Phase 10)

**Feature**: 003-lsp-advanced
**Date**: 2025-12-15
**Status**: Complete

## LSP 规范研究

### 1. Rename Refactoring

**协议方法**:

- `textDocument/prepareRename` - 预检请求，验证符号可重命名性
- `textDocument/rename` - 执行重命名

**关键接口** (LSP 3.17):

```typescript
// prepareRename 响应
interface PrepareRenameResult {
  range: Range;
  placeholder: string;
}

// rename 请求参数
interface RenameParams extends TextDocumentPositionParams {
  newName: string;
}

// rename 响应
type RenameResult = WorkspaceEdit | null;
```

**Server Capability**:

- `renameProvider: boolean | RenameOptions`
- `RenameOptions.prepareProvider: boolean` 表示支持 prepareRename

**决策**:

- 仅支持项目内符号（外部库符号 prepareRename 返回 null）
- 使用 IntelliJ `RefactoringElementController` API

---

### 2. Call Hierarchy

**协议方法**:

- `textDocument/prepareCallHierarchy` - 准备调用层次项
- `callHierarchy/incomingCalls` - 获取调用者（谁调用了这个方法）
- `callHierarchy/outgoingCalls` - 获取被调用者（这个方法调用了谁）

**关键接口** (LSP 3.17):

```typescript
interface CallHierarchyItem {
  name: string;
  kind: SymbolKind;
  tags?: SymbolTag[];
  detail?: string;
  uri: DocumentUri;
  range: Range;
  selectionRange: Range;
  data?: LSPAny;
}

interface CallHierarchyIncomingCall {
  from: CallHierarchyItem;
  fromRanges: Range[];
}

interface CallHierarchyOutgoingCall {
  to: CallHierarchyItem;
  fromRanges: Range[];
}
```

**设计决策**:

- LSP 采用单层延迟加载设计，服务端每次只返回一层
- 单次请求结果数限制：100 项
- 使用 IntelliJ `CallHierarchyBrowser` API

---

### 3. Type Hierarchy

**协议方法**:

- `textDocument/prepareTypeHierarchy` - 准备类型层次项
- `typeHierarchy/supertypes` - 获取父类型
- `typeHierarchy/subtypes` - 获取子类型

**关键接口** (LSP 3.17):

```typescript
interface TypeHierarchyItem {
  name: string;
  kind: SymbolKind;
  tags?: SymbolTag[];
  detail?: string;
  uri: DocumentUri;
  range: Range;
  selectionRange: Range;
  data?: LSPAny;
}
```

**设计决策**:

- 与 Call Hierarchy 相同，采用单层延迟加载
- 单次请求结果数限制：100 项
- 使用 IntelliJ `TypeHierarchyBrowser` API

---

### 4. Server-Initiated Edits (workspace/applyEdit)

**协议方法**:

- `workspace/applyEdit` - Server → Client 请求

**关键接口** (LSP 3.17):

```typescript
interface ApplyWorkspaceEditParams {
  label?: string;
  edit: WorkspaceEdit;
}

interface ApplyWorkspaceEditResult {
  applied: boolean;
  failureReason?: string;
  failedChange?: uinteger;
}
```

**设计决策**:

- 需要扩展 `JsonRpcHandler` 支持双向通信
- 通过 `clientCapabilities.workspace.applyEdit` 检测支持
- 不支持时仅返回 WorkspaceEdit，不主动发送 applyEdit
- 超时限制：30 秒
- pending requests 队列化处理

---

### 5. Workspace Folders

**协议方法**:

- `workspace/didChangeWorkspaceFolders` - 通知（Client → Server）

**关键接口**:

```typescript
interface DidChangeWorkspaceFoldersParams {
  event: WorkspaceFoldersChangeEvent;
}

interface WorkspaceFoldersChangeEvent {
  added: WorkspaceFolder[];
  removed: WorkspaceFolder[];
}
```

**设计决策**:

- 维护 `workspaceFolders: List<WorkspaceFolder>` 状态
- 更新诊断范围

---

### 6. File Watching

**协议方法**:

- `workspace/didChangeWatchedFiles` - 通知（Client → Server）

**关键接口**:

```typescript
interface DidChangeWatchedFilesParams {
  changes: FileEvent[];
}

interface FileEvent {
  uri: DocumentUri;
  type: FileChangeType; // Created=1, Changed=2, Deleted=3
}
```

**设计决策**:

- 通过 `VirtualFileSystem.refresh()` 刷新文件状态
- 触发诊断重新计算

---

## IntelliJ Platform API 研究（官方文档）

**来源**: [IntelliJ SDK - Rename Refactoring](https://plugins.jetbrains.com/docs/intellij/rename-refactoring.html)

### Rename API

- `PsiNamedElement.setName()` - 重命名元素本身
- `PsiReference.handleElementRename()` - 更新所有引用
- `ReferencesSearch.search()` - 查找所有引用（现有项目已使用）
- `RenamePsiElementProcessor` - 自定义重命名逻辑

### Call Hierarchy API

基于现有项目模式使用 `ReferencesSearch` + `PsiMethod` 分析：

- 调用者 (incomingCalls): 搜索方法引用，获取调用位置的父 `PsiMethod`
- 被调用者 (outgoingCalls): 遍历方法体中的 `PsiMethodCallExpression`

### Type Hierarchy API

直接使用 PSI 类层次结构：

- 父类型: `PsiClass.superClass` + `PsiClass.interfaces`
- 子类型: `ClassInheritorsSearch.search()`

---

## 代码模式分析

### 现有 Handler 模式

```kotlin
class XxxHandler(private val project: Project, private val jsonRpcHandler: JsonRpcHandler) {
    fun register() {
        jsonRpcHandler.registerRequestHandler("method", this::handleMethod)
    }

    private fun handleMethod(params: JsonElement?): JsonElement? {
        // 1. 解析参数
        // 2. 调用 Provider
        // 3. 返回结果
    }
}
```

### 现有 Provider 模式

```kotlin
class XxxProvider(private val project: Project) {
    fun getXxx(file: PsiFile, position: Position): Result? = runReadAction {
        // 使用 IntelliJ Platform API
    }
}
```

---

## 澄清记录（来自 spec.md）

| 问题               | 答案                           |
|------------------|------------------------------|
| Hierarchy 结果数限制  | 100 项（与 workspace/symbol 一致） |
| 客户端不支持 applyEdit | 仅返回 WorkspaceEdit，不主动发送      |
| Rename 外部库符号     | 仅支持项目内符号                     |
