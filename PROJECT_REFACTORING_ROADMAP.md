# PowerAi 全项目重构执行路线图

> 生成日期：2026-09-26  
> 审计基线：PR #2 head `347b53a09cd6e40957508fdc199ac626436136be`  
> 对应远端 `main` merge commit：`135b3ffe37d7279bb77c4a9ce3151fb29e2f619a`  
> 文档性质：后续重构的唯一执行路线图。现有 `REFACTOR_PLAN.md` 作为历史记录，不再作为当前待办清单。

## 1. 结论摘要

PowerAi 已完成模块化构建、CI 修复和发布稳定化，但尚未完成系统性的文件级与职责级重构。

当前主要问题不是单纯的文件行数，而是以下问题叠加：

1. `app` 仍承载约 75% 的主源码，模块化迁移尚未收口。
2. 部分服务、DTO 和工具类在 `app`、`core`、`engine` 中存在双实现或迁移兼容壳。
3. 5 个主源码文件超过 400 行，14 个超过 300 行；多个核心函数超过 100 行。
4. UI、ViewModel、domain 算法和反射兼容逻辑的风险性质不同，不能用统一的“按行切文件”方式处理。
5. 250 个 JVM 测试全部从 `app` 模块执行，抽出的 `core`、`engine`、`feature` 模块没有模块内单元测试，代码所有权与测试所有权不一致。
6. Detekt 与 ktlint 当前为非阻断配置，Detekt 只挂在 `app`，现有 Detekt 报告为空，无法约束新增复杂度。

因此，正确顺序是：先建立保护网和单一事实来源，再拆 ViewModel/UI，随后治理 domain、importer、engine，最后收口模块边界。

## 2. 审计基线

### 2.1 模块规模

| 模块 | 主源码文件 | 主源码行数 | 单元测试文件 | 说明 |
|---|---:|---:|---:|---|
| `:app` | 338 | 30,607 | 74 | 主源码约占全项目 74.7%，职责过重 |
| `:core:model-contract` | 34 | 2,204 | 0 | 同时包含模型、查询实现和 MVI 基类，名称与职责不完全一致 |
| `:core:data` | 26 | 1,406 | 0 | 已承接 Room、仓储和本地检索的一部分 |
| `:engine:ai` | 46 | 4,132 | 0 | 推理、流式服务、反射与兼容逻辑集中 |
| `:engine:native` | 3 | 252 | 0 | JNI/Native Android 包装层 |
| `:feature:search-chat` | 16 | 2,212 | 0 | 已承接 DeepSeek，但搜索/聊天 UI 仍大量留在 `app` |
| `:benchmark` | 2 | 60 | 0 | Macrobenchmark |

补充基线：

- 主源码 Kotlin/Java：466 个文件，40,979 行。
- JVM 测试：74 个测试文件，71 个 XML 测试类，250 tests。
- Android instrumentation：3 个测试类，3 tests。
- `app` 详情页包：65 个文件、4,797 行，属于包过密，不属于单文件过长。
- `app` 主页面包：60 个文件、7,162 行，职责分组仍不清晰。
- `engine.ai` 包：43 个 Kotlin 文件、约 4,054 行，反射、Gemma、Llama、streaming 混在同一包。

### 2.2 长文件分布

| 门槛 | 文件数 | 合计行数 |
|---|---:|---:|
| `>= 150` 行 | 84 | 20,072 |
| `>= 200` 行 | 50 | 14,171 |
| `>= 250` 行 | 27 | 8,991 |
| `>= 300` 行 | 14 | 5,400 |
| `>= 400` 行 | 5 | 2,268 |
| `>= 500` 行 | 1 | 596 |

行数只用于筛选，不作为机械拆分依据。算法文件可以较长但仍保持内聚；UI 和 ViewModel 即使低于 400 行，也可能因状态、导航和副作用混合而需要优先处理。

### 2.3 质量风险指标

- `TODO/FIXME` 文本：27 处，其中多项是已完成工作的过期注释。
- 空 `catch`：约 205 处。
- 捕获 `Throwable`：约 408 处。
- 直接使用 `Dispatchers.IO`：约 71 处。
- 正文中的全限定 `com.example.powerai...` 引用：约 597 处，反映迁移后导入和边界尚未收口。
- typealias 兼容壳：4 处。
- `domain` 生产源码直接导入 Android API：0，当前纯 domain 边界值得保留。
- `feature:search-chat` 仍通过包名 `com.example.powerai.ui.mvi` 使用实际位于 `core:model-contract` 的 MVI 基类，包名与模块归属不一致。

