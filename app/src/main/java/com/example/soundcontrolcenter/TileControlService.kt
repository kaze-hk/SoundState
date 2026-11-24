package com.example.soundcontrolcenter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TileControlService : Service() {
    companion object {
        var running by mutableStateOf(false)
            private set
        const val ENABLED = "background_controls_enabled"
        const val READY = "controls_ready"
        private const val CHANNEL = "background_controls"
        const val STOP = "com.example.soundcontrolcenter.STOP_CONTROLS"
    }

    @RequiresApi(37)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            tilePreferences().edit().putBoolean(ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            getString(R.string.background_controls), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0,
            Intent(this, TileControlService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_wave)
            .setContentTitle(getString(R.string.controls_active))
            .setContentText(getString(R.string.controls_notification_body))
            .setStyle(Notification.BigTextStyle().bigText(getString(R.string.controls_notification_body)))
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop_controls), stop).build())
            .build()
        startForeground(1, notification)
        running = true
        tilePreferences().edit().putBoolean(ENABLED, true).apply()
        intent?.getParcelableExtra(READY, android.os.ResultReceiver::class.java)?.send(0, null)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
