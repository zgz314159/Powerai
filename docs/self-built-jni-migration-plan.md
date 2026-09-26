# 自编 JNI 迁移计划

## 目标

- 第一步先跑通 CPU 路线，只验收两件事：停止不再长时间卡死、中文输出不再乱码。
- 第二步再处理 GPU 接入，不把 OpenCL/Vulkan 问题混进 CPU 首轮闭环。

## 当前策略

- 保留现有 AAR 路线，避免一次性替换造成 SMART 页整体回退。
- 新增独立 runtime 抽象，把 AAR 实现和自编 JNI 实现隔离。
- 通过 Gradle 开关控制是否启用自编 JNI 实验路径，默认关闭。

## 24 小时 CPU 验收标准

- 模型可从设备绝对路径成功加载。
- 流式输出能持续回调，不依赖 AAR 反射桥。
- 停止操作能在当前轮推理中断，不再出现明显超时卡住。
- 中文 token 在 UI 最终展示中保持正常 UTF-8 文本，不出现持续乱码。

## 已落地边界

- `DeepSeekNativeRuntime` 作为统一契约。
- `LegacyAarDeepSeekRuntime` 继续承接当前稳定路径。
- `LlamaCppDeepSeekRuntime` 承接自编 JNI CPU 实验路径。
- `DeepSeekNativeRuntimeBridge` 负责运行时选择和失败回退。

## 已完成：SMART/UI 层解除对 `LlamaJni` 的直接依赖

- **状态：已完成。** SMART 页面及其 ViewModel 消费链不再直接调用 `LlamaJni`；推理能力经现有 engine 抽象访问。
- **完成证据**：2026-09-26 只读扫描（范围：`app`、`feature`、`core` 的 `src/main` 与测试源码，已排除 `build/`、`.gradle/`、`.git/`、文档与归档）——**UI/业务消费层引用为 0**：SMART 页面 0、相关 ViewModel 0、`app/src/main` 0、`feature/search-chat/src/main` 0、`core/model-contract/src/main` 与 `core/data/src/main` 0、测试源码 0。
- **范围限定**：本项仅指 UI 解耦，不表示 engine 内部实现或 native 侧已清理，见下方“当前限制”。

## 当前限制：底层清理尚未完成

- `engine/ai` 内部仍保留 `LlamaJni` 相关引用；`LegacyAarDeepSeekRuntime` 与 `LlamaJniReflection` 仍直接依赖或适配该实现。
- native 导出符号与旧兼容入口仍然存在，旧 JNI 实现未移除。
- 同一次扫描在生产 `src/main` 命中 61 行，全部位于 `engine/ai` 与 `engine/native`（2026-09-26 快照，统计范围见上节，**非稳定常量**）。
- 因此「全局移除 `LlamaJni`」尚未完成。

## 下一步

- 真机验证 CPU 路线的停止行为和中文输出。
- CPU 路线稳定后，再评估 GPU 参数和 native backend 扩展。