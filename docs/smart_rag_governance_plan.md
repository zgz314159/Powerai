# Smart RAG 四项缺口治理（项目内执行计划）

日期：2026-10-29  
诊断基线提交：`f1a124d`  
工作树：AEGL `rag-four-gaps-p0-*`（正式目录 `/home/zhong/AndroidStudioProjects/PowerAi` 只读；变更经 worktree → push → 可信晋升）

外部交接原文仅作输入；**运行与评测不得依赖** `~/.codex/visualizations/...` 路径。本文件与 `feature/search-chat/src/test/resources/smart_rag_eval/` 为 owning 契约。

## 四项缺口

1. GGUF 对话模板未经 messages API 应用；生成配置未统一可追溯。
2. 词面相关度被当作完整可答性；弱/局部证据路由不清。
3. 生成后守卫难拦职责/顺序/条件语义改写。
4. 简短内置语料冠以规程名；Sparse/Room/回退链可能再冒充权威。

## 阶段与出口（不得跳过）

| 阶段 | 出口摘要 | 状态 |
|------|----------|------|
| P0 基线 | 失败可定位到源/检索/覆盖/模型/解析/守卫/展示；事实与假设分开；不污染正式目录 | ✅ 材料已入库（`449de71`）；**整体治理未完成** |
| P1 模型接入 | 模板只应用一次；同步/流式一致；停止回归；真机 A/B 有记录 | ✅ JVM：builder+StopTest；真机：`LlamaCppChatTemplateDeviceSmokeTest`（XQ-DQ72）diag=`gguf_chat_template_messages_v1`，同证据双次生成非空中文；对照见 `docs/smart_rag_p0_baseline.md` P1 |
| P2 来源资格 | 新装/旧库升级/重复升级通过；不明来源不冒充权威 | ✅ JVM+真机：`srcpol-2` 后 synopsis=10、legacy 假规程名=0、正式 KB=143 保留；二次冷启动幂等 |
| P3 证据决策 | FULL/PARTIAL/CLARIFY/NONE 等价；弱证据不硬答 | 待执行 |
| P4 输出与状态 | 原文路径；语义负例阻断；历史一致 | 待执行 |
| P5 综合验收 | 固定集+留出集+真机重复；Memory；可信晋升 | 待执行 |

## 工程边界（摘要）

- 禁止在正式 Projects/AndroidStudioProjects 目录内直接改生产并宣称交付。
- 构建使用 AEGL task 的 `gradle_home` / scratch；产物不写回正式仓。
- 每小阶段精确路径 `git add` → commit → 立即 push；push ≠ 可信晋升。
- 真机/来源审核缺失时只能写「代码验证完成，尚缺某项验收」。

## 评测入口

- 用例骨架：`feature/search-chat/src/test/resources/smart_rag_eval/v0_seed_cases.json`
- 基线事实：`docs/smart_rag_p0_baseline.md`
