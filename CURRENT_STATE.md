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
| 搜索结果表格标题定位（`searchLocal` 旧链） | 已修复并回归 | `searchLocal` 命中表格标题/题注块时，详情块选择与「在 PDF 中定位」改指所属表格块；`TableLabelPdfLocateRegressionTest` 5/5 |
| 搜索结果表格标题定位（「本地」hybrid 链） | 已修复并真机验收 | `HybridQueryUseCase.localMode` → `KnowledgeRepository.resolveTableLabelTargets` 复用 `TableLabelBlockResolver`/`KnowledgeTableLabelTargets`，题注命中改指同条目或同页跨条目表格；`LocalModeTableEvidenceRegressionTest` 5/5；真机 Android 16 两条查询黄色框分别落在 PDF 第 29 页 `p29_tbl1`、第 32 页 `p32_tbl1`（见 §9） |
| 本地证据零结果（`发电机允许温升表`） | 已修复 | 根因：`LocalEvidenceRefiner` 仅用 title+markdown 预览评分，而检索命中依赖 indexed block 文本；题注仅在块内的表格条目被以 coverage=0 丢弃 → UI 0 结果。现评分纳入 `contentBlocksJson` 明文 |
| 本地搜索失败语义（错误 vs 真正 0 命中） | 已修复并回归 | `HybridQueryUseCase.localMode` 不再 `catch (Throwable) → emptyList()`；`HybridModeExecutor` 将可恢复的检索/导入错误映射为 `LocalPageState.ERROR`（安全文案、可重试），真正 0 命中仍保留「无匹配」；`CancellationException` 继续传播。`LocalSearchFailureUiStateTest` 5/5、`HybridQueryUseCaseTest` 8/8、`LocalModeTableEvidenceRegressionTest` 5/5 |

> §6 的 JVM 单测数值为 2026-10-02 在本分支工作树**实测**（`testDebugUnitTest`）；其余数值取自既有机器产物。

## 3. 模块与架构

`settings.gradle.kts` 当前 7 个模块（目录均存在）：

| 模块 | 职责 |
|------|------|
| `:app` | Compose UI、ViewModel、导航、Hilt 装配、Room 与 importer |
| `:core:model-contract` | `KnowledgeRepository`、`PowerAIEngine`、blocks 模型、MVI 基类 |
| `:core:data` | `KnowledgeEntity`、仓储实现与本地检索 |
| `:engine:native` | native 源码与 CMake（FAISS、llama JNI、NEON 搜索） |
| `:engine:ai` | 推理引擎封装与 JNI 桥接 |
| `:feature:search-chat` | 搜索与对话功能域 |
| `:benchmark` | Macrobenchmark |

应用内分层仍为 `ui` → `domain` ← `data`；DI 为 `app/src/main/java/com/example/powerai/di/` 下 7 个 `@Module`。KB 目录规范见 [KB_TAXONOMY_GUIDE.md](KB_TAXONOMY_GUIDE.md)。

## 4. 搜索、AI 与 Native 状态

- **词法检索**：`KnowledgeEntity.contentNormalized` + `KnowledgeLocalSearch`
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
| JVM 单测（`:app`，CI 范围） | **426 tests / 0 failures / 0 errors / 0 skipped**（97 个测试类） | `app/build/test-results/testDebugUnitTest/TEST-*.xml`，2026-10-02（`testDebugUnitTest`） | 跨模块合计 **493 tests**（另 `core:model-contract` 7 / `engine:ai` 35 / `feature:search-chat` 25），全部 0 failures |
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
- 端侧推理停止行为与中文输出的真机回归 —— **待验证**
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
