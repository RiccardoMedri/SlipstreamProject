package com.cesenahome.data.download

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.cesenahome.data.R

@UnstableApi
class SlipstreamDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadComponents.NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {

    override fun getDownloadManager(): DownloadManager {
        return DownloadComponents.getDownloadManager(applicationContext)
    }

    @RequiresPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    override fun getScheduler(): Scheduler? {
        return if (Util.SDK_INT >= 21) {
            PlatformScheduler(this, JOB_ID)
        } else {
            null
        }
    }

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

    private fun createContentIntent(): PendingIntent? {
        val launchIntent = packageManager?.getLaunchIntentForPackage(packageName) ?: return null
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, launchIntent, flags)
    }

    companion object {
        private const val JOB_ID = 0x534C // Arbitrary but stable job id
        private const val FOREGROUND_NOTIFICATION_ID = 0x444F574E
    }
}