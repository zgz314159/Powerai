package com.example.powerai.ui.screen.detail

import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.widget.TextView
import com.example.powerai.ui.image.AssetImageUriNormalizer

internal object KnowledgeDetailMarkwonInteractions {
    fun handleTapOrNull(tv: TextView, context: Context, ev: MotionEvent): Boolean? {
        val offset = try {
            tv.getOffsetForPosition(ev.x, ev.y)
        } catch (_: Throwable) {
            return null
        }

        val text = tv.text
        if (text !is Spannable) return null

        val imageUri = findTappedImageUri(text, offset)
        if (!imageUri.isNullOrBlank()) {
            context.startActivity(
                Intent(context, PhotoViewerActivity::class.java).apply {
                    putExtra(PhotoViewerActivity.EXTRA_IMAGE_URI, imageUri)
                }
            )
            return true
        }

        val urlUri = findTappedUrlUri(text, offset)
        if (!urlUri.isNullOrBlank()) {
            context.startActivity(
                Intent(context, PhotoViewerActivity::class.java).apply {
                    putExtra(PhotoViewerActivity.EXTRA_IMAGE_URI, urlUri)
                }
            )
            return true
        }

        return null
    }

    private fun findTappedImageUri(text: Spannable, offset: Int): String? {
        val images = text.getSpans(offset, offset, ImageSpan::class.java)
        if (images.isEmpty()) return null

        val srcRaw = images[0].source.orEmpty()
        return normalizeAssetImageUri(srcRaw)
    }

    private fun findTappedUrlUri(text: Spannable, offset: Int): String? {
        val urls = text.getSpans(offset, offset, URLSpan::class.java)
        if (urls.isEmpty()) return null

        val urlRaw = urls[0].url.orEmpty()
        if (urlRaw.isBlank()) return null

        if (urlRaw.startsWith("file:///android_asset/") || urlRaw.contains("assets/images")) {
            return normalizeAssetImageUri(urlRaw)
        }

        // Leave non-asset URLs to default Markwon URL handling.
        return null
    }

    private fun normalizeAssetImageUri(raw: String): String? {
        val normalized = AssetImageUriNormalizer.normalize(raw)
        if (normalized.isBlank()) return null

        // Only handle android_asset images here; let non-asset URLs fall back to default behavior.
        if (!normalized.startsWith("file:///android_asset/")) return null

        return normalized
    }
}
