# PowerAi — 已知问题与技术债

> 从 [REFACTOR_PLAN.md](REFACTOR_PLAN.md) 与开发备忘提取的**未完成项**。已完成清理见该文档 §1 或 [CURRENT_STATE.md](CURRENT_STATE.md)。

**最后同步**：2026-08-17

---

## P1 — 臃肿文件 / 职责过多

| ID | 位置 | 问题 | 建议 |
|----|------|------|------|
| KI-01 | `AiStreamViewModel.kt`, `AiChatScaffold.kt` | ViewModel/Scaffold 仍偏大 | ✅ 已完成 MVI 重构；状态拆分、更多 UI 组件分层；逻辑已部分下沉 UseCase |
| KI-02 | `HybridViewModel.kt` | 仍承担较多 UI 编排 | ✅ 已完成 MVI 重构；继续下沉 UseCase；已有 runtime support 拆分 |
| KI-03 | `StreamingJsonResourceImporter.kt`, `JsonResourceImporter.kt` | 导入流程仍长 | 按阶段拆分（parse / map / write）；`FileParserFactory` 已引入 |
| KI-04 | `SparseSearcher.kt`, `KnowledgeLocalSearch` 相关 | 检索逻辑可再拆 | 扫描/tokenize/score 子模块；`SparseSearchUtils`、`LocalSearchDiagnostics` 已部分完成 |
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

- 重复 `ui/components` → 已统一 `ui.component`
- 重复 `ui/importer` → 已删，用 `ui.screen.importer`
- 重复 `data/saf/DocumentImportManager` → 已删
- DI 单体 `AppModule` → 已拆 5 模块（2026-03-01）

---

## 更新规则

修复或新发现问题时：
1. 更新本表（状态、日期）
2. 在 `TASK_LOG.md` 记录根因与验证
3. 若里程碑级变化，更新 `CURRENT_STATE.md`
4. `REFACTOR_PLAN.md` 仅作历史参考，避免双份维护长文
