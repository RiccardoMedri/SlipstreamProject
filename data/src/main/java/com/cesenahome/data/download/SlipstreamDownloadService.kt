package com.cesenahome.data.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import com.cesenahome.data.R

@UnstableApi
class SlipstreamDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadComponents.NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    ///Returns the DownloadManager initialized in DownloadComponents
    override fun getDownloadManager(): DownloadManager {
        return DownloadComponents.getDownloadManager(applicationContext)
    }

    ///Returns a Scheduler to reschedule downloads if they fail or after reboot
    @RequiresPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    override fun getScheduler(): Scheduler? {
        return WorkManagerScheduler(applicationContext, WORK_NAME)
    }

    ///Returns a notification to be displayed when the service is running in the foreground.
    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val helper = DownloadComponents.getNotificationHelper(applicationContext)
        val contentIntent = createContentIntent()
        return helper.buildProgressNotification(
            this,
            R.drawable.ic_download_notification,
            contentIntent,
            null,
            downloads,
            notMetRequirements
        )
    }

    //Points to app’s launch Activity, so tapping the notification brings user back in
    private fun createContentIntent(): PendingIntent? {
        val launchIntent = packageManager?.getLaunchIntentForPackage(packageName) ?: return null
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, launchIntent, flags)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            val channel = NotificationChannel(
                DownloadComponents.NOTIFICATION_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val WORK_NAME = "SlipstreamDownloadServiceWork"
        private const val FOREGROUND_NOTIFICATION_ID = 0x444F574E
    }
}