## 3. 关键问题清单

### 3.1 P0：重复实现与迁移未收口

以下问题应早于大文件拆分处理，否则拆分后会继续复制两套逻辑。

| 对象 | 当前状态 | 目标 |
|---|---|---|
| `AiStreamingService` | `app/domain/ai` 与 `engine/ai` 有两份近似实现 | `engine:ai` 保留唯一实现，`app` 仅依赖 repository/contract |
| `JsonUtils` | `app/domain/common` 与 `core/model-contract` 重复 | 保留 core 单一实现，迁移调用后删除 app 版本 |
| `Cancellable` | app 与 core 两个相同接口；实际调用已使用 core | 删除 app 未使用版本 |
| Chat DTO | app `ChatModels.kt` 与 core DTO 部分重复 | 网络 DTO 归 core/engine；会话模型单独命名并保留在正确层 |
| `AiApiService` | app 与 engine 各自定义兼容 OpenAI 的接口 | 判断是否共享契约；若业务不同则重命名为 Vision/Chat 专用 API，避免同名漂移 |
| `AiStreamState` | UI typealias 指向 app domain model | 设定兼容期限，调用迁完后删除别名 |
| `NativeResourceProvider`、`PowerAIEngine` | engine typealias 指向 core contract | 可短期保留，但必须有移除条件和零引用验收 |
| `KnowledgeDetailMarkdownHighlight.kt` | 4 行占位文件 | 验证零引用后删除 |

### 3.2 P1：高风险长文件

| 文件 | 行数 | 主要问题 | 建议拆分结果 | 测试现状 |
|---|---:|---|---|---|
| `ui/screen/database/DatabaseScreen.kt` | 596 | 单个 Composable 约 296 行，混合列表、定位、匹配、anchor、文案 | `DatabaseScreen`、`DatabaseContent`、`DatabaseListNavigator`、`DatabaseAnchorFactory` | 无直接测试 |
| `domain/util/SemanticRoleSearchTextBuilder.kt` | 433 | profile、角色收集、figure 信号、query 匹配、文本拼装混合 | 保留 facade；提取 analyzer、figure extractor、text assembler | 无直接测试，先补 golden tests |
| `ui/screen/main/LocalSummaryCard.kt` | 424 | UI、诊断格式化、gate 文案、citation 混合 | Card、Diagnostics、CitationStrip、Formatter | 有格式化测试，覆盖不完整 |
| `ui/screen/main/SmartPage.kt` | 413 | 页面、evidence 分段、anchor、citation 导航混合 | Page、EvidenceContent、EvidenceNavigation | 无直接测试 |
| `ui/screen/hybrid/HybridViewModel.kt` | 402 | intent、模式执行、反馈、历史、import、Gemma job 协调 | VM 只保留 reducer/intent；提取 mode executor 与 feedback coordinator | 3 个相关测试文件 |
| `feature/search-chat/DeepSeekBenchmarkOrchestrator.kt` | 394 | 三种 benchmark 流程重复，delegate 接口过宽 | 通用 runner + thread/batch/batch-thread scenario | 无直接测试 |
| `domain/query/QueryRewritePlanner.kt` | 391 | normalization、意图规则、action/definition 变体、alias 混合 | facade + normalizer + intent pattern + variant generator | 有 1 个测试文件，需补边界样本 |
| `ui/screen/main/ResponseBodyComponents.kt` | 369 | streaming、markdown、annotated、structured、actions 混合 | streaming、structured text、actions 三组 | 无直接测试 |
| `ui/screen/database/DatabaseViewModel.kt` | 366 | load/search/import/focus/history/state persistence 混合 | load coordinator、focus coordinator、history store；VM 仅 reducer | 有 1 个测试文件 |
| `engine/ai/LlamaJniReflection.kt` | 340 | 一个约 175 行反射解包函数，大量静默 catch | class introspector、callback proxy、instance resolver | 无直接测试，必须先建 fake reflection fixtures |
| `feature/search-chat/DeepSeekViewModel.kt` | 337 | 生成、grounding、设置、benchmark、session 混合 | VM reducer + generation coordinator + benchmark adapter | 无直接测试 |
| `ui/screen/main/MainScreen.kt` | 306 | 60 个 import，根 Composable 约 206 行 | shell、navigation binding、tab state；不再承载业务 UI | 无直接测试 |

