package com.example.powerai.util

import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
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
    private val TAG = "AssetModelLoader"
    override fun buildLoadData(model: Uri, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        val s = model.toString()
        Log.d(TAG, "buildLoadData called for: $s")
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
        private val TAG = "AssetDataFetcher"
        private var stream: InputStream? = null

        override fun loadData(priority: com.bumptech.glide.Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            Log.d(TAG, "loadData START for assetPath=$assetPath thread=${Thread.currentThread().name} ts=${System.currentTimeMillis()}")
            try {
                stream = assets.open(assetPath)
                Log.d(TAG, "loadData opened stream for $assetPath available=${stream?.available()}")
                callback.onDataReady(stream)
            } catch (e: IOException) {
                Log.w(TAG, "loadData failed for $assetPath", e)
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() {
            try {
                Log.d(TAG, "cleanup stream for $assetPath thread=${Thread.currentThread().name} ts=${System.currentTimeMillis()}")
                stream?.close()
            } catch (_: IOException) {}
        }

        override fun cancel() {}

        override fun getDataClass(): Class<InputStream> = InputStream::class.java

        override fun getDataSource(): DataSource = DataSource.LOCAL
    }
}
