# 重构计划与待办清单

此文档根据项目当前源码分析生成，旨在指导下一轮重构工作。已保存于仓库以防丢失。

## 1. 名称重复文件
- **DocumentImportManager.kt**
  - 存在于 `data/importer` 和 `data/saf`。
  - 实际使用的是 `data/importer` 版本（注入、ViewModel 依赖、功能丰富），`data/saf` 中的工具类未被任何代码引用。
  - 已删除 `data/saf` 文件以避免混淆，余下 `data/importer` 是唯一入口。

- UI 组件（`ImportProgressIndicator.kt`, `KnowledgeEntryCard.kt`, `SearchBar.kt`）
  - 出现于 `ui/component` 和 `ui/components` 两个目录，需合并至一个统一路径。
  - 当前项目使用了 `com.example.powerai.ui.component` 包中的版本，其内容较为完整。
  - 已清理 `ui/components` 旧副本，所有后续引用应该导入 `ui.component`。


- **KnowledgeRepository.kt**
  - 领域接口位于 `domain/repository`，实现位于 `data/knowledge`。正常，不删除。

- UI 组件（`ImportProgressIndicator.kt`, `KnowledgeEntryCard.kt`, `SearchBar.kt`）
  - 出现于 `ui/component` 和 `ui/components` 两个目录，需合并至一个统一路径。

- Import 模块 (`ImportScreen.kt`, `ImportViewModel.kt`)
  - 分别在 `ui/importer` 与 `ui/screen/importer`，保留 `ui/screen/importer` 并删除老旧的 `ui/importer` 目录。
  - 已删除旧版本，保持包路径一致与其他屏幕文件。


> 这些重复多数因包路径变迁造成，重构时应删除不再使用的版本并在代码中添加 TODO 注释说明。

## 2. 大型/臃肿文件
见 `refactor_line_counts.txt` 前几十行。
这些文件通常承担太多职责，需要拆分：

- `GemmaLocalInference.kt` (~708 行) → 已开始拆分：`GemmaModelLoader.kt`、`GemmaResponseSanitizer.kt` 和 `GemmaInferenceEngine.kt` 已推出；该类现在更轻量并允许注入 `GemmaModelLoaderType` 实现以便测试。所有核心逻辑已有单元测试，剩余 TODO 注释可视为低优先级。

- `MainPages.kt` 已完全拆分，现为占位符文件（`SmartPage.kt`、`KnowledgeResultList.kt`、`LocalResultsPage.kt` 等均迁移完成）。可考虑删除。 
- `AiStreamViewModel.kt`、`AiChatScaffold.kt` 等 ViewModel/Scaffold 仍需进一步拆分。
  - **进展**：HybridViewModel 现已将核心查询逻辑下沉至 UseCase、提取 Gemma 推理到共享 helper，并添加 `lastGemmaJob` 让单元测试能够可靠等待异步调用；Local 分支的 IO 调度已注入测试用 dispatcher。
  - **新增**：`AiStreamUseCase` 中的网络请求构建与 JSON 片段生成已提取到 `AiStreamingService` 与 `AiStreamRequestBuilder`，并移动 JSON 转义到 `JsonUtils`。同时，会话/turn 管理抽出到新的 `ChatSessionManager`，而流文本的缓冲与定时持久化逻辑拆成 `SessionBufferCollator`。相关单元测试补充覆盖。
  - 页面架构继续保持轻量，后续可关注 `AiStreamViewModel` 状态拆分或更多 UI 组件分层。
- `StreamingJsonResourceImporter.kt`、`JsonResourceImporter.kt` 等导入逻辑应归档到 `data/importer` 并拆分处理流程。
  - Parser selection logic centralized via `FileParserFactory`.
  - `ImportUtils` gained `sha256Hex` helper; redundant code removed from manager.
  - Added interface `FileParser` for testable, pluggable parsing.
  - Constructor now accepts injectable `CoroutineScope` and `FileParserFactoryType` to aid unit testing; comprehensive unit tests for `DocumentImportManager.importUri` and `FileParserFactory` added.
- `SparseSearcher.kt`（313 行）和 `KnowledgeLocalSearch.kt` 等检索服务应分解为扫描、tokenize、score、存储等子模块。
  - 诊断与自愈逻辑已抽离到 `LocalSearchDiagnostics.kt`，文件体积进一步缩小。
  - `HybridQueryUseCase` 已添加覆盖其 `invoke`、`aiMode`、`localMode` 和 `hybridQuery` 分支的单元测试。
  - 搜索内部的 tie-break 与 permissive-fallback行为提取到 `SparseSearchUtils.kt`，并为其添加独立单元测试。
  - **部分完成**：日志审计已移至 `SparseSearchAuditLogger`，剩余文件长度已显著减小，可评估是否需要进一步拆分。
- 若干 ViewModel（`HybridViewModel.kt`, `DatabaseViewModel.kt`）可把业务逻辑下沉到 UseCase。
  - `DatabaseUseCase` 现有单元测试覆盖加载、搜索、历史管理等逻辑。

### DI 模块拆分（2026-03-01 完成）
- **AppModule.kt**（297 行）已按功能域拆分为 5 个专注模块：
  - `CoreModule.kt` (54 行) - 核心基础设施（Context、Gson、WorkManager、Dispatcher、Observability 等）
  - `DatabaseModule.kt` (33 行) - Room 数据库和 DAO
  - `NetworkModule.kt` (148 行) - HTTP 客户端和 API 服务（AI、向量搜索、ANN）
  - `DataModule.kt` (62 行) - 数据仓库和导入器
  - `AIModule.kt` (107 行) - AI 推理、检索和向量搜索服务
  - 原 `AppModule.kt` (21 行) 保留为文档和历史标记
- **收益**：
  - ✅ 提高了可维护性：每个模块只关注一个功能域
  - ✅ 提高了可测试性：依赖关系更清晰，易于 mock
  - ✅ 符合单一职责原则：每个模块职责明确
  - ✅ 所有单元测试通过（140 tests，debug + release variants）
  - ✅ 编译成功，无破坏性改动

## 3. 清理建议

1. 根据上表逐项定位并删除冗余/旧文件。
2. 为每个“臃肿”模块添加 TODO 注释，列出具体拆分目标。
3. 在 `DEVELOPER_NOTES.md` 或 Issue 跟踪上述任务：
   - 例如 `#TODO-01: merge components paths`、`#TODO-02: split GemmaLocalInference` 等。
4. 定期使用脚本（如 `scripts/generate_line_counts.ps1`）检查重复和文件大小，作为 CI 检查。
5. 迁移完成后删除此文档或保留为历史记录。

---

> 自动生成报告：
> - `refactor_duplicates.txt` （重复文件列表）
> - `refactor_line_counts.txt` （按行数降序的 Kotlin 文件）

请根据具体模块的业务逻辑和重构优先级自行排定顺序。

```bash
# 生成辅助脚本
powershell -ExecutionPolicy Bypass -File scripts\generate_line_counts.ps1
```

重构工作应在保证编译通过和单元测试覆盖的前提下逐步进行。