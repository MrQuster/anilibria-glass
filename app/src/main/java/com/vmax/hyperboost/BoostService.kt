package com.vmax.hyperboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BoostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Controller.init(this)
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("boost", "Усилитель", NotificationManager.IMPORTANCE_LOW)
        )
        ServiceCompat.startForeground(
            this, 1, build("Усилитель активен"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        Controller.acquirePolling()
        scope.launch {
            Controller.state.collect { s ->
                val a = s.devices.firstOrNull { it.isActive }
                val text = if (a == null) "Нет активного устройства" else "${a.name}: +${a.boost}%"
                nm.notify(1, build(text))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        Controller.releasePolling()
        super.onDestroy()
    }

    private fun build(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "boost")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("HyperBoost")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
