package com.lumina.filemanager

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.VideoFrameDecoder
import com.lumina.filemanager.core.database.LuminaDatabase

class LuminaApplication : Application(), ImageLoaderFactory {
    val database: LuminaDatabase by lazy {
        Room.databaseBuilder(this, LuminaDatabase::class.java, "lumina.db")
            .addMigrations(LuminaDatabase.MIGRATION_1_2)
            .build()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbnails"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}