# PowerAi — 当前状态快照

> 完成有意义的任务后更新本文件。勿在此写长篇历史，细节见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md) 与 [REFACTOR_PLAN.md](REFACTOR_PLAN.md)。

---

## 1. 项目状态摘要

- **类型**：Android（Kotlin + Compose + Hilt）本地 RAG / 知识库问答应用
- **包名**：`com.example.powerai`
- **Kotlin 源文件**：44 个（`app/src/main/java/`，2026-10-25）；垂直切片后 app 以壳+DI+importer 为主
- **模块**：19 个（见 §3；含 quickask；EmbeddingTest 在 debug）
- **架构**：[ARCHITECTURE.md](ARCHITECTURE.md)；代理协作与安全边界见 [AGENTS.md](AGENTS.md)
- **调试约定**：见 [DEVELOPER_NOTES.md](DEVELOPER_NOTES.md)（如 adb 拉取禁止 shell `>` 重定向，须 `adb pull`）
- **当前**：`origin/main` 已合入 **PR #29**；**整仓离线债清零 Phase D0–D5 完成**（真机验收与 JNI/NDK 史诗外置，见 §8）

## 2. 已验证基线

| 项 | 结果 | 证据 |
|---|---|---|
| 仓库级收口 PR #29 | 已合并 | merge `8af386bc59db5c45832e7155268f7469bbe868c0`；CI JVM + Guardrails + connectedAndroidTest 绿（2026-10-27） |
| Phase C34（模块化收口） | 已完成 | 删无用 `PLog`；确认 Json/importer/`AssetPreloadWorker`/Main 壳留 app 为故意边界；app **44** kt；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C33（Workers → data） | 已完成 | `DataSyncWorker`/`EmbeddingWorker` → `:core:data`；`AssetPreloadWorker` 仍留 app（依赖 DocumentImportManager）；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C32（Vision → data） | 已完成 | `VisionService`/`VisionBoostRepositoryImpl`/`VisionBoostJson`+单测 → `:core:data`；`VisionApiConfig` 解耦 app BuildConfig；DI bind 迁入 data；VisionBoostJsonTest 绿；arch 0 新违规（2026-10-25） |
| Phase C31（空占位清理 + ExportUtils） | 已完成 | 清 app data/local·remote·saf·repository 与 ui 空占位 `.gitkeep`；`ExportUtils` → `:core:data`；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C30（Main Chrome → hybrid） | 已完成 | `MainBottomTab`/`MainBottomBar`/`MainTopBar`/`MainScreenState`/`MainScreenBackHandlers` → `:feature:hybrid`（包名不变）；跨模块 API 公开；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C29（NativeResourceProvider 实现 → data） | 已完成 | `AndroidNativeResourceProvider` → `:core:data`；DI bind 迁入 data `RepositoryModule`；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C28（NativeAnn 调试工具 + 空目录） | 已完成 | `NativeAnnSearcher`/`VectorRepositoryDebugTools`+单测 → `:engine:native`；eval README 随迁；清 app `domain/*` 空占位；compile 绿（2026-10-25） |
| Phase C27（Faiss 原型 → native） | 已完成 | `FaissNative`/`FaissClient`/`LocalFaissAnnRetriever`/`FaissModule` → `:engine:native`；单测随迁；生产 ANN 仍为 NativeAnnRetriever；compile + LocalFaiss 单测绿（2026-10-25） |
| Phase C26（eval → domain） | 已完成 | `domain/eval` 生产源 + 单测迁入 `:core:domain`；Evaluator/LocalRetrievalEvaluation 单测绿；arch 0 新违规（2026-10-25） |
| Phase C25（QueryEncoder + 死域/壳整理） | 已完成 | `QueryEncoder`/`ZeroQueryEncoder` → `:core:domain`；删重复 `KnowledgeMappers` 与无注入 `LlmFactualityScorer`；`MainBottomTab` 独立文件；`AiStreamConfigHelper` 改 public 供跨模块单测；Faiss/AiRuntimeConfig 单测绿（2026-10-25） |
| Phase C24（死 HTTP/向量客户端清理） | 已完成 | 删无用 VectorSearch*/HttpAnnRetriever/AnnApiService/KnowledgeImportUriFlow/AndroidGemmaModelLoader/ImportMemoryBenchmark；NetworkModule 去掉对应 provides；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C23（nav 契约单源） | 已完成 | `Screen.Detail/PdfViewer` 委托 `KnowledgeDetailNavArgs`/`PdfViewerRoute`；AppNavigation Detail args 用 feature 契约；删无用 BingWebSearchClient；ScreenDetailRouteTest 绿（2026-10-25） |
| Phase C22（app 死域清理） | 已完成 | 删无用 DomainLogger/HybridLogic/ResultFormatter/CategoryFilter/ElectricPowerPrompt/RemoteAiRepository/AndroidDomainLogger；清空 screen/chat·document·hybrid；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C21（Tab 宿主 → feature） | 已完成 | `DatabaseTabContent` → `:feature:database`（`onNavigateToKbImport` 回调）；`SearchTabContent` → `:feature:search-chat`；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C20b（MainSearchArea → search-chat） | 已完成 | `MainSearchAndResultsArea` → `:feature:search-chat`（改 `KnowledgeDetailNavArgs`）；删死代码 `MainPager`/`ZoomableImageView`；Main `ui/screen/main` 余 7 文件；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C20a（Main 未用搜索壳清理） | 已完成 | 删未用 `SearchBarStateManager`/`MainSearchBar`/`MainDiagnostics`；Main `ui/screen/main` 余 9 文件；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C19（Main 死代码清理 + DetailTarget） | 已完成 | `KnowledgeDetailTargetResolver`(+单测)→`:feature:knowledge-detail`；删未用 MainHighlightQuery/MainComponents/ImeInsetsBridge；compile + 相关回归绿；arch 0 新违规（2026-10-25） |
| Phase C18b（KnowledgeResultList → hybrid） | 已完成 | KnowledgeResultList/PaginationFooter/AnimatedHelpers → `:feature:hybrid`；去 app theme 依赖；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C18a（Database 抽屉 UI → database） | 已完成 | DatabaseHistory/ImportDiagnostics/KbRebuild/UserKbPackages → `:feature:database`；跨模块入口改 public；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C17b（Chat* UI → search-chat） | 已完成 | Chat*/AiChat*/AiSearch/AiResponse/WebSearchToggle → `:feature:search-chat`；删死代码 AnswerSections/ResponseHeader/ChatDrawer stub；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C17a（ChatHistory 实现 → data） | 已完成 | `ChatHistoryStore`/`ChatHistoryRepositoryImpl` → `:core:data`；DI bind 迁入 data `RepositoryModule`；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C16（AiStream* → search-chat） | 已完成 | AiStream* usecase/UI/VM + ChatSessionManager → `:feature:search-chat`；`AiStreamState`/`ChatModels`/`ChatHistoryRepository` → model-contract；删 UI typealias 去重；VM 去未用 `ChatHistoryStore`；compile + AiStream 相关单测绿；arch 0 新违规（2026-10-25） |
| Phase C15（Smart* → search-chat） | 已完成 | Smart* 迁入 `:feature:search-chat`；`LazyListScrollIndicator`/`ResponseCard`/`SourcesList`→hybrid；`BuildConfig` 改用 `engine.ai`；Detail 导航改 `KnowledgeDetailNavArgs`；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C14（Local/Response 簇 → hybrid） | 已完成 | `Local*` + `ResponseBody*`/`MarkdownTextView`/`AnnotatedTextHelper` + `KnowledgeItemCard`/`TextHighlightUtils`/`EmptyState` → `:feature:hybrid`；compile 绿（2026-10-25） |
| Phase C13（Main 前置：Drawer 壳 + 死代码） | 已完成 | `DrawerWrapper`/`DrawerCardComponents`→`:feature:hybrid`；删除无引用 DocumentPicker 等；compile 绿（2026-10-25） |
| Phase C12（EmbeddingTest → debug） | 已完成 | Activity+layout 迁入 `:feature:debug`；app Manifest 去重；compile 绿；arch 0 新违规（2026-10-25） |
| Phase C11（垂直抽 `:feature:quickask`） | 已完成 | QuickAskScreen 迁入；依赖 hybrid SearchBar + database KnowledgeItemCard；compile 绿（2026-10-25） |
| Phase C10（垂直抽 `:feature:debug`） | 已完成 | 新建模块：Debug 屏/VM；仅依赖 `KnowledgeDao`；compile 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C9（垂直抽 `:feature:mine`） | 已完成 | 新建模块：MineScreen；依赖 `:feature:settings`；dev backdoor 经 `onDevBackdoor` 由 app `MineTabContent` 打开 EmbeddingTestActivity；compile 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C8（垂直抽 `:feature:search`） | 已完成 | 新建模块：Search 屏/VM；依赖 `:feature:hybrid`（SearchBar/`HybridUtils`）+ `RetrievalFusionUseCase`；无 app Port；compile 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C7（垂直抽 `:feature:jsonrepo`） | 已完成 | 新建模块：JsonRepo 屏/VM/组件；`JsonEntry`/`JsonKnowledgeFile`→model-contract；`JsonRepoPort`（`JsonRepository` 实现）；compile 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C6（垂直抽 `:feature:importer`） | 已完成 | 新建模块：KnowledgeImport 屏/VM；`UserKbDirectoryImportPort`（`UserKbPackageImporter` 实现）；PDF 关联导航用 `PdfViewerRoute`；compile 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C5（垂直抽 `:feature:settings`） | 已完成 | 新建模块：Settings 屏/VM/组件；`SettingsImportPort`（`JsonRepository` 实现）解耦 JSON 导入；dev backdoor 经 `onDevBackdoor` 回调由 app 打开 `EmbeddingTestActivity`；compile 绿；`SettingsViewModelTest` 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C4（垂直抽 `:feature:database`） | 已完成 | 新建模块：Database 屏/VM/`DatabaseUseCase`、KnowledgeItemCard/EmptyState；`DatabaseKbPort`+`AppDatabaseKbPort` 解耦 importer；`ImportModels`→model-contract；compile 绿；database 单测绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C3（垂直抽 `:feature:pdf`） | 已完成 | 新建模块：PdfViewer/VM、`PdfKnowledgeUseCase`/`PdfFigureListUseCase`、outline store；`PdfAssociation`/`PdfStorage`→`:core:data`（importer 共用）；依赖 `:feature:knowledge-detail`（bbox/locate）；公开 `PdfLocateTarget`；compile 绿；pdf 单测 + `PdfAssociationTest` + `LocalModeTableEvidence` 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C2（垂直抽 `:feature:knowledge-detail`） | 已完成 | 新建模块：detail 屏/VM、Compose blocks、Markwon、PDF bbox/locate、PhotoViewer；`KnowledgeDetailSupportPort`（`DatabaseUseCase` 实现）；`BlocksJsonPatcher`/`VisionBoost*`/`DetailModels`→model-contract；`FontSettings`→`:core:data`；`SearchTextNormalization`→model-contract；`:feature:knowledge-detail`+`:app` compile 绿；SnapshotUriSelector 单测绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C2-prep（BlocksParser → model-contract） | 已完成 | JVM 侧 `BlocksParser`/`BlocksUtils`/`BlocksIdGenerator`/`BlocksElementExtractors` + `BlocksParserTest` 迁入 `:core:model-contract`（包名不变）；Compose `BlocksRenderer`/`BlockItemComponents` 仍留 `:app`；解除 Pdf usecase → app ui 解析倒置；`BlocksParserTest` 绿；`compileDebugKotlin` 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase D2（EmbeddingIo 死链清理） | 已完成 | 删除已无生产调用的 `EmbeddingIoHelper` + 单测；现行 `EmbeddingRepositoryImpl.enqueueForEmbedding` 写 Room `embedding_metadata`（`:core:data`），不再写 `filesDir/embeddings/pending`；`EmbeddingWorker` 文件批处理路径保留但无自动调度（见 KI-58）；`compileDebugKotlin` 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase D1（WebSearch/History → data 同位） | 已完成 | `WebSearchRepository`/`SearchHistoryRepository`/`SearchEntry`/`HistoryScope` → `:core:model-contract`；Serper 客户端+仓储实现+历史 store → `:core:data`；`WebSearchApiKeyProvider` 解耦 `BuildConfig`；DI 绑在 `core/data`；`:app`/`hybrid`/`data` compile 绿；hybrid **93/93** + data 单测绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase C1（垂直抽 `:feature:hybrid`） | 已完成 | 新建 `:feature:hybrid`：Hybrid 屏/VM/编排、`HybridQueryUseCase`/`AskAi`、历史与 WebSearch 契约、SearchBar/HybridCard；`HybridKbImportPort` 解耦 app importer；`DocumentImportManager` 实现 Port；包名尽量不变；`:feature:hybrid` + `:app` `compileDebugKotlin` SUCCESS；hybrid 单测 **93/93**；`LocalModeTableEvidence`/`LocalSummaryCardFormatting` 绿；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase B5（FTS 同位 + DI） | 已完成 | `RoomFtsRetriever` → `:core:data`；`SemanticRoleSearchTextBuilder` → `:core:domain`；`RepositoryModule` `@Binds FtsRetriever`；`AIModule` 注入 `FtsRetriever` 不再手造 Room 实现；相关单测 **42/42**（HybridRetrieval 5 + TableEvidence 5 + PackageAware 4 + TableLabel 5 + SemanticRole 15 + HybridQuery 8）；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase B4（拆 Gemma 副作用） | 已完成 | 新增 `LocalHybridQueryOrchestrator`（`:core:domain`）承载 localMode 检索+证据+题注改指；`:app` `HybridQueryUseCase` 仅保留 Gemma/AskAi 与对外 API；`compileDebugKotlin` BUILD SUCCESSFUL；相关单测 **23/23**（HybridQuery 8 + TableEvidence 5 + EvidenceRefiner 7 + UiPayload 3）；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase B3（证据簇） | 已完成 | `LocalEvidence*` / `LocalAnswerPlanner` / `LocalExtractiveAnswerBuilder` + 依赖 `PdfSourceRef` / `ContextualSearchTextBuilder` 迁入 `:core:domain`（包名不变）；跨模块调用处放开 Refiner/ExtractiveBuilder/ContextualSearchTextBuilder 可见性；`compileDebugKotlin` BUILD SUCCESSFUL；相关单测 **57/57**（Evidence 7 + AnswerPlanner 14 + Extractive 2 + HybridQuery 8 + TableEvidence 5 + PackageAware 4 + UiPayload 3 + SummaryFactory 14）；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase B2（消检索倒置） | 已完成 | `RetrievalFusionUseCase` 自 `:feature:search-chat` 迁入 `:core:domain`（包名 `domain.retrieval`）；`:app` domain 不再 import `feature.searchchat`；`:core:domain` 依赖 `:engine:ai`（SparseSearcher）；`compileDebugKotlin` BUILD SUCCESSFUL；相关单测全绿（RetrievalFusion 2 + HybridQuery 8 + HybridRetrieval 5 + LocalModeTableEvidence 5 + DeepSeek VM/Stop）；`check_architecture.py` 0 新违规（2026-10-25） |
| Phase B1（`:core:domain`） | 已完成 | `HybridRetrievalService` + `LocalSearchQueryVariantBuilder` 自 `:core:model-contract` 迁入 `:core:domain`（包名不变）；`:app` / `:feature:search-chat` 依赖已接；`compileDebugKotlin` BUILD SUCCESSFUL；相关单测 **18/18**（HybridRetrieval 5 + VariantBuilder 11 + RetrievalFusion 2）；`check_architecture.py` 0 新违规（2026-10-25） |
| PR #1 | 已合并 | merge commit `2c4803f51fdd769b7bb5dd2f14dcff9cd4cbafe2` |
| 已合并 PR head | `7683e9a3c5cb0d0a8bf4c23fa7df0bff14a90240` | `refs/remotes/origin/codex/powerai-stabilization-clean-publication` |
| `origin/main` | `2c4803f51fdd769b7bb5dd2f14dcff9cd4cbafe2` | `git rev-parse origin/main` |
| 表格 `rows` 契约（PowerAi × PaddleModels） | 已加固并通过真实 fixture 验收 | `app/src/test/resources/contracts/paddlemodels_v2_table_rows_contract.json`（3209 B，sha256 `f2f18cb1…d05026`）；`PaddleModelsTableRowsContractTest` 11/11 |
| PDF MVI | 已完成 | `PdfFigureListViewModel` / `PdfKnowledgeViewModel` 继承 `BaseMviViewModel`；契约测试 `PdfFigureListMviContractTest` / `PdfKnowledgeMviContractTest` |
| 真机定位链路 | 已完成 | 真机「DB 列表 → 详情 → 点表格块 → magic window → 在 PDF 中定位」命中原始 PDF **第 29 页 table 1** 与 **第 32 页 table 2**（提交 `d29ea78` 体记录） |
| 搜索结果表格标题定位（`searchLocal` 旧链） | 已退役，规则保留在主路径 | 旧 `KnowledgeRepository.searchLocal`／`KnowledgeLocalSearch` 支线已删除；题注→表格改指保留在 `KnowledgeRepository.resolveTableLabelTargets`（UI 主路径使用）；`TableLabelPdfLocateRegressionTest` 5/5（改为驱动生产 FTS 检索 + resolver） |
| 搜索结果表格标题定位（「本地」hybrid 链） | 已修复并真机验收 | `HybridQueryUseCase.localMode` → `KnowledgeRepository.resolveTableLabelTargets` 复用 `TableLabelBlockResolver`/`KnowledgeTableLabelTargets`，题注命中改指同条目或同页跨条目表格；`LocalModeTableEvidenceRegressionTest` 5/5；真机 Android 16 两条查询黄色框分别落在 PDF 第 29 页 `p29_tbl1`、第 32 页 `p32_tbl1`（见 §9） |
| 本地证据零结果（`发电机允许温升表`） | 已修复 | 根因：`LocalEvidenceRefiner` 仅用 title+markdown 预览评分，而检索命中依赖 indexed block 文本；题注仅在块内的表格条目被以 coverage=0 丢弃 → UI 0 结果。现评分纳入 `contentBlocksJson` 明文 |
| 本地搜索失败语义（错误 vs 真正 0 命中） | 已修复并回归 | `HybridQueryUseCase.localMode` 不再 `catch (Throwable) → emptyList()`；`HybridModeExecutor` 将可恢复的检索/导入错误映射为 `LocalPageState.ERROR`（安全文案、可重试），真正 0 命中仍保留「无匹配」；`CancellationException` 继续传播。`LocalSearchFailureUiStateTest` 5/5、`HybridQueryUseCaseTest` 8/8、`LocalModeTableEvidenceRegressionTest` 5/5 |

