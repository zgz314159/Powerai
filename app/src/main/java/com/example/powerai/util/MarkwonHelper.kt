package com.example.powerai.util

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.util.TypedValue
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageSizeResolver
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

object MarkwonHelper {
    private const val TAG = "MarkwonHelper"

    @Volatile
    private var cachedMarkwonWithTables: Markwon? = null

    @Volatile
    private var cachedMarkwonNoTables: Markwon? = null
    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    fun create(context: Context): Markwon {
        cachedMarkwonWithTables?.let {
            Log.d(TAG, "returning cached Markwon (with tables)")
            return it
        }

        synchronized(this) {
            cachedMarkwonWithTables?.let {
                Log.d(TAG, "returning cached Markwon (with tables)")
                return it
            }
            val markwon = buildMarkwon(context, enableTables = true)
            cachedMarkwonWithTables = markwon
            return markwon
        }
    }

    /**
     * Fallback renderer that does NOT enable TablePlugin.
     *
     * Used when Markwon table rendering crashes at draw time (e.g. TableRowSpan divide-by-zero).
     */
    fun createNoTables(context: Context): Markwon {
        cachedMarkwonNoTables?.let {
            Log.d(TAG, "returning cached Markwon (no tables)")
            return it
        }

        synchronized(this) {
            cachedMarkwonNoTables?.let {
                Log.d(TAG, "returning cached Markwon (no tables)")
                return it
            }
            val markwon = buildMarkwon(context, enableTables = false)
            cachedMarkwonNoTables = markwon
            return markwon
        }
    }

    private fun buildMarkwon(context: Context, enableTables: Boolean): Markwon {
        Log.d(TAG, "buildMarkwon: enableTables=$enableTables")

        val builder = Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        16f,
                        context.resources.displayMetrics
                    )
                ) { latexBuilder ->
                    latexBuilder.inlinesEnabled(true)
                    latexBuilder.theme()
                        .blockFitCanvas(true)
                        .blockPadding(JLatexMathTheme.Padding.symmetric(0, dpToPx(context, 8)))
                        .inlinePadding(JLatexMathTheme.Padding.symmetric(dpToPx(context, 2), dpToPx(context, 1)))
                }
            )
            // Render minimal HTML tags produced by preprocessing (notably <br/> in table cells)
            .usePlugin(HtmlPlugin.create())

        if (enableTables) {
            builder.usePlugin(
                TablePlugin.create { themeBuilder: TableTheme.Builder ->
                    themeBuilder
                        .tableBorderColor(Color.parseColor("#BDBDBD"))
                        .tableBorderWidth(dpToPx(context, 1))
                        .tableCellPadding(dpToPx(context, 8))
                        .tableHeaderRowBackgroundColor(Color.parseColor("#F5F5F5"))
                }
            )
        }

        // Images: do NOT force-enlarge. Only shrink when image would exceed canvas width.
        builder.usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                val resolver: ImageSizeResolver = object : ImageSizeResolver() {
                    override fun resolveImageSize(drawable: AsyncDrawable): Rect {
                        // NOTE:
                        // ImageSizeResolverDef 在某些 Drawable(intrinsicWidth=0) 场景下可能触发整数除零。
                        // 这里完全使用自定义逻辑，确保不会抛出 ArithmeticException。
                        return try {
                            val canvasWidth = drawable.lastKnownCanvasWidth
                            val iw = drawable.intrinsicWidth
                            val ih = drawable.intrinsicHeight

                            // 如果拿不到图片固有尺寸（0 或 -1），给一个最小尺寸，避免后续计算除零。
                            if (iw <= 0 || ih <= 0) {
                                val w = if (canvasWidth > 0) canvasWidth else 1
                                return Rect(0, 0, w, 1)
                            }

                            // 默认使用固有尺寸
                            var w = iw
                            var h = ih

                            // 仅在超出画布宽度时缩小（不放大）
                            if (canvasWidth > 0 && w > canvasWidth) {
                                val ratio = w.toFloat() / canvasWidth.toFloat()
                                w = canvasWidth
                                h = (h / ratio + 0.5f).toInt().coerceAtLeast(1)
                            }

                            Rect(0, 0, w.coerceAtLeast(1), h.coerceAtLeast(1))
                        } catch (t: Throwable) {
                            Log.w(TAG, "imageSizeResolver: fallback due to ${t.javaClass.simpleName}: ${t.message}")
                            Rect(0, 0, 1, 1)
                        }
                    }
                }
                builder.imageSizeResolver(resolver)
            }
        })

        // Register image plugins: configure Glide RequestManager with placeholder/error
        try {
            val requestManager = Glide.with(context).apply {
                try {
                    val displayWidth = context.resources.displayMetrics.widthPixels
                    val ro = RequestOptions()
                        .placeholder(android.graphics.drawable.ColorDrawable(android.graphics.Color.LTGRAY))
                        .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.RED))
                        .fitCenter()
                        .override(displayWidth)
                    setDefaultRequestOptions(ro)
                } catch (_: Throwable) {
                }
            }
            builder.usePlugin(GlideImagesPlugin.create(requestManager))
            Log.d(TAG, "GlideImagesPlugin registered with request defaults (asset support relies on AppGlideModule)")
        } catch (t: Throwable) {
            Log.w(TAG, "GlideImagesPlugin registration failed", t)
        }

        val markwon = builder.build()
        Log.d(TAG, "Markwon build complete")
        return markwon
    }
}
