# smart_rag_eval

固定评测种子与后续正式集放此处（owning：`feature/search-chat`）。

- `v0_seed_cases.json`：P0 种子（截图回归 + 短问/负例/来源策略占位）。**不表示 60 条已齐。**
- 合成证据只出现在测试 fixture；禁止写入 `app/src/main/assets`。
- 断言由后续 P3/P4/P5 单测与真机脚本消费；P0 只入库结构。