| v2 对象根真正流式导入 | 已修复并验证 | `importFromJson` 的 `streamObject` 改为 `JsonReader` 逐字段 / 逐 entry 读取，不再整根 `JsonParser().parse`；尾部不可读前先落首批；取消不再被吞或误报成功。`StreamingJsonObjectRootStreamingTest` 4/4；仓库外完整 KB 经生产链读回 48 entries / 839 blocks / FTS 48，source+docSha256 与第 29/32 页表格 rows/cells/bbox/定位目标与旧整根映射逐字节一致 |
| KB 资产导入原子性 | 已修复并验证 | 单文件导入包进一个 SQLite 事务（`KnowledgeDao.runInTransaction`）：成功才提交 knowledge 行 + FTS + imported 标记；读取/解析失败或取消整体回滚，不残留可搜索半包、不标 imported，重试同一 fileId 成功。`KbImportAtomicityRoomTest` 5/5（Room 生产链）；未改 Room schema |
| KB 导入不静默部分成功 | 已修复并验证 | `StreamingJsonResourceImporter` 不再 `catch (elemEx: Throwable)` 后仅 trace 继续：entry 解析/映射/批写与 `rebuildFts()` 的真实失败向上抛出、经单文件事务回滚；非对象 entry 视为无效并失败（不再无声跳过）；重复 entry 仍按既有语义去重跳过。取消继续传播。`KbImportAtomicityRoomTest` 11/11（新增批写失败/FTS 失败/无效 entry 对象根与数组根/数组根尾部失败/重复跳过）；未改 Room schema |
| 内置 KB 资产更新闭环 | 已修复并验证 | 同一 asset 路径内容变化时按**内容指纹**识别新版本：`imported_files.contentSha256`（流式 SHA-256）决定跳过/更新；`knowledge.packageId`（= asset fileId）标注条目归属，单文件事务内 `deleteByPackageId` → 重导入 → `rebuildFts` → 写新指纹与标记。未变内容跳过且零写入；失败/取消回滚保留旧版条目/FTS/标记；用户导入行（null）与其他包（即使 source/docSha256 相同）不受影响；迁移前 legacy 资产（无指纹）不自动替换。Room 5→6 **仅加列**（`packageId`、`contentSha256`），旧数据保留。`KbAssetUpdateReplaceRoomTest` 3/3、`KbAssetUpdateResilienceRoomTest` 3/3、`AppDatabaseMigration5To6Test` 1/1；仓库外 158 页 KB 经生产链读回 48 entries / 839 blocks / 48 FTS、第 29/32 页表格与 PDF source 不变 |
| 内置 KB 旧库重建 + 失败重试闭环 | 已修复并验证 | ① `DocumentImportManager.importAssetsIfNeed` 返回 `AssetImportOutcome`；`AssetPreloadWorker` 单资产失败 → `Result.retry`（不再误报 success），`CancellationException` 继续传播。② 数据库页「重试导入」真正重跑生产导入（此前只刷新诊断）。③ 新增用户主动「重建内置知识库」：仅确认后**单事务**清除本应用 `knowledge` + FTS + `imported_files`，失效 `vision_cache`/`embedding_metadata`（按知识条目 id），并在提交后**同一进程内清空原生向量索引**（`VectorRepository.clear()`，`NativeVectorRepository` Java 层串行化 `search`/`upsert`/`clear`）并删除应用私有 `vector_index.bin`（磁盘删除失败不报完整成功）；再由当前内置资产重建；状态区分 running/success/failed/cancelled，失败整体回滚可重试。`KbRebuildRoomTest` 5/5、`KbRebuildVectorIndexTest` 4/4（重建前旧 ID 可检索、重建后同进程不再出现）、`AssetPreloadWorkerTest` 4/4、`DatabaseViewModelRebuildTest` 5/5（真实 Room + 生产编排 + ViewModel/Mockito）。外部 158 页 KB 经 `ExternalKbReadBackTest` 读回 48 entries / 839 blocks / 48 FTS，第 29/32 页表格断言通过 |
| Smart/DeepSeek 生成停止闭环 | 已修复并 JVM 验证（fake engine） | `DeepSeekViewModel.abortGeneration` 真正停止：取消运行中的 generation job 并调用 `PowerAIEngine.stopGeneration()`；`isRunActive` 顺序纠正后委托 `DeepSeekSessionManager.isRunActive(runToken, sessionId)`，停止后迟到 chunk/最终结果/完成回调被拒；`generateGroundedAnswer` 在 `prepareForNextGeneration` 之后mint runToken，修掉 token 被提前失效。`DeepSeekGenerationStopTest` 8/8（真实 ViewModel+编排器+SessionManager+fake engine）。**真机模型生成尚未验证** |
| 内置 KB 重建/诊断行数（PDF 源） | 已修复并真机验证 | `AssetImportScanner.countImportedRows` 原先只用资产目录 `source` 前缀统计；PDF 源内置资产的 `fileMetadata.source` 为 `pdf:{sha}::{name}`（不含资产路径），整包已导入也会被统计成 **0 行**，导致重建成功文案显示「0 条」、诊断误标「已导入但 0 条」。现改为优先按 `knowledge.packageId`（资产导入写入的包归属）统计，`source` 前缀仅作迁移前 legacy 行回退；新增 `KnowledgeDao.countByPackageId`（Room 仅加查询，不改 schema）。真机 Android 16 重建后 UI「重建完成：48 条 · 1 个资源」、诊断「已导入 48」，与 Room 48/48、blocks 839 一致（修复前为 0 条）。`KbRebuildRoomTest` 增加成功行数断言 |
| 用户目录 KB 包导入（无需重打包） | 已实现并验证（JVM + 仓库外样本） | 新增正式界面「导入知识库目录」（`KnowledgeImportScreen` + `ImportViewModel`，系统 `OpenDocumentTree` 选择器）：读取原样 `knowledge_base.json`，`shots/*` 相对引用复制到 `filesDir/kb_user/<包>/<内容指纹>/` 并把块内 `imageUri/src/images[]` 重写为私有 `file://`（源目录移走仍可显示）。**复用** `StreamingJsonResourceImporter`（新增可选 `imageUriRewriter` 构造参数与 `JsonImageRefRewriter` 原地重写 entry 图片引用，未造第二解析器/搜索链）；`packageId = "user:" + sha256(目录标识)`，与内置 `asset:` 包隔离；内容指纹复用 `imported_files.contentSha256`（未变化跳过；同目录 A→B 只替换本包并清理旧版本，不碰内置/其他用户包/手工文档）。先落文件后提交（单事务），失败/取消回滚并清理暂存；相对路径校验拒绝穿越/绝对/缺失/损坏 KB。新增 `KnowledgeDao.getFirstByPackageId`（仅加查询，Room 5→6 不变）；PDF 关联抽出共享 `PdfAssociation`（SHA-256 校验，不匹配不关联），`PdfViewerScreen` 复用。仓库外 103号 KB 经生产链读回 **48 entries / 839 blocks / 48 FTS / 42 资产可解析**，第 29/32 页表格 bbox 命中。测试：`UserKbPackageImporterTest` 11/11、`KbResourcePathTest` 2/2、`PdfAssociationTest` 2/2、`JsonImageRefRewriterTest` 3/3、`UserKbDirectoryAcceptanceTest`（外部样本 opt-in）1/1 |
| 用户包生命周期 + 重建隔离（本阶段） | 已实现并验证（JVM 真实 Room + 真机） | ①「重建内置知识库」改为只处理**可确认归属**的内置包（`packageId` 指向当前打包资产；内置 ID 是 `sha256("asset:"+path)` **裸哈希**，非 `asset:` 前缀）：用户包、手工导入行、无指纹 legacy 行与无法确认归属的裸哈希包全部保留；②新增**单独**的「清空全部知识…」入口（`DocumentImportManager.clearAllKnowledgeBases`，两步确认）清除全部 knowledge/FTS/imported_files + id-keyed 缓存后重导内置资产；③「用户知识库」区块列出已导入包的名称/条目数/版本时间/PDF 关联，支持「更新（选同一目录，未变化跳过）」与确认后「移除」单包——移除单事务删本包 `knowledge`+FTS+标记 + 本包 id 的 vision/embedding 缓存，提交后清本包私有镜像与派生向量索引；仅接受 `user:` 包，失败/取消保留旧版并如实报错；④重建/清空/移除只在确有替换时失效向量索引。新增/改：`KbRebuildScope`、`KbCacheInvalidation`、`KnowledgeDao.getIdsByPackageId/deleteImportedFile`、`VisionCacheDao.deleteForEntities`、`EmbeddingDao.deleteByIds`、`UserKbPackageImporter.listPackages/removePackage`、`DatabaseUserPackageCoordinator`、`UserKbPackagesUiState`。**未改 Room schema（仍 v6）**。测试：`KbRebuildIsolationTest` 2/2、`KbClearAllRoomTest` 5/5、`KbRebuildVectorIndexTest` 4/4、`UserKbPackageImporterTest` 17/17、`DatabaseViewModelRebuildTest` 8/8 |
| 用户包生命周期真机验收（本阶段） | 已验证（真机 M2007J17C / Android 16，覆盖安装未清数据） | 现场含 `user:` 包「PowerAiKb103」（48 条/42 shots）与内置/遗留包；界面「重建内置知识库」后：用户包 48 条仍在、`imported_files` 标记保留、FTS 239 含用户行、42 shots 文件与 `imageUri` 全部存在、`p29_tbl1`/`p32_tbl1` 块仍在；无法确认归属的裸哈希包（accept103，48 条）被保留。另用临时小包走界面「导入（2 条）→ 更新（内容未变化，已跳过）→ 移除（2 条条目 + 镜像一并清除）」，内置与真实用户包不受影响 |

