# 真机 / 仪器验收清单（不伪完成）

> Phase D5。下列项需在真实设备或 CI 仪器环境执行后，才能在 [CURRENT_STATE.md](../CURRENT_STATE.md) 标为「已验收」。**未执行前保持「待真机」。**

## A. 端侧推理停止 + 中文输出

设备：Android 16（或目标机型）· DeepSeek/Smart 路径可用时

| # | 步骤 | 期望 | 状态 |
|---|------|------|------|
| A1 | 加载本地模型，发起 grounded 生成 | 流式输出可见 | ✅ 2026-10-29 XQ-DQ72 仪器 |
| A2 | 生成中点「停止」 | UI 进入停止中→已取消；不再追加 token；无「推理失败」误报 | ✅ 同上 |
| A3 | 中文查询（技规类） | 回答为可读中文；无明显乱码 / 英文串扰主导 | ✅ 同上 |
| A4 | 再开新一轮生成 | 可正常开始；上一轮 job 已取消 | ✅ 同上 |

仪器：`SmartDeepSeekUiAcceptanceTest` + `LlamaAndroidDeviceSmokeTest`（见 `CURRENT_STATE.md` §2）。JVM 护栏：`DeepSeekGenerationStopTest`。

## B. KI-54 用户目录 KB 导入全链路

| # | 步骤 | 期望 | 状态 |
|---|------|------|------|
| B1 | 系统目录选择器选用户包目录 | 导入成功；条目数与样本一致（完整样本参考 48/839/48；本机小样 `PowerAiKb_B_sample` 为 3 entries / 6 shots） | 数据链 ✅ 仪器 `Ki54UserKbImportSmokeTest`（File 源）；**SAF UI 仍待手点** |
| B2 | 关联原始 PDF（SHA-256） | 匹配成功；不匹配有明确提示 | 待真机 |
| B3 | 「本地」搜索表格题注查询 | 命中表格；详情/截图可开 | 待真机（完整 48 样本更佳） |
| B4 | 「在 PDF 中定位」 | 黄框落在正确页（如 29/32） | 待真机 |
| B5 | 抽屉「更新（选同一目录）」/「一键更新」JSON 未变 | 跳过 | 数据链 ✅（同测 Skipped）；**UI 待手点** |
| B6 | 「移除」用户包 | 本包清空；内置包不受影响 | 数据链 ✅（同测 Removed）；**UI 待手点** |

手测样本（已 `adb push`）：`/sdcard/Download/PowerAiKb_B_sample/`（含 `knowledge_base.json` + `shots/`）。

## C. Android 16 / API 36 仪器测试

| # | 步骤 | 期望 |
|---|------|------|
| C1 | CI `connectedAndroidTest` on API 36 emulator | 全绿 |
| C2 | 本地 `./gradlew connectedDebugAndroidTest`（有设备时） | ✅ 2026-10-27：XQ-DQ72 / Android 15 (API 35)；`connectedDebugAndroidTest` **10/10** 全绿（签名冲突已先 `adb uninstall` 再装 debug） |

## 记录模板

验收后追加到 `CURRENT_STATE.md` §2：

```
| 真机验收 <主题> | 已验收 | <设备/API>；<日期>；关键断言摘要 |
```