### 3.3 P1：超长函数而非超长文件

优先治理以下函数：

- `DatabaseScreen`：约 296 行。
- `MainScreen`：约 206 行。
- `SmartPage`：约 194 行。
- `LlamaJniReflection.unwrapMapForInstance`：约 175 行。
- `LocalDiagnosticsFormatter.build`：约 170 行。
- `DatabaseHistoryDrawerContent`：约 162 行。
- `SmartDeepSeekTabContent.submitSmartQuery`：约 157 行。
- 多个 detail/table/response Composable：120 至 142 行。
- `BlocksParser.parseBlockObject`：约 133 行。
- `DocumentImportManager.importAssetsIfNeed`：约 111 行。
- `StreamingJsonResourceImporter.importFromJson`：约 109 行。

### 3.4 P2：模块所有权不完整

当前依赖图没有 Gradle 循环，但职责仍偏向 `app`：

```text
app
  -> core:model-contract
  -> core:data
  -> engine:native
  -> engine:ai
  -> feature:search-chat

feature:search-chat -> core:model-contract, core:data, engine:ai
core:data          -> core:model-contract
engine:ai          -> core:model-contract
engine:native      -> core:model-contract
```

主要问题：

1. `app` 同时拥有 UI、domain use case、Android data/importer、网络 API 和 DI。
2. `core:model-contract` 不仅是 contract，还包含 query 实现和 `BaseMviViewModel`。
3. `feature:search-chat` 已存在，但 Smart/Local/Response UI 大量留在 `app/ui/screen/main`。
4. `core:data` 已存在，但 app 中仍有 importer、repository、json、vision 等数据实现。
5. 所有 JVM 测试都集中在 `app`，被抽取模块无法独立证明自身行为。

目标不是立刻新增大量模块。先在现有模块内收敛包和依赖，再根据稳定边界创建 `core:domain`、`core:presentation` 或新的 feature 模块。

### 3.5 P2：包过密和命名债务

- `ui.screen.main` 有 60 个文件，应按 `shell`、`local`、`smart`、`response`、`history` 形成明确子域。
- `ui.screen.detail` 有 65 个文件，应按 `blocks`、`markdown`、`table`、`media` 分组；不要继续机械拆成更多同包小文件。
- `engine.ai` 应按 `streaming`、`deepseek`、`gemma`、`llama`、`reflection` 分包。
- 主源码中 `mapper` 与 `mappers` 的历史问题在生产路径已基本收敛，但测试包仍有旧命名，应随测试归属迁移处理。

### 3.6 P2：异常与并发策略不统一

大量 `catch (_: Throwable) {}` 出现在反射、native fallback、日志和检索路径。不能全局替换，但必须分为：

1. 明确可忽略的 cleanup/best-effort 操作。
2. 应转为 typed result 的业务失败。
3. 应记录并向 UI 暴露的用户可见失败。
4. 绝不能吞掉的 cancellation、OOM、linkage 和初始化错误。

`Dispatchers.IO` 应通过已有 qualifier 或 dispatcher provider 注入，尤其是 ViewModel、UseCase 和 importer；底层 Android adapter 可保留明确调度策略。

## 4. 目标架构

### 4.1 最终职责

| 层/模块 | 最终职责 |
|---|---|
| `app` | Application、Activity、导航、顶层 Hilt 装配、Android-only adapter |
| `core:model-contract` | 稳定 DTO、repository contract、engine contract；不放 ViewModel |
| `core:domain`（候选） | 纯 Kotlin use case、query、semantic、retrieval policy |
| `core:presentation`（候选） | MVI contract、BaseMviViewModel、通用 presentation state |
| `core:data` | Room、repository 实现、import pipeline、local search、持久化 |
| `engine:ai` | streaming、DeepSeek、Gemma、Llama runtime 与 API adapter |
| `engine:native` | JNI/CMake 和 native repository adapter |
| `feature:search-chat` | Search/Chat/Smart 的 UI、ViewModel、feature coordinator |
| `feature:database`（候选） | Database UI、VM、navigation helper |
| `feature:pdf`（候选） | PDF viewer、figure/knowledge MVI、PDF-specific presentation |

只有在包内职责稳定、依赖方向单向、测试已跟随代码后，才创建候选模块。禁止“先建空模块，再把混合职责整包搬过去”。

### 4.2 依赖原则

