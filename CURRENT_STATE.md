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

> §6 的数值均取自**合并窗口前后已有的机器产物**，本文件更新时**未重新执行**任何 Gradle 任务。

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

- 全仓 **12 个具体 ViewModel**（`:app` 11 + `:feature:search-chat` 1），其中 **10 个**继承 `BaseMviViewModel`
- `DeepSeekViewModel` **已完成迁移**（位于 `:feature:search-chat`），`DeepSeekIntent.kt` 存在并被 UI 调用
- **尚未迁移（2 个）**：
  - `app/src/main/java/com/example/powerai/ui/screen/pdf/PdfFigureListViewModel.kt`
  - `app/src/main/java/com/example/powerai/ui/screen/pdf/PdfKnowledgeViewModel.kt`
- 复现统计：`find … -name "*ViewModel.kt" ! -name "BaseMviViewModel.kt"` 计总数，再 `grep BaseMviViewModel` 计已迁移数
- **BlocksParser**：`type: "figure"` 映射为 **`FigureNodeBlock`**（`ui/blocks/BlocksParser.kt`）；`semanticRole: figure_callout` **不会**被自动过滤——`BlocksParserTest` 明确断言按 `semanticRole` 保留 code 块

## 6. 测试与 CI 证据

| 项 | 结果 | 证据与时间 | 限制 |
|----|------|-----------|------|
| JVM 全量单测 | **250 tests / 0 failures / 0 errors / 0 skipped**（71 个测试类） | 目标工作树 `app/build/test-results/testDebugUnitTest/TEST-*.xml`，2026-09-25 23:07:22 | **最近一次有机器产物支持的完整 JVM 单测结果**；产物被 `app/.gitignore` 忽略，本文件更新时未重跑 |
| instrumentation（app） | **3/3** | PR 合并门禁结果；`android-instrumentation-tests.yml` 执行 `:app:connectedDebugAndroidTest`，`app/src/androidTest` 3 个测试类各 1 个 `@Test` | 合并前 CI 结果，本批未复跑 |
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

- 2 个 PDF ViewModel 尚未迁移到 MVI（见 §5）
- `benchmarks` CI job 默认不执行（依赖 `vars.RUN_BENCHMARKS`）
- `llama_library/` 预置库未入库，需本地准备
- lint 66 个 warning 未清零

## 8. 下一步工作

- 将 2 个 PDF ViewModel 迁移到 MVI —— **计划**，未开始
- 维持 lint error 为 0 —— **待验证**（下一次 lint 运行）
- 端侧推理停止行为与中文输出的真机回归 —— **待验证**

## 9. 历史里程碑（历史性质，不代表当前状态）

| 日期 | 事项 |
|------|------|
| 2026-03-01 | DI 按域拆分；重复包 `ui.components`、`ui.importer`、`data.saf` 已清理 |
| 2026-05-27 | Agent 治理框架（`AGENTS.md` + Memory）建立 |
| 2026-08-17 | MVI 重构启动并覆盖绝大多数 ViewModel（当前进度见 §5） |
| 2026-09-12 | `DeepSeekViewModel` 逻辑交由 Orchestrator；`LlamaJni` 拆为 Native/Reflection Bridge、Controller、Metrics（均在 `engine/ai`） |
| 2026-09-19 | 依赖方向治理、`KbManifest` 引入、Version Catalog 全面迁移（`gradle/libs.versions.toml`） |
