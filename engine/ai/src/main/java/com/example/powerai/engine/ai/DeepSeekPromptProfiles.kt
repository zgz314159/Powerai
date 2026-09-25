package com.example.powerai.engine.ai

object DeepSeekPromptProfiles {
    const val QUICK_SYSTEM_PROMPT = "你是移动端速答助手。禁止输出 <think>、</think>、推理过程、分析说明、前言客套或分点步骤。必须从第一个字开始直接输出最终答案正文，用 1-2 句简洁中文完成回答。若本地资料不足，也只允许直接说明资料不足并给出简短常识性回答。"

    const val DEEP_SYSTEM_PROMPT = "请先在 <think></think> 中输出极短思路摘要，总长度尽量控制在两句内，只写检索依据和判断结论，不要解释背景，不要复述问题，不要逐字复述最终回答；随后直接输出给用户看的最终回答。"
}