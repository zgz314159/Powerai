package com.example.powerai.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * MVI ViewModel 基类
 * 提供统一的状态管理和副作用处理
 */
abstract class BaseMviViewModel<Intent, State, Effect>(
    initialState: State
) : ViewModel(), MviContract<Intent, State, Effect> {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _effect = Channel<Effect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    /**
     * 当前状态的快捷访问
     */
    protected val currentState: State get() = _uiState.value

    /**
     * 更新 UI 状态
     */
    protected fun updateState(reducer: State.() -> State) {
        _uiState.value = currentState.reducer()
    }

    /**
     * 发送一次性副作用
     */
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch { _effect.send(effect) }
    }

    /**
     * 处理 Intent 的核心方法，子类必须实现
     */
    abstract override fun onIntent(intent: Intent)
}
