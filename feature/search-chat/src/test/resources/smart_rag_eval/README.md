# smart_rag_eval

固定评测种子与后续正式集放此处（owning：`feature/search-chat`）。

- `v0_seed_cases.json`：P0 种子骨架（保留）。
- `v1_fixed_cases.json`：P5 固定集 v1（约 50 条，holdout≈24%）。**未满建议 60，不得宣称完整集齐。**
- 合成证据只出现在测试 fixture；禁止写入 `app/src/main/assets`。
- 断言：`SmartRagFixedEvalTest`（主）+ `SmartRagSeedEvalTest`（v0 兼容）；真机：`SmartRagDecisionDeviceSmokeTest`。
- 仍缺：扩到 ≥60、UI 截图问答端到端、AEGL 可信晋升。