> §6 的 JVM 单测数值为 2026-10-03 在本分支工作树**实测**（`testDebugUnitTest`）；其余数值取自既有机器产物。

## 3. 模块与架构

`settings.gradle.kts` 当前 19 个模块（目录均存在）：

| 模块 | 职责 |
|------|------|
| `:app` | 导航、Hilt 装配、导入编排（含 Ports）、资产扫描、SAF/URI、剩余 UI（Main 壳为主） |
| `:core:model-contract` | 契约/模型：`KnowledgeRepository`、`BlocksParser`、`ImportModels`、`JsonEntry`/`JsonKnowledgeFile`、`WebSearch`/`SearchHistory`、`ChatModels`/`AiStreamState`/`ChatHistoryRepository` |
| `:core:domain` | 检索/证据/评测：HybridRetrieval、RetrievalFusion、LocalEvidence*、`LocalHybridQueryOrchestrator`、SemanticRole、`QueryEncoder`、`domain.eval`（B1–B5/C25–C26） |
| `:core:data` | `KnowledgeEntity`、仓储、`RoomFtsRetriever`、WebSearch/History、ChatHistory、`FontSettings`、`PdfAssociation`/`PdfStorage`、JSON KB 导入核心 |
| `:engine:native` | native 源码与 CMake（FAISS、llama JNI、NEON 搜索） |
| `:engine:ai` | 推理引擎封装与 JNI 桥接 |
| `:feature:hybrid` | Hybrid 主屏垂直切片：VM/编排、`HybridQueryUseCase`、本地/智能 UI |
| `:feature:knowledge-detail` | 知识详情垂直切片：Detail VM/UI、Markwon、Compose blocks、PDF locate、PhotoViewer |
| `:feature:pdf` | PDF 查看垂直切片：Viewer/VM、page knowledge/figure usecase、outline |
| `:feature:database` | 数据库/KB 管理垂直切片：Database VM/UI、`DatabaseUseCase`；经 `DatabaseKbPort` 依赖 app 导入 |
| `:feature:settings` | 设置垂直切片：Settings VM/UI；经 `SettingsImportPort` 依赖 app `JsonRepository` |
| `:feature:importer` | 用户 KB 目录导入垂直切片：Import VM/UI；经 `UserKbDirectoryImportPort` 依赖 app importer |
| `:feature:jsonrepo` | JSON 知识文件管理垂直切片：JsonRepo VM/UI；经 `JsonRepoPort` 依赖 app `JsonRepository` |
| `:feature:search` | 简单词法搜索垂直切片：Search VM/UI；`RetrievalFusionUseCase` + hybrid SearchBar |
| `:feature:mine` | 「我的」页垂直切片：MineScreen；依赖 settings；dev backdoor 由 app 回调 |
| `:feature:debug` | 调试：Debug VM/UI + `EmbeddingTestActivity` |
| `:feature:quickask` | 快速问答原型屏；hybrid SearchBar + database KnowledgeItemCard |
| `:feature:search-chat` | 搜索与对话：DeepSeek + Smart* + AiStream* + Chat* UI |
| `:benchmark` | Macrobenchmark |

