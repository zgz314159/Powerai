# P0 基线核对（2026-10-29）

## 环境

| 项 | 值 |
|----|-----|
| 正式仓 HEAD（核对时） | `f1a124d` — `fix(smart): 库内有条文却误报资料不足` |
| AEGL task | `rag-four-gaps-p0-1793284581` |
| worktree 分支 | `aegl-task/rag-four-gaps-p0` @ `f1a124d` |
| 设备 | Sony XQ-DQ72 / Android 15（`adb devices` 已连接） |
| 机内模型 | 仅见 `files/models/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf` |
| 依赖 | `net.ladenthin:llama-android:6.0.0`（`gradle/libs.versions.toml`） |

## 已确认事实（代码 + 设备日志）

1. **模板未应用**：`LlamaCppDeepSeekRuntime.buildInferenceParameters` 使用 `InferenceParameters.of(merged)` 拼普通文本；库 6.0.0 另有 `withMessages(List<ChatMessage>)`，当前路径未调用。
2. **systemPrompt**：以字符串前缀拼进 prompt，**不是** chat role 的 system message。
3. **采样/停止**：仅见 `withNPredict` / `withStream` / 若干 stop 字符串；temperature/top-p/seed 未显式写入诊断。
4. **检修问句（22:13:52）**：`confidence=CORRECT`，证据 `10kV接地` 已入 Prompt；随后有生成（`ANSWER_START`）。方案所述「最终短拒答」属生成/解析/守卫链问题，**不是**检索 0 命中。（完整 raw 正文未完整落盘，不能断言是模型拒答还是守卫替换。）
5. **停电问句（22:14:56）**：`confidence=AMBIGUOUS`，证据第 34 条含「一人操作,一人监护」进入 Prompt；现网曾展示改写职责关系——说明弱证据仍会生成，现有守卫未拦语义改写。
6. **分级**：`SmartEvidenceGrader` 为词典/词面/电压启发式（阈值 0.55/0.28），**不是**论文级 CRAG。
7. **来源**：`power_safety_rules.json` + `PowerSafetyRulesAssetImporter.DISPLAY_SOURCE = "电力安全工作规程"`；字段不能证明条号/版本/效力。
8. **守卫截断**：`SmartAnswerGuard.fallbackFromEvidence` 使用 `.take(220)` 固定截断。

## 假设（未实验前不得当根因闭环）

- 应用 messages 模板后假拒答/胡编是否显著下降。
- Thinking 解析是否吞掉正文。
- Prompt 裁剪 / prefix cache 是否错配。
- 量化本身是否为主因（无对照实验前不认定）。

## 失败定位矩阵（P0 出口）

| 现象 | 优先排查层 |
|------|------------|
| 证据 0 / INCORRECT | 检索 / FTS-LIKE / sparse / 相关度门闸 |
| CORRECT 仍「资料不足」 | 生成 / 解析 / AnswerGuard / 展示替换 |
| AMBIGUOUS 改写职责 | 决策路由（应 PARTIAL/原文）+ 生成核验 |
| 冠名「规程」的短句 | 来源资格（P2） |

## 门禁备注

- 正式目录当前对本 UID 仍可写；**本任务约定只改 AEGL worktree**，不直接改正式 checkout。
- AEGL `task verify` 默认跑 `scripts/run-tests.sh`（本仓可能无此脚本）或 `mise run test`；Android 模块验证改用 host `GRADLE_USER_HOME=~/.gradle` + worktree `local.properties`（gitignore），日志写入 AEGL scratch；不在正式目录执行 Gradle。
- 可信晋升（broker）与 `git push` 分支分开记录。

## P1 进展（2026-10-29 夜）

| 项 | 结果 |
|----|------|
| `withMessages` 单 user（system 折叠） | 已实现；JVM 单测绿 |
| Prompt 前缀缓存键绑定正文 fp + tpl | 已实现 |
| `DeepSeekGenerationStopTest` | UncompletedCoroutinesError 抖动（与历史一致）；**未标回归通过** |
| 同证据真机 A/B | **未执行** → P1 出口未关闭 |
