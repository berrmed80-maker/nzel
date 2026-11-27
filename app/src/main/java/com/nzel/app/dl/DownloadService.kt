package com.nzel.app.dl

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.nzel.app.R

class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(this, getString(R.string.notif_downloading), null))
        return START_STICKY
    }

    companion object {
        const val NOTIF_ID = 41
        const val CHANNEL = "downloads"

        fun start(context: Context) {
            val i = Intent(context, DownloadService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIF_ID, buildNotification(context, context.getString(R.string.notif_done), null))
        }

        fun update(context: Context, percent: Int) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIF_ID, buildNotification(context, context.getString(R.string.notif_downloading), percent))
        }

        private fun buildNotification(context: Context, text: String, percent: Int?): Notification {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pi = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val b = Notification.Builder(context, CHANNEL)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pi)
                .setOngoing(percent != null)
            if (percent != null) b.setProgress(100, percent, percent < 0)
            return b.build()
        }
    }
}
