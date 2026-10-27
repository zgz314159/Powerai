# 真机 / 仪器验收清单（不伪完成）

> Phase D5。下列项需在真实设备或 CI 仪器环境执行后，才能在 [CURRENT_STATE.md](../CURRENT_STATE.md) 标为「已验收」。**未执行前保持「待真机」。**

## A. 端侧推理停止 + 中文输出

设备：Android 16（或目标机型）· DeepSeek/Smart 路径可用时

| # | 步骤 | 期望 |
|---|------|------|
| A1 | 加载本地模型，发起 grounded 生成 | 流式输出可见 |
| A2 | 生成中点「停止」 | UI 进入停止中→已取消；不再追加 token；无「推理失败」误报 |
| A3 | 中文查询（技规类） | 回答为可读中文；无明显乱码 / 英文串扰主导 |
| A4 | 再开新一轮生成 | 可正常开始；上一轮 job 已取消 |

JVM 护栏（已有，不替代真机）：`DeepSeekGenerationStopTest` 等。

## B. KI-54 用户目录 KB 导入全链路

| # | 步骤 | 期望 |
|---|------|------|
| B1 | 系统目录选择器选用户包目录 | 导入成功；条目数与样本一致（参考 48/839/48） |
| B2 | 关联原始 PDF（SHA-256） | 匹配成功；不匹配有明确提示 |
| B3 | 「本地」搜索表格题注查询 | 命中表格；详情/截图可开 |
| B4 | 「在 PDF 中定位」 | 黄框落在正确页（如 29/32） |
| B5 | 抽屉「更新（选同一目录）」JSON 未变 | 跳过 |
| B6 | 「移除」用户包 | 本包清空；内置包不受影响 |

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
