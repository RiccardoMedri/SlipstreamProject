package com.cesenahome.data.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object DownloadComponents {

    private const val DOWNLOAD_FOLDER = "media_downloads"
    internal const val NOTIFICATION_CHANNEL_ID = "slipstream_downloads"

    private val lock = Any()

    @Volatile
    private var databaseProvider: DatabaseProvider? = null

    @Volatile
    private var downloadCache: Cache? = null

    @Volatile
    private var downloaderFactory: DownloaderFactory? = null

    @Volatile
    private var downloadManager: DownloadManager? = null

    @Volatile
    private var notificationHelper: DownloadNotificationHelper? = null

    //Creates a DownloadManager with the application's context
    fun getDownloadManager(context: Context): DownloadManager {
        val appContext = context.applicationContext
        return downloadManager ?: synchronized(lock) {
            downloadManager ?: createDownloadManager(appContext).also { downloadManager = it }
        }
    }

    fun getNotificationHelper(context: Context): DownloadNotificationHelper {
        val appContext = context.applicationContext
        return notificationHelper ?: synchronized(lock) {
            notificationHelper ?: DownloadNotificationHelper(appContext, NOTIFICATION_CHANNEL_ID).also {
                notificationHelper = it
            }
        }
    }

    private fun createDownloadManager(context: Context): DownloadManager {
        val db = getDatabaseProvider(context)
        val cache = getDownloadCache(context)
        val http = DefaultHttpDataSource.Factory()

        // Use the constructor instead of DownloadManager.Builder(...)
        return DownloadManager(
            /* context = */ context,
            /* databaseProvider = */ db,
            /* cache = */ cache,
            /* upstreamFactory = */ http,
            /* executor = */ Executors.newFixedThreadPool(2)
        ).apply {
            // optional tuning:
            maxParallelDownloads = 3
        }
    }

    private fun getDownloaderFactory(context: Context): DownloaderFactory {
        return downloaderFactory ?: synchronized(lock) {
            downloaderFactory ?: DefaultDownloaderFactory(
                buildCacheDataSourceFactory(context)
            ).also { downloaderFactory = it }
        }
    }

    private fun buildCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        return CacheDataSource.Factory()
            .setCache(getDownloadCache(context))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
    }

    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        return databaseProvider ?: synchronized(lock) {
            databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also { databaseProvider = it }
        }
    }

    fun getDownloadCache(context: Context): Cache {
        return downloadCache ?: synchronized(lock) {
            downloadCache ?: createCache(context.applicationContext).also { downloadCache = it }
        }
    }

    private fun createCache(context: Context): Cache {
        val dir = getDownloadDirectory(context)
        return SimpleCache(dir, NoOpCacheEvictor(), getDatabaseProvider(context))
    }

    private fun getDownloadDirectory(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
        val parent = externalDir ?: context.filesDir
        return File(parent, DOWNLOAD_FOLDER).apply { mkdirs() }
    }
}