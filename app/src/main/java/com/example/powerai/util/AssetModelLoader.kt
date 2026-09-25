package com.example.powerai.util

import android.content.res.AssetManager
import android.net.Uri
// android.util.Log removed per TODO order; logging replaced with comments if needed
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.IOException
import java.io.InputStream

class AssetModelLoader(private val assets: AssetManager) : ModelLoader<Uri, InputStream> {
    // TAG constant removed; logging suppressed
    override fun buildLoadData(model: Uri, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        val s = model.toString()
        // log removed: buildLoadData called for: $s
        if (!s.startsWith("file:///android_asset/")) return null
        val assetPath = s.removePrefix("file:///android_asset/")
        return ModelLoader.LoadData(ObjectKey(model), AssetDataFetcher(assets, assetPath))
    }

    override fun handles(model: Uri): Boolean = model.toString().startsWith("file:///android_asset/")

    class Factory(private val assets: AssetManager) : ModelLoaderFactory<Uri, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, InputStream> {
            return AssetModelLoader(assets)
        }

        override fun teardown() {}
    }

    class AssetDataFetcher(private val assets: AssetManager, private val assetPath: String) : DataFetcher<InputStream> {
        // TAG constant removed; logging suppressed
        private var stream: InputStream? = null

        override fun loadData(priority: com.bumptech.glide.Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            // log removed: loadData START for assetPath=$assetPath
            try {
                stream = assets.open(assetPath)
                // log removed: loadData opened stream for $assetPath
                callback.onDataReady(stream)
            } catch (e: IOException) {
                // log removed: loadData failed for $assetPath
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() {
            try {
                // log removed: cleanup stream for $assetPath
                stream?.close()
            } catch (_: IOException) {}
        }

        override fun cancel() {}

        override fun getDataClass(): Class<InputStream> = InputStream::class.java

        override fun getDataSource(): DataSource = DataSource.LOCAL
    }
}
