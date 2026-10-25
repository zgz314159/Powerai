# PowerAi — 架构与 Pipeline

> 结构级变更时更新。细则与契约见 [AGENTS.md](AGENTS.md)。

---

## 1. 模块与分层（Android）

### 1.1 Gradle 模块

| 模块 | 职责 |
|------|------|
| `:app` | 导航、Hilt 装配、Room/importer 实现（含 Ports）、剩余 UI（Main 壳为主） |
| `:core:model-contract` | 共享契约与模型：`KnowledgeRepository`、`PowerAIEngine`、`BlocksParser`/`BlocksJsonPatcher`、`ImportModels`、`JsonEntry`/`JsonKnowledgeFile`、`VisionBoost*`、MVI、query、`WebSearch`/`SearchHistory` |
| `:core:domain` | 检索/证据领域：`HybridRetrievalService`、`RetrievalFusionUseCase`、`LocalEvidence*`、`LocalHybridQueryOrchestrator`、`PdfSourceRef`、`SemanticRoleSearchTextBuilder` |
| `:core:data` | `KnowledgeEntity`、仓储、`RoomFtsRetriever`、WebSearch/History、`FontSettings`、`PdfAssociation`/`PdfStorage`、JSON KB 导入核心 |
| `:engine:native` | native 源码与 CMake（FAISS、llama JNI、NEON 搜索） |
| `:engine:ai` | 推理引擎封装与 JNI 桥接（DeepSeek、Gemma、llama 运行时） |
| `:feature:hybrid` | Hybrid 主屏垂直切片：`HybridViewModel`/`HybridQueryUseCase`；经 `HybridKbImportPort` 依赖 app 导入 |
| `:feature:knowledge-detail` | 知识详情垂直切片：`KnowledgeDetailViewModel`/Markwon/Compose blocks/PDF locate；经 `KnowledgeDetailSupportPort` 依赖 app |
| `:feature:pdf` | PDF 查看垂直切片：Viewer/`PdfKnowledgeViewModel`/`PdfFigureListViewModel`；依赖 knowledge-detail 的 bbox/locate |
| `:feature:database` | 数据库/KB 管理垂直切片：`DatabaseViewModel`/`DatabaseUseCase`；经 `DatabaseKbPort` 依赖 app 导入 |
| `:feature:settings` | 设置垂直切片：`SettingsViewModel`/UI；经 `SettingsImportPort` 依赖 app `JsonRepository` |
| `:feature:importer` | 用户 KB 目录导入垂直切片：`ImportViewModel`/`KnowledgeImportScreen`；经 `UserKbDirectoryImportPort` 依赖 app |
| `:feature:jsonrepo` | JSON 知识文件管理垂直切片：`JsonRepositoryViewModel`/UI；经 `JsonRepoPort` 依赖 app `JsonRepository` |
| `:feature:search` | 简单词法搜索垂直切片：`SearchViewModel`/UI；依赖 hybrid SearchBar + `RetrievalFusionUseCase` |
| `:feature:mine` | 「我的」页垂直切片：`MineScreen`；依赖 settings；dev backdoor 由 app 回调 |
| `:feature:debug` | 调试：`DebugViewModel`/UI + `EmbeddingTestActivity` |
| `:feature:quickask` | 快速问答原型屏 |
| `:feature:search-chat` | 搜索与对话功能域（DeepSeek） |
| `:benchmark` | Macrobenchmark 基准 |

> Main 壳仍在 `:app`；Drawer/Local/Response/Markdown/KnowledgeItemCard/Scroll/ResponseCard 在 `:feature:hybrid`（C13–C15）；Smart*/AiStream* 在 `:feature:search-chat`（C15–C16）；chat 模型与 `ChatHistoryRepository` 在 model-contract；`ChatHistoryStore`/Impl 在 `:core:data`（C17a）。

### 1.2 应用内分层

```
┌─────────────────────────────────────────┐
│  ui (Compose, ViewModel, Navigation)    │
└──────────────────┬──────────────────────┘
                   │ 依赖
┌──────────────────▼──────────────────────┐
│  domain (UseCase, Model, Repository IF) │
│  query / retrieval / ai / eval          │
└──────────────────┬──────────────────────┘
                   │ 实现
┌──────────────────▼──────────────────────┐
│  data (Room, Importer, API, Retriever)  │
└─────────────────────────────────────────┘
```

**禁止**：`domain` 依赖 `ui` 或 Android UI API；`data` 依赖 `ui`。

`domain` 下不含 `llm` 子包：LLM 与引擎抽象位于 `:engine:ai`（`engine/ai`），`domain/query` 的实现位于 `:core:model-contract`；混合检索服务位于 `:core:domain`。

**DI**（`app/src/main/java/com/example/powerai/di/`）：`CoreModule`、`AppModule`、`NetworkModule`、`DataModule`、`AIModule`、`RepositoryModule`、`FaissModule`。

---

## 2. 端到端 Pipeline

### 2.1 知识入库（离线 / 工具链）

