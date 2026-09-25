package com.example.powerai.ui.mvi

/**
 * MVI 架构核心契约
 * @param Intent 用户意图/操作
 * @param State UI 状态（单一数据源）
 * @param Effect 一次性副作用（导航、Toast 等）
 */
interface MviContract<Intent, State, Effect> {
    /**
     * 处理用户 Intent，触发状态更新
     */
    fun onIntent(intent: Intent)
}
