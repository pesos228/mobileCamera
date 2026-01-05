package com.android.mobilecamera.feature.gallery

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

object ImageLoaderProvider {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .memoryCache {
                    MemoryCache.Builder(context)
                        .maxSizePercent(0.25) // 25% RAM
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(100 * 1024 * 1024) // 100 MB
                        .build()
                }
                .crossfade(150) // Быстрая анимация
                .respectCacheHeaders(false)
                .build()
                .also { instance = it }
        }
    }
}