应用内分层仍为 `ui` → `domain` ← `data`；DI 为 `app/src/main/java/com/example/powerai/di/` 下 7 个 `@Module`。KB 目录规范见 [KB_TAXONOMY_GUIDE.md](KB_TAXONOMY_GUIDE.md)。

## 4. 搜索、AI 与 Native 状态

- **词法检索**：`KnowledgeEntity.contentNormalized`/`searchContent` → `RoomFtsRetriever`（FTS），经 `HybridRetrievalService` RRF 融合（旧 `KnowledgeLocalSearch` 支线已于 2026-10-03 退役）
- **Query**：`QueryUnderstandingPipeline` → `HybridRetrievalService` / `AnnRetriever`（native → local → HTTP）
- **融合与作答**：`RetrievalFusionUseCase`、`LocalEvidenceRefiner`、`LocalAnswerPlanner`、`AiStreamUseCase`
- **端侧引擎**：`PowerAIEngine`（位于 `engine/ai`），DeepSeek / Gemma 经 `DeepSeekNativeRuntimeBridge` 选择与回退
- **知识包**：`KbManifest` 支持外部知识包的独立版本管理
- **Native 实现位置**：`engine/native/src/main/cpp/`（`native_search.cpp`、`native_search_faiss.cpp`、`llama_jni.cpp`、`CMakeLists.txt`、`faiss/`）；`app` 模块**无** `externalNativeBuild`
- **ABI**：`app/build.gradle.kts` 的 `ndk.abiFilters` 仅 **`arm64-v8a`**
- **KB assets**：`app/src/main/assets/kb/`（已跟踪 27 个文件）；`llama_library/` 未纳入本分支且已被 `.gitignore` 忽略

