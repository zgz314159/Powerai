package com.example.powerai.ui.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ImageView(context, attrs, defStyleAttr) {

    private val drawMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    private var viewWidthPx = 0f
    private var viewHeightPx = 0f
    private var baseScale = 1f
    private var normalizedScale = 1f
    private var minScale = 1f
    private var maxScale = 6f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    var onSingleTap: (() -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = drawMatrix
        isClickable = true
    }

    fun setScaleBounds(min: Float, max: Float) {
        minScale = min.coerceAtLeast(1f)
        maxScale = max.coerceAtLeast(minScale)
        normalizedScale = normalizedScale.coerceIn(minScale, maxScale)
        fixTranslation()
        imageMatrix = drawMatrix
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { fitImageToView() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidthPx = w.toFloat()
        viewHeightPx = h.toFloat()
        fitImageToView()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (normalizedScale > minScale || dx != 0f || dy != 0f) {
                        drawMatrix.postTranslate(dx, dy)
                        fixTranslation()
                        imageMatrix = drawMatrix
                        isDragging = true
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                performClick()
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun fitImageToView() {
        val drawable = drawable ?: return
        if (viewWidthPx <= 0f || viewHeightPx <= 0f) return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f) return

        drawMatrix.reset()
        val scale = min(viewWidthPx / drawableWidth, viewHeightPx / drawableHeight)
        val redundantXSpace = viewWidthPx - scale * drawableWidth
        val redundantYSpace = viewHeightPx - scale * drawableHeight
        drawMatrix.postScale(scale, scale)
        drawMatrix.postTranslate(redundantXSpace / 2f, redundantYSpace / 2f)
        baseScale = scale
        normalizedScale = 1f
        imageMatrix = drawMatrix
    }

    private fun currentImageWidth(): Float {
        val drawable = drawable ?: return 0f
        return drawable.intrinsicWidth * baseScale * normalizedScale
    }

    private fun currentImageHeight(): Float {
        val drawable = drawable ?: return 0f
        return drawable.intrinsicHeight * baseScale * normalizedScale
    }

    private fun fixTranslation() {
        drawMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val fixX = getFixTranslation(transX, viewWidthPx, currentImageWidth())
        val fixY = getFixTranslation(transY, viewHeightPx, currentImageHeight())
        if (fixX != 0f || fixY != 0f) {
            drawMatrix.postTranslate(fixX, fixY)
        }
    }

    private fun getFixTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        return when {
            trans < minTrans -> minTrans - trans
            trans > maxTrans -> maxTrans - trans
            else -> 0f
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val targetScale = (normalizedScale * scaleFactor).coerceIn(minScale, maxScale)
            val factor = targetScale / normalizedScale
            normalizedScale = targetScale
            drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            fixTranslation()
            imageMatrix = drawMatrix
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val targetScale = if (normalizedScale < (minScale + maxScale) / 2f) {
                min(maxScale, max(2.5f, minScale))
            } else {
                minScale
            }
            val factor = targetScale / normalizedScale
            normalizedScale = targetScale
            drawMatrix.postScale(factor, factor, e.x, e.y)
            fixTranslation()
            imageMatrix = drawMatrix
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isDragging) {
                onSingleTap?.invoke()
            }
            return true
        }
    }
}
