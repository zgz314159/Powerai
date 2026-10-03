# PowerAi — 当前状态快照

> 完成有意义的任务后更新本文件。勿在此写长篇历史，细节见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md) 与 [REFACTOR_PLAN.md](REFACTOR_PLAN.md)。

---

## 1. 项目状态摘要

- **类型**：Android（Kotlin + Compose + Hilt）本地 RAG / 知识库问答应用
- **包名**：`com.example.powerai`
- **Kotlin 源文件**：338 个（`app/src/main/java/`）；全模块合计 463 个
- **模块**：7 个（见 §3）
- **架构**：[ARCHITECTURE.md](ARCHITECTURE.md)；代理协作与安全边界见 [AGENTS.md](AGENTS.md)
- **调试约定**：见 [DEVELOPER_NOTES.md](DEVELOPER_NOTES.md)（如 adb 拉取禁止 shell `>` 重定向，须 `adb pull`）

## 2. 已验证基线

| 项 | 结果 | 证据 |
|---|---|---|
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

> §6 的 JVM 单测数值为 2026-10-03 在本分支工作树**实测**（`testDebugUnitTest`）；其余数值取自既有机器产物。

## 3. 模块与架构

`settings.gradle.kts` 当前 7 个模块（目录均存在）：

| 模块 | 职责 |
|------|------|
| `:app` | Compose UI、ViewModel、导航、Hilt 装配、导入编排（`DocumentImportManager`）、资产扫描、SAF/URI 入口与 importer 装配 |
| `:core:model-contract` | `KnowledgeRepository`、`PowerAIEngine`、blocks 模型、MVI 基类 |
| `:core:data` | `KnowledgeEntity`、仓储实现、本地检索与 JSON KB 导入核心（`core/data/importer`） |
| `:engine:native` | native 源码与 CMake（FAISS、llama JNI、NEON 搜索） |
| `:engine:ai` | 推理引擎封装与 JNI 桥接 |
| `:feature:search-chat` | 搜索与对话功能域 |
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

- 全仓 **12 个具体 ViewModel**（`:app` 11 + `:feature:search-chat` 1），**12 个**均继承 `BaseMviViewModel`
- `DeepSeekViewModel` **已完成迁移**（位于 `:feature:search-chat`），`DeepSeekIntent.kt` 存在并被 UI 调用
- PDF：`PdfFigureListViewModel`、`PdfKnowledgeViewModel` **已完成 MVI 迁移**（State/Intent/Effect + use case；契约测试 `PdfFigureListMviContractTest`、`PdfKnowledgeMviContractTest`）
- 复现统计：`find … -name "*ViewModel.kt" ! -name "BaseMviViewModel.kt"` 计总数，再 `grep BaseMviViewModel` 计已迁移数
- **BlocksParser**：`type: "figure"` 映射为 **`FigureNodeBlock`**（`ui/blocks/BlocksParser.kt`）；`semanticRole: figure_callout` **不会**被自动过滤——`BlocksParserTest` 明确断言按 `semanticRole` 保留 code 块；表格 `rows` 契约：仅 array 型 `rows`（含空数组）优先，历史整数 `rows` 不再遮蔽 `table_rows`，`cells` 不再被当作二维 rows，由 `PaddleModelsTableRowsContractTest` 以 PaddleModels 真实生产 fixture 锁定

## 6. 测试与 CI 证据

| 项 | 结果 | 证据与时间 | 限制 |
|----|------|-----------|------|
| JVM 单测（`:app`，CI 范围） | **446 tests / 0 failures / 0 errors / 1 skipped**（97 个测试类） | `app/build/test-results/testDebugUnitTest/TEST-*.xml`，2026-10-03（`testDebugUnitTest`） | 跨模块合计 **543 tests**（另 `core:data` 22 / `core:model-contract` 7 / `engine:ai` 35 / `feature:search-chat` 33），全部 0 failures。唯一 skip 为 `ExternalKbReadBackTest`（未设 `POWERAI_KB_FILE`；设样本后实测 48/839/48 通过） |
| instrumentation（app） | **3/3** | PR 合并门禁结果；`android-instrumentation-tests.yml` 执行 `:app:connectedDebugAndroidTest`（`api-level: 35`，x86_64 模拟器），`app/src/androidTest` 3 个测试类各 1 个 `@Test` | **限制**：合并前 CI 结果，本批未复跑；仅在 API 35 模拟器验证（`arm64-v8a`-only 本地库需 ARM translation），**Android 16 / API 36 仪器测试未覆盖** |
| Android lint | **0 errors**，既有 **66 warnings** | 合并前 lint 报告 `lint-results-debug.xml`（2026-09-24 23:31） | warnings 未清零，此处不逐条复制 |
| CI 任务范围 | `test` job：`:app:ktlintMainSourceSetCheck` + `:app:compileDebugKotlin` + `:app:testDebugUnitTest`；`benchmarks` job 受 `vars.RUN_BENCHMARKS` 控制，默认不执行 | `.github/workflows/ci.yml` | 配置现状，本批未修改 |

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

- 维持 lint error 为 0 —— **待验证**（下一次 lint 运行）
- 端侧推理停止行为与中文输出的真机回归 —— **待验证**（停止闭环已用 fake engine 在 JVM 确定性验收；真机模型未验证）
- 内置 KB 旧库重建的真机验收（确认弹窗、running/success/failed/cancelled 状态、失败重试）—— **待验证**（JVM + 真实 Room 已确定性验收；真机 UI 未验证）
- 补齐 Android 16 / API 36 的仪器测试覆盖 —— **未开始**

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
