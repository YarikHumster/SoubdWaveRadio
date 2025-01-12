package com.yaros.RadioUrl.helpers

import android.util.Log
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import timber.log.Timber
import java.io.File

@UnstableApi
object CacheManager {
    private var cacheSize: Long = 200 * 1024 * 1024 // 200 MB
    private const val TAG = "CacheManager"
    var simpleCache: SimpleCache? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private const val PREF_NAME = "CachePreferences"
    internal var PREF_CACHE_STATUS = "PREF_CACHE_STATUS"

    fun initializeCache(context: Context) {
        if (simpleCache == null) {
            Timber.tag(TAG).d("Initializing cache...")
            val cacheDir = File(context.cacheDir, "media")
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
            Timber.tag(TAG).d("Cache initialized at ${cacheDir.absolutePath}")
        }
    }

    fun isCachingEnabled(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(PREF_CACHE_STATUS, false)
    }

    fun disableCaching(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(PREF_CACHE_STATUS, false).apply()
        Timber.tag(TAG).d("Caching is disabled")
    }

    fun enableCaching(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(PREF_CACHE_STATUS, true).apply()
        Timber.tag(TAG).d("Caching is enabled")
    }

    fun setCacheSize(context: Context, sizeInMb: Long) {
        if (cacheSize != sizeInMb * 1024 * 1024) {
            cacheSize = sizeInMb * 1024 * 1024
            Timber.tag(TAG).d("Cache size set to $sizeInMb MB")
            clearCache(context)
            initializeCache(context)
        }
    }

    fun getDataSourceFactory(context: Context): DataSource.Factory? {
        if (!isCachingEnabled(context)) {
            Timber.tag(TAG).d("Caching is disabled, returning DefaultDataSourceFactory")
            return DefaultDataSourceFactory(context, "SoundWaveRadio")
        }
        if (cacheDataSourceFactory == null) {
            if (simpleCache == null) {
                Timber.tag(TAG).d("Cache is not initialized, initializing...")
                initializeCache(context)
            }
            val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(context, "SoundWaveRadio")
            cacheDataSourceFactory = simpleCache?.let {
                CacheDataSource.Factory()
                    .setCache(it)
                    .setUpstreamDataSourceFactory(dataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            }
            if (cacheDataSourceFactory == null) {
                Timber.tag(TAG).e("Failed to initialize CacheDataSource.Factory")
                return null
            }
            Timber.tag(TAG).d("Data source factory initialized with cache enabled")
        } else {
            Timber.tag(TAG).d("Returning existing data source factory")
        }
        return cacheDataSourceFactory
    }

    fun clearCache(context: Context) {
        Timber.tag(TAG).d("Clearing cache...")
        simpleCache?.apply {
            try {
                release()
                Timber.tag(TAG).d("Cache released")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error releasing cache")
            }
        }
        val cacheDirectory = File(context.cacheDir, "media")
        if (cacheDirectory.exists()) {
            val deleted = cacheDirectory.deleteRecursively()
            Timber.tag(TAG).d("Cache directory ${if (deleted) "cleared" else "not cleared"}")
        }
        simpleCache = null
        initializeCache(context)
    }

    fun onDestroy() {
        Timber.tag(TAG).d("Releasing cache on destroy...")
        try {
            simpleCache?.release()
            Timber.tag(TAG).d("Cache released")
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).e(e, "Error releasing cache on destroy")
        } finally {
            simpleCache = null
        }
    }
}