## 5. UI/MVI 迁移状态

- 全仓 **13 个具体 ViewModel**（`:app` 5 + hybrid 1 + knowledge-detail 1 + pdf 2 + database 1 + search-chat 2），**13 个**均继承 `BaseMviViewModel`
- Feature VM：Hybrid / KnowledgeDetail / Pdf* / Database / DeepSeek / AiStream
- PDF：`PdfFigureListViewModel`、`PdfKnowledgeViewModel` **已完成 MVI 迁移**（State/Intent/Effect + use case；契约测试 `PdfFigureListMviContractTest`、`PdfKnowledgeMviContractTest`）
- 用户目录导入：`KnowledgeImportScreen` + `ImportViewModel`（继承 `BaseMviViewModel`，`KnowledgeImportUiState` 区分 idle/running/success/skipped/failed/cancelled），已注册导航路由 `Screen.KbImport`，入口位于「数据库」抽屉「用户知识库」区块
- 用户包管理（本阶段）：抽屉「用户知识库」区块（`UserKbPackagesUiState.kt` / `UserKbPackagesBlock`）列出每个 `user:` 包的名称/条目数/版本时间/PDF 关联，提供「更新（选同一目录）」与确认后「移除」；「内置知识库」区块（`KbRebuildUiState.kt`）提供隔离的「重建内置知识库」与两步确认的「清空全部知识…」。状态经 `DatabaseUserPackageCoordinator` / `DatabaseImportCoordinator` 落到 `DatabaseUiState`（`pendingRemovePackageId`、`userPackageMessage`、`clearAllStep`）
- 复现统计：`find … -name "*ViewModel.kt" ! -name "BaseMviViewModel.kt"` 计总数，再 `grep BaseMviViewModel` 计已迁移数
- **BlocksParser**：`type: "figure"` 映射为 **`FigureNodeBlock`**（`ui/blocks/BlocksParser.kt`）；`semanticRole: figure_callout` **不会**被自动过滤——`BlocksParserTest` 明确断言按 `semanticRole` 保留 code 块；表格 `rows` 契约：仅 array 型 `rows`（含空数组）优先，历史整数 `rows` 不再遮蔽 `table_rows`，`cells` 不再被当作二维 rows，由 `PaddleModelsTableRowsContractTest` 以 PaddleModels 真实生产 fixture 锁定

## 6. 测试与 CI 证据