```
DOCX/PDF
  → extraction (tools: docx_reader, pdf pages)
  → normalize (blocks, markdown tables, kb path sanitizer)
  → knowledge_base.json (+ 截图 manifest)
  → assets/kb/<fileId>/（内置）  或  用户选择的输出目录（含 knowledge_base.json + shots/）
  → import (核心位于 `:core:data` 的 `core/data/importer`：StreamingJsonResourceImporter JsonReader 逐条流式、失败即抛出；`:app` 的 DocumentImportManager 负责 assets/URI 入口与单文件事务原子提交；UserKbPackageImporter 负责「用户目录包」：以内容指纹跳过/替换、把引用的 shots 复制到应用私有镜像并把块内 imageUri 重写为私有 file://，先落文件后提交)
  → storage (Room KnowledgeEntity, contentNormalized)
  → optional: vector index (NativeVectorRepository)
```

离线 KB 构建脚本（`tools/` 下的 Python 工具链）未纳入本分支；本节只描述入库的运行时路径。

目录规范：[KB_TAXONOMY_GUIDE.md](KB_TAXONOMY_GUIDE.md)。

### 2.2 查询与回答（运行时）

```
User Query
  → QueryUnderstandingPipeline (normalize, intent, retrieval queries)
  → retrieval
       ├─ RoomFtsRetriever (lexical FTS: contentNormalized / searchContent)
       ├─ HybridRetrievalService / HybridQueryUseCase
       └─ AnnRetriever (native → local Faiss → HTTP)
  → fusion / evidence (LocalEvidenceRefiner, RetrievalFusionUseCase)
  → answer
       ├─ Local: LocalAnswerPlanner, extractive builder
       ├─ AI stream: AiStreamUseCase → AiStreamingService
       └─ On-device LLM: PowerAIEngine / DeepSeek / Gemma
  → ui (Hybrid, Main, Detail, Blocks renderer)
```

### 2.3 详情展示

```
KnowledgeEntity / blocks JSON
  → BlocksParser / BlocksRenderer (ui/blocks)
  → fallback: Markwon / chunk / table normalize (ui/screen/detail/*)
```

**原则**：结构问题在 **normalize / data/importer** 解决；**export/ui** 不做结构修复。

---

## 3. 主要包职责

| 包 | 职责 |
|----|------|
| `core/data/importer` | JSON KB 导入核心（`:core:data`）：`StreamingJsonResourceImporter`、解析/映射/批写、markdown 表规范化、导入进度模型 |
| `data/importer` | `:app` 侧 Android 适配：资产扫描、URI/SAF 入口、DOCX/PDF/TXT parser、blocks 预处理与路径规范化；`UserKbPackageImporter`（用户目录包：`packageId = "user:"+sha256(目录标识)`、内容指纹跳过/替换、引用 shots 复制到 `filesDir/kb_user/<包>/<指纹>/` 并重写块内 `imageUri`） |
| `data/repository` | `KnowledgeRepositoryImpl`、本地搜索处理 |
| `data/retriever` | ANN、向量索引、HTTP 检索 |
| `data/local` | Room entity/dao/migration |
| `domain/usecase` | HybridQuery、AiStream、Database、Local answer |
| `domain/query` | Query 理解、改写、意图（实现位于 `:core:model-contract`） |
| `domain/retrieval` | 混合检索服务 |
| `engine/ai` | Gemma、DeepSeek、引擎抽象与 JNI 桥接 |
| `domain/ai` | 流式 AI 服务 |
| `domain/repository` | `KnowledgeRepository` 契约 |
| `ui/screen/main` | 主 Tab、聊天、本地/智能搜索 |
| `ui/screen/hybrid` | Hybrid 模式 VM 与 UI 工厂 |
| `ui/screen/detail` | 知识详情与 markdown/blocks 渲染 |
| `ui/blocks` | Blocks schema 解析与 Compose 渲染 |

---

## 4. 核心契约

- **`KnowledgeRepository`**（`domain/repository`）：应用内知识访问核心接口；实现于 `data/repository`。
- **`KnowledgeEntity`**：含 `contentNormalized` 供词法检索。
- **`fileId`**：相对 `assets/kb/` 的路径，与 KB  taxonomy 一致。

---

## 5. Native 与外部服务

| 组件 | 位置 | 用途 |
|------|------|------|
| NEON vector search | `engine/native/src/main/cpp/native_search.cpp` | 本地向量距离 |
| FAISS wrapper | `native/faiss_wrapper`、`app/src/main/java/com/example/powerai/data/retriever` | ANN |
| llama JNI | `engine/native/src/main/cpp/llama_jni.cpp`、`engine/ai` | 端侧 GGUF |
| embedding 服务 | `tools/embedding_prototype` | 开发期 HTTP embedding |

---

## 6. 相关文档

- Agent 工作流：[AGENTS.md](AGENTS.md)
- 已知技术债：[KNOWN_ISSUES.md](KNOWN_ISSUES.md)
- 重构历史：[REFACTOR_PLAN.md](REFACTOR_PLAN.md)
