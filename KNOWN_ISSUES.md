# PowerAi — 已知问题与技术债

> 从 [REFACTOR_PLAN.md](REFACTOR_PLAN.md) 与开发备忘提取的**未完成项**。已完成清理见该文档 §1 或 [CURRENT_STATE.md](CURRENT_STATE.md)。

**最后同步**：2026-10-30（Smart RAG P0–P4 关闭；P5 v1=62+真机决策烟测绿；UI 端到端/可信晋升未完）

---

## Smart RAG 四项缺口（进行中，勿标完成）

| ID | 缺口 | 状态 |
|----|------|------|
| KI-60 | 模型未走 `InferenceParameters.withMessages`；system 非角色编码；采样参数诊断不全 | ✅ P1 关闭（messages+诊断+真机烟测）；残留：QUICK/DEEP 思考段策略与 n_ctx 预算仍可后续收紧 |
| KI-61 | 词面分级 ≠ 意图覆盖；AMBIGUOUS 仍可硬生成 | ✅ P3 关闭（`SmartAnswerDecisionEngine` 路由 PARTIAL/NONE；弱证据不交模型） |
| KI-62 | 守卫难拦职责/顺序改写；`take(220)` 非合格摘录；假「资料不足」与资料缺失未分因 | ✅ P4 关闭（句界摘录+结构改写回退原文）；假不足分因仍依赖 P3 决策 reasonCode |
| KI-63 | `power_safety_rules` 冠名「电力安全工作规程」；Sparse/Room 回退可冒充权威 | ✅ P2 关闭（诚实标签+门闸+真机升级/幂等） |

计划与基线：`docs/smart_rag_governance_plan.md`、`docs/smart_rag_p0_baseline.md`。

---

## 本轮清零范围（机器/文档可验收）

| 状态 | 说明 |
|------|------|
| ✅ | 全量 ktlint→0；KI-58；KI-53/20/03/04（D2–D3） |
| ✅ 产品边界接受 | KI-50/51/52；KI-57；KI-56（FTS 主路径） |
| 真机待验（不伪完成） | KI-54（用户目录导入 B）；API 36 仪器补齐。端侧停止+中文（清单 A）已于 2026-10-29 仪器验收 |
| 史诗外置（不纳入本轮清零） | KI-56 NDK per-id 删（KI-21 已接 llama-android） |

---

## P1 — 臃肿文件 / 职责过多

| ID | 位置 | 问题 | 建议 |
|----|------|------|------|
| KI-01 | `AiStreamViewModel.kt`, `AiChatScaffold.kt` | ViewModel/Scaffold 仍偏大 | ✅ 已完成 MVI 重构 |
| KI-02 | `HybridViewModel.kt` | 仍承担较多 UI 编排 | ✅ 已完成 MVI 重构 |
| KI-03 | `core/data/importer/StreamingJsonResourceImporter.kt` | 导入流程仍长（~333 行） | ✅ 接受冻结（2026-10-27）：source-size 门禁看守；禁止无护栏大拆 |
| KI-04 | `SparseSearcher.kt` | 检索逻辑可再拆 | ✅ 接受冻结（2026-10-27）：`SparseSearchUtils` 已部分完成；主路径稳定 |
| KI-05 | `MainPages.kt` | 占位符文件 | ✅ 已关闭（2026-10-27）：仓库中已无该文件 |
| KI-06 | `ui/screen/detail/*` | 详情 markdown/table 文件多 | 规范：normalize 留 data/importer，勿在 UI 堆结构修复 |

---

## P2 — 结构与一致性

| ID | 位置 | 问题 | 建议 |
|----|------|------|------|
| KI-58 | `EmbeddingWorker` + `EmbeddingTestActivity` | enqueue 只写 Room pending，不调度 Worker | ✅ 已关闭（2026-10-27）：删除 EmbeddingWorker + instrumented 测；保留 debug Activity；enqueue 仅写 metadata |
| KI-10 | `data/mapper` vs `data/mappers` | 双路径并存 | ✅ 已关闭（2026-10-27）：仅存 `core/data/.../mapper` |
| KI-11 | `GemmaLocalInference.kt` | 曾 ~708 行，已部分拆分 | 低优先级；保持测试覆盖 |
| KI-12 | `DatabaseViewModel.kt` | 业务可下沉 | ✅ 已完成 MVI 重构 |

---

## P3 — 工具链与运维