| 项 | 结果 | 证据与时间 | 限制 |
|----|------|-----------|------|
| JVM 单测（`:app`，CI 范围） | **477 tests / 0 failures / 0 errors / 2 skipped**（103 个测试类） | `app/build/test-results/testDebugUnitTest/TEST-*.xml`，2026-10-04（`testDebugUnitTest`） | 跨模块合计 **577 tests / 0 failures**（另 `core:data` 25 / `core:model-contract` 7 / `engine:ai` 35 / `feature:search-chat` 33）；2 skip 为外部样本 opt-in（`ExternalKbReadBackTest`、`UserKbDirectoryAcceptanceTest`），设 `POWERAI_KB_FILE` / `POWERAI_USER_KB_DIR` 后实测 **48/839/48** 与 **48/839/42** 通过 |
| instrumentation（app） | **3/3** | PR 合并门禁结果；`android-instrumentation-tests.yml` 执行 `:app:connectedDebugAndroidTest`（`api-level: 35`，x86_64 模拟器），`app/src/androidTest` 3 个测试类各 1 个 `@Test` | **限制**：合并前 CI 结果，本批未复跑；仅在 API 35 模拟器验证（`arm64-v8a`-only 本地库需 ARM translation），**Android 16 / API 36 仪器测试未覆盖** |
| Android lint | **0 errors**，既有 **66 warnings** | 合并前 lint 报告 `lint-results-debug.xml`（2026-09-24 23:31） | warnings 未清零，此处不逐条复制 |
| CI 任务范围 | `test` job：`:app:ktlintMainSourceSetCheck` + `:app:compileDebugKotlin` + `:core:data:compileDebugKotlin` + `:app:testDebugUnitTest` + `:core:data:testDebugUnitTest`；`quality` job：`detekt detektAllFindings ktlintCheck` + 3 个 python 守卫；`benchmarks` job 受 `vars.RUN_BENCHMARKS` 控制，默认不执行 | `.github/workflows/ci.yml` | 配置现状，本批未修改 |
| 质量门禁（本阶段本地复跑） | **通过** | `detekt` + `ktlintCheck` = `BUILD SUCCESSFUL`；`scripts/source_size_report.py --check`（494 files / 43913 lines，detekt baseline 1248）、`scripts/check_architecture.py`（0 新违规；baseline 3 known duplicates）、`scripts/check_ktlint_changed_lines.py --base origin/main`（0 violations on changed lines），2026-10-04 | — |

### JDK / toolchain / 字节码

| 层 | 当前值 | 来源 |
|----|--------|------|
| Gradle daemon JVM | **21** | `gradle/gradle-daemon-jvm.properties` → `toolchainVersion=21` |
| GitHub Actions `setup-java` | **21**（3 处：`ci.yml` ×2、`android-instrumentation-tests.yml` ×1） | workflow 的 `java-version` |
| 字节码目标 | **17**（7 个模块均为 `JavaVersion.VERSION_17` + `jvmTarget = "17"`） | 各模块 `build.gradle.kts` |
| Kotlin | 2.1.10 | `gradle/libs.versions.toml` |

运行 JDK 21 与字节码目标 17 并存，属**当前配置现状**（非 17/21 不一致）；本批未改动任何配置。

## 7. 已知限制

- `benchmarks` CI job 默认不执行（依赖 `vars.RUN_BENCHMARKS`）
- `llama_library/` 预置库未入库，需本地准备
- lint 66 个 warning 未清零
- instrumentation 仅在 API 35 x86_64 模拟器验证；**Android 16 / API 36 仪器测试未覆盖**（`compileSdk` 36，本地库仅 `arm64-v8a`）

## 8. 下一步工作

### 垂直切片收口计划（连续执行，不逐刀请示）

| Phase | 状态 | 说明 |
|-------|------|------|
| **C11** quickask | ✅ | `:feature:quickask` |
| **C12** EmbeddingTest | ✅ | 迁入 `:feature:debug` |
| **C13** Main 前置 | ✅ | Drawer 壳→hybrid；删死组件 |
| **C14** Local/Response | ✅ | Local* + Response/Markdown/Card/EmptyState → hybrid |
| **C15** Smart* | ✅ | Smart* → search-chat；Scroll/ResponseCard/SourcesList → hybrid |
| **C16** AiStream* | ✅ | AiStream* + ChatSessionManager → search-chat；chat 模型/Repository 契约 → model-contract |
| **C17a** ChatHistory 实现 | ✅ | Store/Impl → `:core:data`（同位 WebSearch/History） |
| **C17b** Chat* UI | ✅ | Chat*/AiChat*/AiSearch/AiResponse → search-chat；清 AnswerSections 等死代码 |
| **C18a** Database 抽屉 | ✅ | DatabaseHistory/KbRebuild/UserKb → `:feature:database` |
| **C18b** KnowledgeResultList | ✅ | KnowledgeResultList/Pagination/AnimatedHelpers → hybrid |
| **C19** DetailTarget + 死代码 | ✅ | KnowledgeDetailTargetResolver → knowledge-detail；删 MainHighlight/MainComponents/Ime |
| **C20a** Main 未用搜索壳 | ✅ | 删 SearchBarStateManager/MainSearchBar/MainDiagnostics；main 余 9 文件 |
| **C20b** MainSearchArea | ✅ | MainSearchAndResultsArea → search-chat；删 MainPager/ZoomableImageView；main 余 7 |
| **C21** Tab 宿主 | ✅ | DatabaseTabContent → database；SearchTabContent → search-chat |
| **C22** app 死域清理 | ✅ | 删无用 domain util/usecase/repository 与空 screen 目录 |
| **C23** nav 契约单源 | ✅ | Screen.Detail/Pdf 委托 feature nav；删 BingWebSearchClient |
| **C24** 死 HTTP/向量客户端 | ✅ | 删 VectorSearch*/HttpAnn/AnnApi 与无用 loader/benchmark |
| **C25** QueryEncoder + 死域 | ✅ | QueryEncoder → core:domain；删 KnowledgeMappers/LlmFactualityScorer；MainBottomTab 拆分 |
| **C26** eval → domain | ✅ | domain/eval 生产+单测 → `:core:domain` |
| **C27** Faiss 原型 → native | ✅ | FaissNative/Client/LocalFaiss/FaissModule → `:engine:native` |
| **C28** NativeAnn 调试 + 空目录 | ✅ | NativeAnnSearcher/DebugTools → native；清 domain 占位 |
| **C29** NativeResourceProvider 实现 | ✅ | AndroidNativeResourceProvider → `:core:data` |
| **C30** Main Chrome | ✅ | Tab/BottomBar/TopBar/State/BackHandlers → `:feature:hybrid` |
| **C31** 空占位 + ExportUtils | ✅ | 清空目录；ExportUtils → `:core:data` |
| **C32** Vision → data | ✅ | Vision* + VisionApiConfig；bind → `:core:data` |
| **C33** Workers → data | ✅ | DataSync/Embedding → data；AssetPreload 留 app |
| **C34** 模块化收口 | ✅ | 删 PLog；残量边界冻结；垂直切片本批结束 |

### app 故意残留（收口边界，非未完成债）

| 簇 | 说明 |
|----|------|
| NavHost / MainActivity / theme | 应用壳与导航 |
| Hilt DI modules | 组合根；Ports 绑定 importer/Json |
| importer + JsonRepository | 导入/解析实现；feature 经 Port 依赖 |
| `AssetPreloadWorker` | 绑定 `DocumentImportManager` |
| Main 壳 3 文件 | `MainScreen`/`MainFeatureContent`/Mine·Quiz stubs（跨 feature 编排） |
| Glide/`AssetModelLoader`/`AppConfig`/`PreviewTemplates` | 应用级配置与资源加载 |

