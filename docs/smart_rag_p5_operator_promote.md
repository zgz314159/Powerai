# P5 可信晋升 — Operator 操作单

日期：2026-10-30  
Task：`rag-four-gaps-p0-1793284581`  
Branch：`aegl-task/rag-four-gaps-p0`  
交付提交（推送后）：以 `origin/aegl-task/rag-four-gaps-p0` HEAD 为准（撰写时 `d93cde6`）  
正式仓：`/home/zhong/AndroidStudioProjects/PowerAi`（只读；仅 broker 可写）

## 已具备（Agent 侧）

| 项 | 证据 |
|----|------|
| P0–P4 代码 | 分支提交链自 `449de71`…`b8569ad` |
| P5 固定集 | `v1_fixed_cases.json` 62 条，holdout≈23%；`SmartRagFixedEvalTest` |
| 真机 | XQ-DQ72：模板烟测、决策烟测、截图管道/UI |
| AEGL verify | `aegl-task task verify rag-four-gaps-p0-1793284581` → **PASS**（`verify.status`） |
| 入口脚本 | `scripts/run-tests.sh`（沙箱 JVM 门禁；不含 Mockito PromptManager） |

## Agent 无法完成的原因（事实）

1. **Controller token 缺失**：无 `/run/user/<uid>/aegl-controller.token`；Agent **不得**自造 token。
2. **Broker socket 对当前 UID 不可达**：`aegl-promote-broker.service` Active，但 `/run/aegl-promote/broker.sock` → `Permission denied`；`identity status` → `BROKER=NOT_AVAILABLE (not a socket)`。
3. `task register` → `UNREACHABLE`；`task promote` → `BLOCKED: controller token file missing`。
4. **白名单根本不含 PowerAi**：broker `AEGL_PROJECT_ALLOWLIST` / `promotion_core.ALLOWED_PROJECT_DIRS` 仅有  
   `Google-Android-Agent-Development-Stack`、`KeepLab-Relay`、`AI-Engineering-Governance-Layer`。  
   本任务正式路径为 `/home/zhong/AndroidStudioProjects/PowerAi`，**即便有 token 也会被拒绝**。  
   无密码 sudo，Agent 无法改 `/etc/systemd/system/aegl-promote-broker.service`。

因此：**push ≠ 可信晋升**；对本仓更现实的收口是 **GitHub PR 合入 `main`**，或由 operator 扩展 AEGL 白名单后再走 broker。

## Operator 步骤（建议）

```bash
# 0) 环境（按本机部署调整）
export AEGL_BROKER_SOCKET=/run/aegl-promote/broker.sock
export AEGL_CONTROLLER_TOKEN_FILE=/run/user/$(id -u)/aegl-controller.token
# 将 operator 密钥写入 token 文件（mode 600），并 export AEGL_CONTROLLER_TOKEN=<同内容>

# 1) 身份探针（须 BROKER=VERIFIED 且 socket owner ≠ 本 AI uid）
/home/zhong/Projects/AI-Engineering-Governance-Layer/bin/aegl-task identity status

# 2) 确认 verify 仍为 PASS（必要时重跑）
/home/zhong/Projects/AI-Engineering-Governance-Layer/bin/aegl-task task verify rag-four-gaps-p0-1793284581
cat /home/zhong/.local/state/aegl/tasks/rag-four-gaps-p0-1793284581/verify.status   # 期望 PASS

# 3) 登记 + 记录（recorder 身份）
/home/zhong/Projects/AI-Engineering-Governance-Layer/bin/aegl-task task register rag-four-gaps-p0-1793284581
/home/zhong/Projects/AI-Engineering-Governance-Layer/bin/aegl-task task record rag-four-gaps-p0-1793284581

# 4) 晋升
/home/zhong/Projects/AI-Engineering-Governance-Layer/bin/aegl-task task promote rag-four-gaps-p0-1793284581
# 期望：PROMOTE=APPLIED
```

晋升后核对正式仓 HEAD 是否包含本分支交付提交，并更新 Memory：`CURRENT_STATE.md` / `KNOWN_ISSUES.md` 将「可信晋升未完」改为已关闭。

## 非晋升残留（可选）

- 宿主机单独跑：`DeepSeekPromptManagerTest`（沙箱 MockMaker 受限，未纳入 verify）。
- 旧 A234「必须出现停止按钮」语义已改为接受 directAnswer 终态。
