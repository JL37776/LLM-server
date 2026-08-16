package com.nzshores.llmserver.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nzshores.llmserver.MainActivity
import com.nzshores.llmserver.R

/**
 * Exists purely to keep the process alive while [KtorApiServer] is serving requests - Android
 * will kill a background process under memory pressure, which would silently drop the LAN server.
 */
class ApiForegroundService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val channel = NotificationChannel(CHANNEL_ID, "LLM server", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LLM Manager server running")
            .setContentText("Serving OpenAI-compatible requests on your LAN")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "llm_server_channel"
        private const val NOTIFICATION_ID = 42
    }
}