### 整仓债清零（Phase D）

> 本轮清零 = 离线可验收的代码/文档债。真机验收与 KI-21 JNI 史诗不伪完成。细则见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

| Phase | 项 | 状态 |
|-------|----|------|
| — | PR #29 合入 + CI 绿 | ✅ 完成 |
| D0 | 台账校准（关 KI-05/10/22；KI-21 外置；KI-56/50–52 接受限制） | ✅ 完成 |
| D1 | KI-58 退役 EmbeddingWorker 死链 | ✅ 完成（删 Worker + instrumented；enqueue 仅 metadata） |
| D2 | 全量 ktlint → 0 + CI 强制 | ✅ 完成（ignoreFailures=false；editorconfig 收口特例） |
| D3 | KI-53 乱码；KI-20 拆 `docs/debug_lan.md`；KI-03/04 接受冻结 | ✅ 完成 |
| D4 | KI-57 书面接受「重选目录」关闭 | ✅ 后由 E1 实现一键 treeUri 取代 |
| D5 | 真机清单（停止/中文/KI-54/API36） | ✅ 清单已写；**真机执行仍待验** |
| E1 | KI-57 持久化 treeUri + 一键更新 | ✅ Room 7 `sourceTreeUri`；UI「一键更新」/「重选目录」 |
| 外置 | KI-21 JNI 真 runtime；KI-56 NDK per-id 删；真机验收执行 | 史诗 / 待真机 |

## 9. 历史里程碑（历史性质，不代表当前状态）

