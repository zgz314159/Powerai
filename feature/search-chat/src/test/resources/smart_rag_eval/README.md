# smart_rag_eval

固定评测种子与后续正式集放此处（owning：`feature/search-chat`）。

- `v0_seed_cases.json`：P0 种子骨架（保留）。
- `v1_fixed_cases.json`：P5 固定集 v1（**62** 条，holdout≈23%）。合成 fixture 仅测试用。
- 合成证据只出现在测试 fixture；禁止写入 `app/src/main/assets`。
- 断言：`SmartRagFixedEvalTest`（主）+ `SmartRagSeedEvalTest`（v0 兼容）；真机：`SmartRagDecisionDeviceSmokeTest`。
- 真机：`SmartRagScreenshotPipelineDeviceTest` / `SmartRagScreenshotUiAcceptanceTest`。
- AEGL：`scripts/run-tests.sh` → `aegl-task task verify` PASS（沙箱不含 Mockito PromptManager）。
- 仍缺：`aegl-task task promote`（需 operator controller token + 异 UID broker）。
