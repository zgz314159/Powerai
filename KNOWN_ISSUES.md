# PowerAi — 已知问题与技术债

> 从 [REFACTOR_PLAN.md](REFACTOR_PLAN.md) 与开发备忘提取的**未完成项**。已完成清理见该文档 §1 或 [CURRENT_STATE.md](CURRENT_STATE.md)。

**最后同步**：2026-10-04

---

## P1 — 臃肿文件 / 职责过多

| ID | 位置 | 问题 | 建议 |
|----|------|------|------|
| KI-01 | `AiStreamViewModel.kt`, `AiChatScaffold.kt` | ViewModel/Scaffold 仍偏大 | ✅ 已完成 MVI 重构；状态拆分、更多 UI 组件分层；逻辑已部分下沉 UseCase |
| KI-02 | `HybridViewModel.kt` | 仍承担较多 UI 编排 | ✅ 已完成 MVI 重构；继续下沉 UseCase；已有 runtime support 拆分 |
| KI-03 | `core/data/importer/StreamingJsonResourceImporter.kt`（2026-10-03 已归 `:core:data`） | 导入流程仍长 | 按阶段拆分（parse / map / write）；`FileParserFactory` 已引入；`:app` 仅保留编排/适配 |
| KI-04 | `SparseSearcher.kt` | 检索逻辑可再拆 | 扫描/tokenize/score 子模块；`SparseSearchUtils` 已部分完成；旧 `KnowledgeLocalSearch`/`LocalSearchDiagnostics` 支线已于 2026-10-03 退役 |
| KI-05 | `MainPages.kt` | 占位符文件 | 评估删除 |
| KI-06 | `ui/screen/detail/*` | 详情 markdown/table 文件多 | 保持 normalize 在 data/importer，避免 UI 层继续堆结构修复 |

---

## P2 — 结构与一致性

| ID | 位置 | 问题 | 建议 |
|----|------|------|------|
| KI-10 | `data/mapper` vs `data/mappers` | 双路径并存 | 统一命名与入口，避免重复映射逻辑 |
| KI-11 | `GemmaLocalInference.kt` | 曾 ~708 行，已部分拆分 | 剩余 TODO 低优先级；保持测试覆盖 |
| KI-12 | `DatabaseViewModel.kt` | 业务可下沉 | ✅ 已完成 MVI 重构；扩展 `DatabaseUseCase` 覆盖 |

---

## P3 — 工具链与运维

| ID | 位置 | 问题 | 建议 |
|----|------|------|------|
| KI-20 | `README.md` | benchmark 与局域网调试混在同一文件 | 可选拆分为 `docs/debug_lan.md` |
| KI-21 | `llama_jni` / DeepSeek | JNI 仍为 stub 或需真 runtime | 见 [DEEPSEEK_JNI_INTEGRATION.md](DEEPSEEK_JNI_INTEGRATION.md) |
| KI-22 | 行数治理 | 无 CI 门禁 | 定期 `scripts/generate_line_counts.ps1` |

---

## P4 — 开发陷阱（非代码债）

| ID | 说明 |
|----|------|
| KI-30 | Windows 终端 **禁止** 用 `>` 重定向导出 adb 文件；须 `adb pull`（[DEVELOPER_NOTES.md](DEVELOPER_NOTES.md)） |
| KI-31 | `KnowledgeRepository` **禁止**改既有方法签名（[copilot-instructions](.github/copilot-instructions.md)） |

---

## 已关闭（仅供参考）

- 「本地」页检索/导入异常被伪装成「未找到相关结果」：`HybridQueryUseCase.localMode` 捕获 `Throwable` 后返回 `emptyList()`，`HybridModeExecutor` 兜底又构造空检索结果，最终与「确实 0 命中」共用同一空态。现将可恢复错误映射为可重试的 `LocalPageState.ERROR`（安全文案，不泄露异常详情），仅真正 0 命中保留「无匹配」；`CancellationException` 继续传播，取消/新旧查询竞态不再回写结果/错误/历史（`LocalSearchFailureUiStateTest`）
- 「本地」页搜索命中表格题注块时未改指所属表格：题注→表格改指已接入 `HybridQueryUseCase.localMode`（`KnowledgeRepository.resolveTableLabelTargets`，复用 `TableLabelBlockResolver`/`KnowledgeTableLabelTargets`），支持同条目与同页跨条目几何最近表格；旧 `searchLocal` 支线已于 2026-10-03 退役，规则保留在主路径
- 本地检索曾存在两条并行支线（真实 UI 走 `HybridRetrievalService`，旧 `searchLocal`/`RetrievalFusionService` 无生产调用）：已退役旧支线及其无用 DI 绑定，收敛为一条 UI 主路径（2026-10-03）
- 「本地」页 `发电机允许温升表` 返回 0 结果：`LocalEvidenceRefiner` 仅用 title+markdown 预览评分，而 FTS/LIKE 命中依赖 indexed block 文本；题注仅存在于块内的表格条目被判 coverage=0 丢弃；现评分纳入 `contentBlocksJson` 明文
- PDF 定位目标页未滚入视口：页面项在 bitmap 渲染前为短占位高度，`scrollToItem` 被列表滚动范围钳制；已通过底部预留视口高度修复
- `PdfRenderer` 并发渲染导致 `IllegalStateException: Current page not closed`：多个页面项并行渲染；已将渲染调度串行化
- 重复 `ui/components` → 已统一 `ui.component`
- 重复 `ui/importer` → 已删，用 `ui.screen.importer`
- 重复 `data/saf/DocumentImportManager` → 已删
- DI 单体 `AppModule` → 已拆 5 模块（2026-03-01）

