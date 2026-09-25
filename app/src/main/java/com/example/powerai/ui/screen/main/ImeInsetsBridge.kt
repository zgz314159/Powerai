package com.example.powerai.ui.screen.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 通过 ViewCompat 监听 IME Insets，返回用于贴键盘顶部Y 轴偏移（负值表示上移）
 * 仅在 [enabled] true 时生效，避免对非弹层场景产生影响
 */
@Composable
internal fun rememberImeTranslationY(enabled: Boolean): Float {
    if (!enabled) return 0f

    val view = LocalView.current
    var imeBottomPx by remember(view) { mutableFloatStateOf(0f) }

    DisposableEffect(view, enabled) {
        val listener = androidx.core.view.OnApplyWindowInsetsListener { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            imeBottomPx = (imeBottom - navBottom).coerceAtLeast(0).toFloat()
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, listener)
        ViewCompat.requestApplyInsets(view)

        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = -imeBottomPx,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ime_translation_y"
    )
    return animatedOffset
}
