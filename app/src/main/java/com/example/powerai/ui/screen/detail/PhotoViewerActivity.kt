package com.example.powerai.ui.screen.detail

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.powerai.R
import com.example.powerai.ui.image.AssetImageUriNormalizer

class PhotoViewerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)

        val photoView = findViewById<ImageView>(R.id.photo_view)
        val uriRaw = intent?.getStringExtra(EXTRA_IMAGE_URI)
        val TAG = "PhotoViewerActivity"
        val uri = uriRaw?.let { AssetImageUriNormalizer.normalize(it) }?.takeIf { it.isNotBlank() }
        Log.d(TAG, "onCreate received uri=$uri")
        uri?.let { target ->
            try {
                Log.d(TAG, "Glide PhotoViewer START uri=$target thread=${Thread.currentThread().name} nativeAlloc=${Debug.getNativeHeapAllocatedSize()} freeMem=${Runtime.getRuntime().freeMemory()}")
                Glide.with(photoView)
                    .load(target)
                    .error(android.R.drawable.ic_menu_report_image)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, targetGl: Target<Drawable>, isFirstResource: Boolean): Boolean {
                            Log.w(TAG, "Glide PhotoViewer FAILED uri=$target", e)
                            return false
                        }

                        override fun onResourceReady(resource: Drawable, model: Any, targetGl: Target<Drawable>, dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean): Boolean {
                            Log.d(TAG, "Glide PhotoViewer DONE uri=$target thread=${Thread.currentThread().name} nativeAlloc=${Debug.getNativeHeapAllocatedSize()} freeMem=${Runtime.getRuntime().freeMemory()}")
                            return false
                        }
                    })
                    .into(photoView)
            } catch (e: Exception) {
                Log.w(TAG, "Glide load failed for $target", e)
            }
        }
    }
}
