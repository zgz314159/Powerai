package com.example.powerai.ui.screen.detail

import android.content.Context

internal fun dpToPx(context: Context, dp: Int): Int =
    (dp * context.resources.displayMetrics.density).toInt()