| 日期 | 事项 |
|------|------|
| 2026-03-01 | DI 按域拆分；重复包 `ui.components`、`ui.importer`、`data.saf` 已清理 |
| 2026-05-27 | Agent 治理框架（`AGENTS.md` + Memory）建立 |
| 2026-08-17 | MVI 重构启动并覆盖绝大多数 ViewModel（当前进度见 §5） |
| 2026-09-12 | `DeepSeekViewModel` 逻辑交由 Orchestrator；`LlamaJni` 拆为 Native/Reflection Bridge、Controller、Metrics（均在 `engine/ai`） |
| 2026-09-19 | 依赖方向治理、`KbManifest` 引入、Version Catalog 全面迁移（`gradle/libs.versions.toml`） |
| 2026-10-02 | 表格 `rows` 契约加固：array `rows` 优先、整数 `rows` 不遮蔽 `table_rows`、cells 矩形重建；纳入 PaddleModels 真实 fixture 契约测试 |
| 2026-10-02 | 状态基线校正：PDF MVI 已完成（PR #9，两个 PDF ViewModel → `BaseMviViewModel`）；真机验证详情表格块 → PDF 定位命中第 29/32 页（`d29ea78`） |
| 2026-10-02 | PDF 定位视口修复：页面列表底部预留视口高度，目标页跳转不再被 `scrollToItem` 钳制；`PdfRenderer` 渲染串行化；新增定位回归测试（instrumentation + JVM） |
| 2026-10-02 | 表格标题块命中修正：本地搜索结果落在表格标题/题注块（而非表格块）时，详情块选择与「在 PDF 中定位」改指其所属表格块；同条目优先，跨条目按同页邻接推断并入表格条目。新增 JVM 回归 `TableLabelPdfLocateRegressionTest` |
| 2026-10-02 | 「本地」hybrid 链表格证据闭环：题注→表格改指接入真实 UI 检索/证据链（`HybridQueryUseCase.localMode` → `KnowledgeRepository.resolveTableLabelTargets`，复用 `TableLabelBlockResolver`/`KnowledgeTableLabelTargets`，支持跨条目同页几何最近）；修复 `LocalEvidenceRefiner` 仅按 markdown 预览评分导致 `发电机允许温升表` UI 0 结果（现纳入 indexed block 文本）。新增 `LocalModeTableEvidenceRegressionTest`（经 `localMode` + 详情/PDF 目标解析）；真机 Android 16 复验第 29/32 页黄色框命中 |
| 2026-10-02 | 「本地」搜索失败语义修复：明确区分「检索/导入失败」与「确实 0 命中」。`HybridQueryUseCase.localMode` 停止吞异常（含 `CancellationException` 继续传播）；`HybridModeExecutor` 将可恢复错误写入可重试的 `LocalPageState.ERROR`（安全文案，不含异常详情）；`LocalSearchArea` 在失败时展示失败提示与「重试」。新增 `LocalSearchFailureUiStateTest`（失败/导入失败/取消竞态/成功有结果/成功零结果） |
| 2026-10-03 | 本地检索主路径收敛（退役旧支线）：删除无生产调用的 `LocalSearchUseCase`、`VectorSearchRepository`、`RetrievalFusionService`（含 `AIModule` 无用 Hilt Provider）及 `KnowledgeRepository.searchLocal` 与 `KnowledgeLocalSearch`/`KnowledgeLocalSearchQuery`/`KnowledgeLocalSearchStrategies`/`KnowledgeLocalSearchProcessor`/`LocalSearchDiagnostics`/`KnowledgeSnippetBuilder`。「本地」页统一走 `HybridModeExecutor → HybridQueryUseCase → RetrievalFusionUseCase → HybridRetrievalService`。保留 `resolveTableLabelTargets` 题注→表格规则与 `LocalPageState.ERROR`/取消语义 |
| 2026-10-03 | v2 KB 真正流式导入：`StreamingJsonResourceImporter.streamObject` 从整根 `JsonParser().parse` 改为 `JsonReader` 按根字段/entry 逐项读取（未知根字段安全跳过；`entries` 先于 `fileMetadata` 的有界兼容：先 flush 再按 stable id 回填 `fileMetadata.source`）。`DocumentImportManager` 与 flow 均重新抛出 `CancellationException`。新增 `StreamingJsonObjectRootStreamingTest`（尾部不可读前已落首批 / entries-first 保留 source / 未知根字段 / 取消传播）；仓库外完整 KB 逐字节等价读回 |
| 2026-10-03 | KB 导入原子性：`DocumentImportManager.importAssetsIfNeed` 将单文件导入（含 FTS 重建与 imported 标记）包进 `dao.runInTransaction`，失败/取消整体回滚；`StreamingJsonResourceImporter` 失败改为向调用方抛出而非吞成 failed 进度。新增 Room 生产链 `KbImportAtomicityRoomTest`（有效首次 / 两批后尾部失败 / 取消 / 同 fileId 重试 / 旧同 fileId 数据与无关 KB 保护）；仓库外完整 KB 经 manager 链读回 48/839/48 与第 29/32 页表格目标不变 |
| 2026-10-03 | KB 导入不静默部分成功：移除 `StreamingJsonResourceImporter` 数组根/对象根 entry 循环里吞掉 `elemEx` 后继续的 `catch`，并移除 `dao.rebuildFts()` 的非取消吞错——单条 entry 解析/映射/批写与 FTS 重建失败改为抛出，由 PR #20 的单文件事务回滚；非对象 entry 视为无效失败而非无声跳过；重复 entry 保持既有去重跳过（测试明确）。`KbImportAtomicityRoomTest` 扩到 11/11；仓库外完整 KB 经 manager 链读回 48/839/48、第 29/32 页表格目标不变 |
| 2026-10-03 | Smart/DeepSeek 生成停止闭环：`DeepSeekViewModel.isRunActive` 由恒 `true` 改为按接口顺序委托 `DeepSeekSessionManager.isRunActive(runToken, sessionId)`；`abortGeneration` 取消 generation job 并调用 `engine.stopGeneration()`，置 `STOPPING→CANCELLED`；`generateGroundedAnswer` 在 `prepareForNextGeneration` 之后mint runToken；编排器异常路径在运行已失效时归类为取消而非「推理失败」，并确保流式 collector 在所有退出路径被取消。UI「停止生成」按钮显隐改为 `canAbortGeneration`（进行中生成才可见）。`DeepSeekGenerationStopTest` 8/8（真实 ViewModel+编排器+SessionManager+fake engine）；未改推理算法/提示词/模型路径 |
| 2026-10-03 | KB 导入核心归属 `:core:data`：`StreamingJsonResourceImporter` 及其解析/映射/批写、`JsonResourceParser`、`JsonEntryMapper`、`ImportUtils`/`ImportDefaults`/`ImportProgress`、`MarkdownTableNormalizer`/`MarkdownTableUtils`、`MemoryKnowledgeDao` 迁入 `core/data/importer`；`:app` 保留 `DocumentImportManager`、资产扫描与 SAF/URI 适配。直测随迁入 `:core:data`，Room/app 编排集成测留在 `:app`；无反向依赖、无双重生产入口；仓库外 103号 KB 读回迁移前后逐字节一致（135 entries / 148 blocks / 135 FTS） |
| 2026-10-03 | 内置 KB 资产更新闭环：新增 `imported_files.contentSha256`（流式 SHA-256 内容指纹）与 `knowledge.packageId`（= asset fileId 归属）；`DocumentImportManager` 在**同一 asset 路径内容变化**时以单文件事务替换**仅本包**旧条目（`deleteByPackageId` → 重导入 → `rebuildFts` → 写指纹与标记），未变内容跳过且零写入，失败/取消回滚保留旧版条目/FTS/标记，用户导入行与其他包（即使 source/docSha256 相同）不受影响；URI 导入抽到 `UriDocumentImporter`。Room 5→6 **仅加列**（`packageId`、`contentSha256`）。新增 `KbAssetUpdateReplaceRoomTest`/`KbAssetUpdateResilienceRoomTest`/`AppDatabaseMigration5To6Test`/`ExternalKbReadBackTest`；仓库外 158 页 KB 经生产链读回 48 entries / 839 blocks / 48 FTS。迁移前 legacy 资产（无指纹）不自动替换（见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)） |
| 2026-10-03 | 内置 KB 旧库重建 + 失败重试：`importAssetsIfNeed` 返回 `AssetImportOutcome`，`AssetPreloadWorker` 单资产失败改为 `Result.retry`（取消继续传播）；数据库页「重试导入」真正重跑生产导入；新增用户主动「重建内置知识库」（`DocumentImportManager.rebuildBuiltInKnowledgeBase` + `DatabaseImportCoordinator` + `KbRebuildBlock`）：仅确认后单事务清除本应用 knowledge/FTS/imported_files 并失效 `vision_cache`/`embedding_metadata`/私有 `vector_index.bin`，再由内置资产重建，状态区分 running/success/failed/cancelled。新增 `KbRebuildRoomTest` 5/5、`AssetPreloadWorkerTest` 4/4、`DatabaseViewModelRebuildTest` 5/5；移除 16 个无引用的旧 DOCX `assets/images/*_rId*` 抽取图 |
| 2026-10-04 | 内置 KB 重建/诊断行数按包归属：修复 PDF 源（`pdf:{sha}::{name}`）内置资产在「重建内置知识库」成功文案与「导入诊断」中误报 0 条；`KnowledgeDao.countByPackageId` + `AssetImportScanner.countImportedRows` 优先按 `knowledge.packageId` 统计、回退 legacy 源前缀；`KbRebuildRoomTest` 增加成功行数断言。真机 Android 16：重建后 UI「重建完成：48 条 · 1 个资源」、诊断「已导入 48」，Room 48/48、blocks 839 |
| 2026-10-04 | 用户目录 KB 包导入（无需重打包）：新增正式界面「导入知识库目录」（系统目录选择器，`Screen.KbImport` + `KnowledgeImportScreen`/`ImportViewModel`）；复用 `StreamingJsonResourceImporter`（新增可选 `imageUriRewriter` 重写块内图片引用，不造第二解析器/搜索链）；`packageId = "user:" + sha256(目录标识)` 与内置 `asset:` 隔离，内容指纹复用 `imported_files.contentSha256`（未变化跳过、A→B 仅替换本包并清理旧版本）；被引用截图复制到 `filesDir/kb_user/<包>/<内容指纹>/` 并重写为私有 `file://`（源目录移走仍可显示）；先落文件后提交事务，失败/取消回滚清理；相对资源路径校验拒绝穿越/绝对/缺失/损坏。新增 `KnowledgeDao.getFirstByPackageId`、共享 `PdfAssociation`（SHA-256 校验，不匹配不关联，`PdfViewerScreen` 复用）；新增测试 `UserKbPackageImporterTest`/`KbResourcePathTest`/`PdfAssociationTest`/`JsonImageRefRewriterTest`/`UserKbDirectoryAcceptanceTest`；仓库外 103号样本读回 48/839/48 且 42 资产可解析、第 29/32 页表格 bbox 命中 |
| 2026-10-04 | 用户 KB 包生命周期（查看/更新/移除）+ 重建隔离：`KbRebuild` 引入 `KbRebuildScope`——「重建内置知识库」只按 `packageId`（裸哈希 `sha256("asset:"+path)`）处理**可确认归属**的内置包并保留用户包/手工导入/legacy/无法确认的裸哈希包；新增独立「清空全部知识…」（`clearAllKnowledgeBases`，两步确认）作为全删入口；`UserKbPackageImporter` 增 `listPackages`/`removePackage`（单事务删本包行 + FTS + 标记 + 本包 id 的 vision/embedding 缓存，提交后清本包私有镜像与派生向量索引，仅接受 `user:` 包，失败/取消保留旧版并如实报错）；抽屉「用户知识库」区块列出名称/条目数/版本时间/PDF 关联并提供「更新（选同一目录，未变化跳过）」「移除」；新增 `KbCacheInvalidation`、`KnowledgeDao.getIdsByPackageId/deleteImportedFile`、`VisionCacheDao.deleteForEntities`、`EmbeddingDao.deleteByIds`、`DatabaseUserPackageCoordinator`、`UserKbPackagesUiState`；`KbRebuildIsolationTest`/`KbClearAllRoomTest`/`KbRebuildVectorIndexTest`/`UserKbPackageImporterTest`/`DatabaseViewModelRebuildTest` 全绿；未改 Room schema（v6）。真机 Android 16 覆盖安装复验：用户包 48 条/42 shots/FTS/第 29、32 页表格在「重建内置」后不变，临时小包走界面导入→更新（跳过）→移除全部通过 |
