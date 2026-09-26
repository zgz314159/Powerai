# PowerAi — 架构与 Pipeline

> 结构级变更时更新。细则与契约见 [AGENTS.md](AGENTS.md)。

---

## 1. 模块与分层（Android）

### 1.1 Gradle 模块

| 模块 | 职责 |
|------|------|
| `:app` | Compose UI、ViewModel、导航、Hilt 装配、Room 与 importer 实现 |
| `:core:model-contract` | 共享契约与模型：`KnowledgeRepository`、`PowerAIEngine`、blocks 模型、MVI 基类 |
| `:core:data` | `KnowledgeEntity`、仓储实现与本地检索 |
| `:engine:native` | native 源码与 CMake（FAISS、llama JNI、NEON 搜索） |
| `:engine:ai` | 推理引擎封装与 JNI 桥接（DeepSeek、Gemma、llama 运行时） |
| `:feature:search-chat` | 搜索与对话功能域 |
| `:benchmark` | Macrobenchmark 基准 |

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

`domain` 下不含 `llm` 子包：LLM 与引擎抽象位于 `:engine:ai`（`engine/ai`），`domain/query` 的实现位于 `:core:model-contract`。

**DI**（`app/src/main/java/com/example/powerai/di/`）：`CoreModule`、`AppModule`、`NetworkModule`、`DataModule`、`AIModule`、`RepositoryModule`、`FaissModule`。

---

## 2. 端到端 Pipeline

### 2.1 知识入库（离线 / 工具链）

```
DOCX/PDF
  → extraction (tools: docx_reader, pdf pages)
  → normalize (blocks, markdown tables, kb path sanitizer)
  → knowledge_base.json (+ 截图 manifest)
  → assets/kb/<fileId>/  或  用户导入 URI
  → import (StreamingJsonResourceImporter / DocumentImportManager)
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
       ├─ sparse / KnowledgeLocalSearch (contentNormalized)
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
| `data/importer` | JSON/DOCX 导入、blocks 预处理、路径规范化 |
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