```text
feature/* -> core:presentation -> core:domain -> core:model-contract
feature/* -> core:data / engine:* 仅通过 contract 或明确 facade
core:data -> core:domain + core:model-contract
engine:*  -> core:model-contract
app       -> feature/* + adapters + DI
```

- domain 不依赖 Compose、Activity、View、Android Context。
- feature 不依赖 app 包。
- data/engine 不依赖 UI。
- 同一业务概念只能有一个生产实现。
- typealias 只能作为有期限的迁移桥，并必须有删除门槛。

## 5. 分阶段执行方案

## Phase 0：建立可执行保护网

目标：让后续每次拆分都能被自动验证，而不是依赖“编译通过”。

工作项：

1. 新增统一源码体量报告，覆盖全部生产模块，而非只统计 app。
2. 修正 Detekt 配置：启用可产生复杂度指标的 processors/rules。
3. Detekt 覆盖 `app`、`core:*`、`engine:*`、`feature:*`。
4. 先建立 baseline，禁止新问题增长；不要一次清零历史问题。
5. ktlint 与 Detekt 先采用“修改文件阻断”，稳定后再全仓阻断。
6. 增加架构检查：feature 不得 import app，domain 不得 import Android/UI。
7. 将本路线图中的规模指标写入可重复脚本。

建议门槛：

- 新增生产文件不超过 300 行。
- 修改后的生产文件原则上不超过 350 行；超过须在 PR 中说明。
- 新增函数不超过 80 行。
- 新增 ViewModel 不直接使用 `Dispatchers.IO`。
- Detekt baseline 不增长。

验收：质量任务可在 CI 稳定运行，且不改变产品行为。

## Phase 1：建立单一事实来源

建议拆成 3 个提交：

1. 删除未使用的 app `Cancellable`；调用统一使用 core contract。
2. `JsonUtils` 统一到 core，迁移 `AiStreamRequestBuilder` 后删除 app 版本。
3. 合并 `AiStreamingService`；梳理 Chat DTO 与两个 `AiApiService` 的命名/所有权。

约束：

- 不在同一提交中搬模块并改变协议字段。
- 为 JSON escape/unescape、SSE chunk、DONE marker、request body 增加 characterization tests。
- 保持外部 API 与序列化字段兼容。

完成标准：已识别的重复实现只剩一个权威实现；兼容 typealias 有明确删除清单。

## Phase 2：补齐高风险重构前测试

在改生产结构前，补以下测试：

1. `SemanticRoleSearchTextBuilder`：role、figure、fallback、query matching golden cases。
2. `DatabaseScreen` 纯函数：定位、fallback score、anchors、collapsed groups、meta line。
3. `SmartPage`：evidence segmentation、citation index、limit 与 label。
4. `DeepSeekBenchmarkOrchestrator`：三个 scenario 的顺序、状态和 summary。
5. `LlamaJniReflection`：fake class、map unwrap、callback proxy、default return。
6. `DeepSeekViewModel`：intent、generation、abort、benchmark delegation。
7. 两个 PDF ViewModel：重复加载、空结果、页切换、figure/table 去重。

测试应迁到代码所有者模块；短期无法迁移时可先留 app，但后续模块迁移必须同步移动。

## Phase 3：ViewModel 与 MVI 收口

### 3.1 PDF

- 将 `PdfFigureListViewModel`、`PdfKnowledgeViewModel` 迁到 `BaseMviViewModel`。
- 定义 `State/Intent/Effect`。
- 将 DAO/repository 查询封装为 use case。
- 注入 dispatcher，避免直接 `Dispatchers.IO`。

### 3.2 Hybrid

- `HybridViewModel` 只保留 intent 路由、state reduction、effect 发射。
- 提取 `HybridModeExecutor` 负责 Local/AI/Smart 模式调用。
- 提取 `LocalAnswerFeedbackCoordinator`。
- 保留现有 `RuntimeSupport`、`SideEffectCoordinator`，避免重新发明相同抽象。
- 去除仅为测试暴露的 mutable job，改为可观测执行状态或测试 dispatcher。

### 3.3 Database

- 提取 `DatabaseLoadCoordinator`：load/search/import retry。
- 提取 `DatabaseFocusCoordinator`：item/group focus 和恢复。
- 搜索历史归独立 store/use case。
- ViewModel 仅管理 state、intent 和生命周期。

目标：三个主要 ViewModel 各自不超过约 250 行，且业务分支由纯函数或 use case 覆盖。

