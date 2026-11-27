package com.nzel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.nzel.app.dl.DownloadEngine
import com.nzel.app.dl.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NzelApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(DownloadService.CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        appScope.launch(Dispatchers.IO) { DownloadEngine.init(this@NzelApp) }
    }

    companion object {
        lateinit var instance: NzelApp
            private set
    }
}
