# Research: LSP Extended Features (Phase 9)

**Date**: 2025-12-14  
**Branch**: `001-lsp-extended`

## R1. IntelliJ Platform API 研究

### Signature Help

**API 入口**:

- `com.intellij.lang.parameterInfo.ParameterInfoHandler` - 语言特定的参数信息处理
- `com.intellij.lang.parameterInfo.ParameterInfoUtils` - 工具方法

**实现策略**:

1. 获取当前位置的 `PsiElement`
2. 查找包含的函数调用表达式
3. 使用 `resolveReference()` 获取目标方法
4. 从 `PsiMethod`/`KtFunction` 提取参数信息
5. 计算当前光标所在的参数索引（通过逗号计数）

**Decision**: 直接使用 PSI 分析而非 `ParameterInfoHandler`，因为后者需要 Editor 上下文

---

### Workspace Symbols

**API 入口**:

- `com.intellij.ide.util.gotoByName.ChooseByNameContributor`
- `com.intellij.ide.util.gotoByName.GotoClassContributor`
- `com.intellij.ide.util.gotoByName.GotoSymbolContributor`
- `com.intellij.navigation.ChooseByNameContributorEx`

**实现策略**:

1. 使用 `AllClassesGotoSearcher` 或 `DefaultChooseByNameItemProvider`
2. 设置 `maxResults = 100` 限制返回数量
3. 转换 `NavigationItem` 为 `SymbolInformation`
4. 处理搜索取消（检查 `CancellationException`）

**Decision**: 使用 `ChooseByNameContributorEx.processNames()` 和 `processElementsWithName()` 组合

---

### Code Formatting

**API 入口**:

- `com.intellij.psi.codeStyle.CodeStyleManager`
- `com.intellij.openapi.editor.actions.AbstractIndentLinesHandler`

**实现策略**:

1. 创建文档副本（`PsiDocumentManager.getDocument()` + copy）
2. 在副本上执行 `CodeStyleManager.reformatText()`
3. 使用 diff 算法比较原文档和格式化后的文档
4. 生成 `TextEdit[]` 返回给客户端

**Decision**: 使用 `DocumentImpl` 创建临时文档，避免修改原文件

---

### Code Actions (QuickFix)

**API 入口**:

- `com.intellij.codeInsight.intention.IntentionManager`
- `com.intellij.codeInsight.daemon.impl.ShowIntentionsPass`
- `com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction`

**实现策略**:

1. 获取当前位置的诊断信息（已有 `DiagnosticsProvider`）
2. 使用 `IntentionManager.getIntentions()` 获取可用操作
3. 过滤 `priority` 为 `HIGH` 或 `ERROR_PRIORITY` 的 QuickFix
4. 仅返回可转换为 `WorkspaceEdit` 的操作

**Decision**: 初期不支持 `command` 方式，仅返回 `edit` 字段

---

### Go to Implementation

**API 入口**:

- `com.intellij.codeInsight.navigation.actions.GotoImplementationAction`
- `com.intellij.psi.search.searches.DefinitionsScopedSearch`
- `com.intellij.codeInsight.navigation.DefinitionsScopedSearchQuery`

**实现策略**:

1. 获取当前位置的 `PsiElement`
2. 使用 `DefinitionsScopedSearch.search()` 查找实现
3. 处理接口方法和抽象方法
4. 支持 Java ↔ Kotlin 互操作（IntelliJ 内置支持）

**Decision**: 复用现有 `PsiMapper` 进行位置转换

---

### Inlay Hints

**API 入口**:

- `com.intellij.codeInsight.hints.InlayHintsProvider` (2020.3+)
- `com.intellij.codeInsight.hints.InlayHintsSink`
- `com.intellij.codeInsight.hints.declarative.InlayHintsProvider` (2022.3+)

**实现策略**:

1. 遍历 PSI 树收集需要提示的位置
2. 对于类型推断：查找 `val`/`var` 声明，获取推断类型
3. 对于参数名：查找函数调用，获取参数名
4. 转换为 LSP `InlayHint` 格式

**Decision**: 直接 PSI 分析而非使用 `InlayHintsProvider`，避免 Editor 依赖

---

## R2. 技术决策总结

| Area              | Decision                  | Alternatives Considered  | Rationale    |
|-------------------|---------------------------|--------------------------|--------------|
| Signature Help    | PSI 直接分析                  | ParameterInfoHandler     | 避免 Editor 依赖 |
| Workspace Symbols | ChooseByNameContributorEx | AllClassesGotoSearcher   | 更灵活的过滤控制     |
| Formatting        | 临时文档 + diff               | 直接修改                     | 符合 LSP 模型    |
| Code Actions      | IntentionManager + 过滤     | ShowIntentionsPass       | 更简洁的 API     |
| Implementation    | DefinitionsScopedSearch   | GotoImplementationAction | 更底层更可控       |
| Inlay Hints       | PSI 遍历                    | InlayHintsProvider       | 避免 Editor 依赖 |

---

## R3. 依赖项

### IntelliJ Platform Modules

所有功能使用 IntelliJ Platform 内置能力，无需额外依赖：

- `com.intellij.lang` - 语言支持
- `com.intellij.psi` - PSI 框架
- `com.intellij.codeInsight` - 代码智能
- `com.intellij.ide.util` - IDE 工具

### 线程安全

所有 PSI 操作必须在 `ReadAction.compute {}` 中执行，符合 Constitution 约束。