## Phase 4：Compose 长函数拆分

按用户交互边界拆分，不按视觉小块机械拆分。

1. `DatabaseScreen`
   - 屏幕 scaffold 与状态订阅。
   - 内容/列表渲染。
   - 焦点与滚动 side effect。
   - 纯导航/anchor/匹配 helper。
2. `SmartPage`
   - 页面状态与列表。
   - evidence segment renderer。
   - citation/anchor navigation。
3. `LocalSummaryCard`
   - 卡片容器。
   - diagnostics formatter。
   - discarded gate UI。
   - citation strip。
4. `MainScreen`
   - shell 与 navigation binding。
   - tab/pager state。
   - 不再内联 feature 业务 UI。
5. `ResponseBodyComponents`
   - streaming content。
   - structured/annotated content。
   - actions。

验收重点：状态恢复、列表定位、点击导航、折叠组、streaming cursor、citation 行为不变。

## Phase 5：Domain 算法拆分

### 5.1 Semantic role

保留 `SemanticRoleSearchTextBuilder` 作为兼容 facade，内部拆为：

- `SemanticRoleAnalyzer`
- `FigureSemanticSignalExtractor`
- `SemanticSearchTextAssembler`
- `SemanticTextNormalizer`

### 5.2 Query rewrite

保留 `QueryRewritePlanner` 对外 API，内部拆为：

- `QueryNormalizer`
- `QueryIntentPatterns`
- `ActionRuleVariantGenerator`
- `DefinitionVariantGenerator`
- `QueryAliasRewriter`

这些文件虽然长，但算法内聚度较高。只有在 golden tests 固定行为后才拆，不追求最低行数。

## Phase 6：Importer 与 data 所有权

1. 将 importer 明确分为 `scan -> parse -> normalize -> map -> write`。
2. `DocumentImportManager` 只负责 orchestration 和进度。
3. `JsonResourceImporter`、`StreamingJsonResourceImporter` 共享 mapper 和批量提交策略。
4. Android `ContentResolver/Uri` 适配与纯解析分开。
5. 将适合的 app `data` 实现迁到 `core:data`，测试同步迁移。
6. `data/mapper` 命名统一，不再保留 `mapper/mappers` 双路径。

迁移提交与行为修复提交分开，数据库 schema 与 migration 不在纯结构重构中修改。

## Phase 7：Engine 与反射边界

1. `LlamaJniReflection` 拆为：
   - `LlamaClassIntrospector`
   - `LlamaCallbackProxyFactory`
   - `LlamaInstanceResolver`
   - `LlamaReflectionDiagnostics`
2. 将大量 `catch Throwable` 分类为 recoverable、diagnostic、fatal。
3. `DeepSeekBenchmarkOrchestrator` 使用共享 benchmark runner 和三个 scenario。
4. `DeepSeekViewModel` 只保留 intent/state，benchmark 与 generation 下沉 coordinator。
5. `engine.ai` 按 streaming/deepseek/gemma/llama/reflection 分包。

反射/JNI 重构必须保留兼容入口，并以 fake reflection tests、单元测试和真机 smoke test 三层验证。

## Phase 8：模块边界收口

在前述包级重构完成后评估并实施：

1. 建立 `core:domain`，承接纯 use case、query、semantic、retrieval policy。
2. 建立 `core:presentation`，承接 MVI contract 和 base ViewModel。
3. 将 Search/Chat/Smart UI 与 VM 收口到 `feature:search-chat`。
4. 根据依赖稳定度决定是否建立 `feature:database`、`feature:pdf`。
5. `app` 最终只保留 composition root、navigation 和 Android adapter。
6. 将 app 中针对 core/engine 的测试迁到所属模块。

阶段目标：`app` 主源码占比从约 74.7% 降至 55% 以下，同时不引入 feature 间横向依赖。

## Phase 9：治理收尾

1. 删除确认零引用的 placeholder 和过期 typealias。
2. 清理已完成 TODO 和“log removed per TODO”类历史注释。
3. 更新 `KNOWN_ISSUES.md`，关闭已完成项。
4. 将旧 `REFACTOR_PLAN.md` 标记为历史，并链接本路线图。
5. 更新 `CURRENT_STATE.md`、`ARCHITECTURE.md`。
6. Detekt/ktlint 从 baseline 模式逐步转为 CI 阻断。
7. 为模块依赖和文件规模增加持续门禁。

## 6. 推荐执行批次

