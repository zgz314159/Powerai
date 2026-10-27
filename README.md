# PowerAi

Android 本地 RAG / 知识库问答应用（Kotlin · Compose · Hilt）。

## 文档

| 文档 | 说明 |
|------|------|
| [AGENTS.md](AGENTS.md) | Agent 协作与工程边界 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 架构与模块依赖 |
| [CURRENT_STATE.md](CURRENT_STATE.md) | 当前状态快照 |
| [KNOWN_ISSUES.md](KNOWN_ISSUES.md) | 已知问题与技术债 |
| [docs/debug_lan.md](docs/debug_lan.md) | 局域网 embedding/ANN 调试 |
| [docs/device_acceptance_checklist.md](docs/device_acceptance_checklist.md) | 真机/仪器验收清单（不伪完成） |
| [DEVELOPER_NOTES.md](DEVELOPER_NOTES.md) | adb / 开发陷阱 |

## Performance Benchmark（历史摘录）

arm64-v8a 设备上本地原生搜索引擎（NEON）单次查询（向量维度 128）测得：

| vectors | time (ms) | throughput (vec/ms) |
|---:|---:|---:|
| 100 | 3.969 | 25.198 |
| 500 | 1.430 | 349.561 |
| 1000 | 1.565 | 638.787 |
| 5000 | 2.366 | 2113.234 |

实现见 `app/src/main/cpp/native_search.cpp`。局域网调试步骤已迁至 [docs/debug_lan.md](docs/debug_lan.md)。
