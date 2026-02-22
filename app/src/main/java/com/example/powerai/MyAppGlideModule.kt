package com.example.powerai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.example.powerai.util.AssetModelLoader
import java.io.InputStream

@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Register our AssetModelLoader for Uri -> InputStream so Glide can load asset URIs
        val TAG = "MyAppGlideModule"
        Log.d(TAG, "registerComponents: registering AssetModelLoader for Uri and String")
        registry.append(Uri::class.java, InputStream::class.java, AssetModelLoader.Factory(context.assets))
        // Also register for String -> InputStream so Glide can load asset URIs passed as strings
        registry.append(
            String::class.java,
            InputStream::class.java,
            object : com.bumptech.glide.load.model.ModelLoaderFactory<String, InputStream> {
                override fun build(multiFactory: com.bumptech.glide.load.model.MultiModelLoaderFactory): com.bumptech.glide.load.model.ModelLoader<String, InputStream> {
                    return object : com.bumptech.glide.load.model.ModelLoader<String, InputStream> {
                        override fun buildLoadData(model: String, width: Int, height: Int, options: com.bumptech.glide.load.Options): com.bumptech.glide.load.model.ModelLoader.LoadData<InputStream>? {
                            if (!model.startsWith("file:///android_asset/")) return null
                            val assetPath = model.removePrefix("file:///android_asset/")
                            Log.d(TAG, "String model loader buildLoadData for: $model")
                            return com.bumptech.glide.load.model.ModelLoader.LoadData(com.bumptech.glide.signature.ObjectKey(model), com.example.powerai.util.AssetModelLoader.AssetDataFetcher(context.assets, assetPath))
                        }

                        override fun handles(model: String): Boolean = model.startsWith("file:///android_asset/")
                    }
                }

                override fun teardown() {}
            }
        )
    }
}