| 批次 | 主题 | 建议范围 | 前置依赖 |
|---|---|---|---|
| R01 | 质量 baseline | Detekt、体量脚本、架构检查 | 无 |
| R02 | 无争议重复清理 | Cancellable、JsonUtils、占位文件 | R01 |
| R03 | AI streaming 单一实现 | 两个 AiStreamingService、DTO、API 命名 | R02 |
| R04 | 高风险 characterization tests | semantic、database helper、smart evidence | R01 |
| R05 | DatabaseScreen 拆分 | database UI 和纯 helper | R04 |
| R06 | DatabaseViewModel 重构 | load/focus/history coordinator | R04 |
| R07 | PDF MVI | 两个 PDF ViewModel | R01 |
| R08 | HybridViewModel 收口 | mode executor、feedback coordinator | 现有 Hybrid tests |
| R09 | Smart/Local/Main UI | SmartPage、LocalSummaryCard、MainScreen | R04、R08 |
| R10 | Semantic/Query 算法 | 两个 facade 与内部组件 | R04 |
| R11 | Engine reflection/benchmark | Llama reflection、DeepSeek benchmark | Phase 2 tests |
| R12 | Import pipeline | importer、mapper、transaction | importer tests |
| R13 | 模块迁移 | core:domain、core:presentation、features | R02-R12 |
| R14 | 文档与强门禁 | docs、Detekt/ktlint CI | 全部 |

每个批次应控制在一个根因和一个可回滚边界内。禁止把 R05-R13 合并成一次大迁移。

## 7. 每批统一验收

基础验证：

```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:compileDebugUnitTestKotlin
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:assembleDebug
```

模块迁移时追加：

```powershell
.\gradlew.bat :<owner-module>:compileDebugKotlin
.\gradlew.bat :<owner-module>:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
```

涉及 UI、PDF、JNI 或数据库时追加对应 instrumentation/真机验证。

每批必须满足：

- 250 个现有 JVM tests 不减少、不跳过。
- 新增逻辑有 owner-module 测试。
- lint error 为 0。
- `git diff --check` 无输出。
- 无新增重复实现。
- 无新增静默异常吞噬。
- 无新增本机路径、运行结果文件或大二进制。
- 文档仅在真实结构变化后更新。

## 8. 项目级完成定义

满足以下条件，才可声明“臃肿长文件重构完成”：

1. 已识别的 P0 重复实现全部收敛为单一事实来源。
2. 没有未豁免的生产文件超过 400 行。
3. 新增或重构后的业务函数原则上不超过 80 行。
4. `DatabaseScreen`、`SmartPage`、`LocalSummaryCard`、`HybridViewModel`、`DatabaseViewModel` 完成职责拆分。
5. `SemanticRoleSearchTextBuilder`、`QueryRewritePlanner` 保留稳定 facade，内部职责可独立测试。
6. `LlamaJniReflection` 不再包含超长多职责反射函数。
7. 两个 PDF ViewModel 完成 MVI 迁移。
8. `app` 主源码占比降至 55% 以下，或对无法迁移部分有明确架构说明。
9. core、engine、feature 都拥有模块内测试；测试不再全部寄居于 app。
10. Detekt/ktlint 对新增债务具有真实阻断能力。
11. `KNOWN_ISSUES.md`、`CURRENT_STATE.md`、`ARCHITECTURE.md` 与源码一致。
12. 完整 lint、JVM tests、debug/release assemble 及相关 instrumentation 全绿。

## 9. 明确不做

- 不进行全项目一次性重写。
- 不在同一提交中同时改行为、改包名和搬模块。
- 不为了行数指标制造无语义的小文件。
- 不继续复制 utils、DTO、repository 或 API interface。
- 不在测试缺失时拆 query、reflection、importer 核心算法。
- 不用 typealias 无限期掩盖迁移未完成。
- 不把 205 个静默 catch 机械替换成日志；按失败语义逐域治理。
- 不以“编译通过”替代行为验收。

## 10. 执行方式

后续每次从远端最新 `main` 创建干净分支和独立 worktree，按 R01-R14 顺序推进。每个批次开始前先锁定写集、测试和停止条件；完成后独立提交并验证。原 `codex/powerai-stabilization` 分支仅作为历史恢复点，不在其脏工作区继续实施重构。

建议第一批执行 R01；它不改产品行为，却能让后续每个重构批次都具有可重复的规模、复杂度和依赖边界证据。