| ID | 位置 | 问题 | 建议 |
|----|------|------|------|
| KI-20 | `README.md` | benchmark 与局域网调试混在同一文件 | ✅ 已关闭（2026-10-27）：局域网调试迁至 `docs/debug_lan.md` |
| KI-21 | `llama_jni` / DeepSeek | 曾为 stub / 缺 so | ✅ **已关闭（2026-10-28）**：`net.ladenthin:llama-android:6.0.0` + `DEEPSEEK_SELF_BUILT_JNI_ENABLED=true`；`LlamaCppDeepSeekRuntime` 加载 GGUF；仪器 `LlamaAndroidDeviceSmokeTest` 绿（XQ-DQ72）。遗留 stub：`llama_jni.cpp` / legacy `LlamaNativeBridge` 未删，仅非默认路径。GPU/OpenCL 未接 |
| KI-22 | 行数治理 | 曾无 CI 门禁 | ✅ 已关闭（2026-10-27）：CI `source_size_report.py --check` |

---

## P4 — 开发陷阱（非代码债）

| ID | 说明 |
|----|------|
| KI-30 | Windows 终端 **禁止** 用 `>` 重定向导出 adb 文件；须 `adb pull`（[DEVELOPER_NOTES.md](DEVELOPER_NOTES.md)） |
| KI-31 | `KnowledgeRepository` **禁止**改既有方法签名（[copilot-instructions](.github/copilot-instructions.md)） |

---

## 已关闭（仅供参考）

- KI-05 `MainPages.kt` 占位：文件已不存在（2026-10-27）
- KI-10 `mapper`/`mappers` 双路径：仅余 `core/data/mapper`（2026-10-27）
- KI-22 行数 CI：`scripts/source_size_report.py --check` 已在 Quality Guardrails（2026-10-27）
- KI-58 EmbeddingWorker 死链：删除 Worker 与 instrumented 测；debug Activity 保留 JNI/FTS 手工入口（2026-10-27）
- 「本地」页检索/导入异常被伪装成「未找到相关结果」…（既有关闭记录略）
- 「本地」页搜索命中表格题注块时未改指所属表格…（既有）
- 本地检索双支线退役（2026-10-03）
- 「本地」页 `发电机允许温升表` 0 结果 / PDF 视口 / PdfRenderer 串行化
- 重复 `ui.components` / `ui.importer` / `data.saf`；DI 单体拆分

---

## P5 — 兼容边界（内置 KB 资产更新）

| ID | 位置 | 说明 | 建议 |
|----|------|------|------|
| KI-40 | `DocumentImportManager` / `imported_files.contentSha256` | legacy 无指纹资产不自动替换 | ✅ 重建/清空入口已提供 |
| KI-55 | `UserKbPackageImporter.removePackage` | 用户包移除事务语义 | ✅ 已实现 |
| KI-56 | `invalidateNativeVectorIndex` | 无 per-package 删；rebuild/移除清整库向量缓存 | **接受限制**（FTS 主路径）；NDK 按 id 删属后续史诗，不纳入本轮清零 |
| KI-57 | 用户包「更新」入口 | 持久化 SAF treeUri + 一键更新 | ✅ 已实现（2026-10-27）：Room 6→7 `sourceTreeUri`；有 URI 显示「一键更新」，权限失效可「重选目录」 |

---

## P6 — 用户目录 KB 包导入（新增能力边界）

| ID | 位置 | 说明 | 建议 |
|----|------|------|------|
| KI-50 | `UserKbPackageImporter` | 跳过判定仅看 JSON 字节指纹 | **接受限制**（与 PaddleModels 契约一致） |
| KI-51 | `SafKbDirectorySource` | 非层级 opaque provider 可能失败 | **接受限制**；遇真实 case 再枚举子文档 |
| KI-52 | 用户目录导入读入整份 JSON | 峰值内存 | **接受限制**；大 KB 再两遍流式 |
| KI-53 | `PdfViewerScreen` | 历史乱码文案 | ✅ 已关闭（2026-10-27）：可见 UI 文案已为正常中文；无 mojibake 残留 |
| KI-54 | 真机验收 | 用户目录导入全链路未真机跑 | **真机待验**（不伪完成） |
| KI-59 | Smart 联网 | 智能页「联网增强」依赖 `local.properties` → `SERPER_API_KEY`；未配置时无网页、仅本地+设备日期；有 key 时仅时效或本地 0 命中才搜 | 配置 Serper 后真机验自动联网路径 |
| KI-60 | Smart 模型档 | 端侧若仅有 Distill-1.5B-Q4，Instruct/Q5–Q6 优选规则无候选可换；质量上限仍受模型档约束 | 有条件时 push Instruct-Q5/Q6 到 `files/models/` |

---

## 更新规则

修复或新发现问题时：
1. 更新本表（状态、日期）
2. 在 `TASK_LOG.md` 记录根因与验证
3. 若里程碑级变化，更新 `CURRENT_STATE.md`
4. `REFACTOR_PLAN.md` 仅作历史参考，避免双份维护长文
