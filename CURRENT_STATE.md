# PowerAi — 当前状态快照

> 每次完成有意义的任务后由 Agent 更新本节。勿在此写长篇历史，细节见 `TASK_LOG.md`。

**最后更新**：2026-08-17

---

## 项目概要

- **类型**：Android（Kotlin + Compose + Hilt）本地 RAG / 知识库问答应用
- **包名**：`com.example.powerai`
- **Kotlin 源文件**：约 367 个（`app/src/main/java/com/example/powerai/`）

---

## 架构快照

| 层 | 路径 | 说明 |
|----|------|------|
| ui | `ui/` | Compose 屏幕、组件、导航 |
| domain | `domain/` | 用例、模型、检索、LLM、query pipeline |
| data | `data/` | Room、importer、repository、retriever |
| di | `di/` | Hilt 模块（已按域拆分） |
| tools | `tools/` | DOCX→KB、PDF 裁剪、embedding 原型（Python） |
| native | `app/src/main/cpp/`, `llama_library/` | FAISS、llama JNI、NEON 搜索 |

详见 [ARCHITECTURE.md](ARCHITECTURE.md)。

---

## 已完成里程碑（摘要）

| 项 | 状态 |
|----|------|
| DI 拆分（Core/Database/Network/Data/AI） | ✅ 2026-03-01，`AppModule` 仅作文档占位 |
| 重复 UI 包 `ui/components` | ✅ 已清理，统一 `ui.component` |
| 重复 Import 路径 `ui/importer` | ✅ 已删除，统一 `ui.screen.importer` |
| `data/saf/DocumentImportManager` | ✅ 已删除，唯一入口 `data/importer` |
| Gemma 推理拆分 | ✅ 部分（Loader/Sanitizer/Engine） |
| Hybrid 查询 / AiStream 部分下沉 UseCase | ✅ 进行中，见 KNOWN_ISSUES |
| Agent 治理框架（AGENTS + Memory + Cursor rules） | ✅ 2026-05-27 |
| MVI 架构重构（全部 10 个 ViewModel） | ✅ 2026-08-17 |

---

## 测试与构建

- 单元测试：约 **140** tests（debug + release，以 `REFACTOR_PLAN.md` 记录为准）
- 编译回归命令：`:app:compileDebugKotlin`
- KB 工具链回归：`python -m py_compile` + `tools/build_kb_from_docx.py --help`

---

## 知识库（assets）

- 根目录：`app/src/main/assets/kb/`
- 布局：`行业/内容类型/专业/文档slug/knowledge_base.json`
- 规范：[KB_TAXONOMY_GUIDE.md](KB_TAXONOMY_GUIDE.md)

---

## 检索与推理（运行时）

- **词法检索**：`contentNormalized` + sparse / local search
- **向量**：Native ANN / Local FAISS / HTTP（`FaissModule` 优先 native）
- **Query**：`QueryUnderstandingPipeline`（normalize → intent → retrieval queries）
- **LLM**：Gemma、DeepSeek（JNI / llama.cpp）、AiStream 远程流式

---

## 调试

- 局域网 embedding：`files/ai_base_url.txt` 或 `BuildConfig.AI_BASE_URL`（见 README）
- adb 拉文件：**禁止** shell `>` 重定向，须 `adb pull`（见 DEVELOPER_NOTES）

---

## 活跃关注点

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)（臃肿 ViewModel、importer 拆分、mapper 双路径等）。