---

## P5 — 兼容边界（内置 KB 资产更新）

| ID | 位置 | 说明 | 建议 |
|----|------|------|------|
| KI-40 | `DocumentImportManager` / `imported_files.contentSha256` | 迁移前（Room < 6）已导入的资产没有内容指纹，其旧条目**无法无歧义归属**（不能用 `source`/`docSha256` 猜测，否则会误删 source 相同的其他包）。升级后这类 legacy 资产在自动导入中被**检测并跳过替换**（诊断状态 `legacy`），避免新旧混合或误删；升级后**新导入**的资产走完整 A/B 替换。 | ✅ 已提供**用户主动**的「重建内置知识库」入口（`DocumentImportManager.rebuildBuiltInKnowledgeBase`）：仅在用户确认后，单事务清除本应用 `knowledge` + FTS + `imported_files`，失效 `vision_cache`/`embedding_metadata`（按知识条目 id），并在提交后**同一进程内清空原生向量索引**（`VectorRepository.clear()`；`NativeVectorRepository` 以锁串行化 `search`/`upsert`/`clear`）并删除应用私有 `vector_index.bin`（磁盘删除失败不报完整成功）；再由当前内置资产重建；状态区分 running/success/failed/cancelled，失败整体回滚可重试。仍**不**按 source 猜测归属、**不**静默清理；未做真机 UI 验收 |

---

## P6 — 用户目录 KB 包导入（新增能力边界）

| ID | 位置 | 说明 | 建议 |
|----|------|------|------|
| KI-50 | `UserKbPackageImporter` / `imported_files.contentSha256` | 跳过判定以所选目录的 `knowledge_base.json` 字节 SHA-256 为准；若只改了 `shots/` 而 JSON 未变，则判为「未变化」而跳过，不会被重新复制。 | 契约上 PaddleModels 会同时重生成 JSON 与截图；如未来需要，加入对资源清单的指纹。 |
| KI-51 | `SafKbDirectorySource` | 通过 `DocumentsContract.buildDocumentUriUsingTree` 以「树文档 id + 相对路径」直接寻址子文档，未做目录枚举；对层级的 ExternalStorage 类 provider（手机内置/外置存储）有效，对使用非层级 opaque document id 的第三方 provider 可能取不到资源。 | 如遇此类 provider，改用子文档枚举（`buildChildDocumentsUriUsingTree`）建立 id 映射。 |
| KI-52 | `StreamingJsonResourceImporter` / `UserKbPackageImporter` | 用户目录导入会先把 `knowledge_base.json` 整体读入内存（用于先哈希再决定跳过/替换与统计块数）；内置 assets 路径仍为纯流式。 | 大 KB 若需更低峰值内存，可改为两遍流式（先哈希、再导入）。 |
| KI-53 | `PdfAssociation` / `PdfViewerScreen` | PDF 关联流程为 SHA-256 严格匹配；`PdfViewerScreen` 中我改动的关联回调已更正为正常中文，但该文件仍有其它历史乱码字符串（如按钮文案）未在本次范围内清理。 | 后续单独清理该文件剩余乱码（非本次任务范围）。 |
| KI-54 | 真机验收 | 用户目录导入的**真机**链路（系统目录选择器 → 导入 → 关联 PDF → 搜索 → 表格截图 → PDF 定位）尚未在本阶段真机执行；JVM + 仓库外样本已验收 48/839/48 与第 29/32 页表格。 | 在 Android 16 真机按验收用例走一遍并补记录。 |

---

## 更新规则

修复或新发现问题时：
1. 更新本表（状态、日期）
2. 在 `TASK_LOG.md` 记录根因与验证
3. 若里程碑级变化，更新 `CURRENT_STATE.md`
4. `REFACTOR_PLAN.md` 仅作历史参考，避免双份维护长文
