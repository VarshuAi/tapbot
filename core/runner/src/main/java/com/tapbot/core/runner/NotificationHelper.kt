package com.tapbot.core.runner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Handles creation and updates of the persistent notification required
 * by Android Foreground Services.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TapBot Runner Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active local Telegram bots running in the background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(
        runningBotsCount: Int,
        activeBotNames: List<String>
    ): Notification {
        val title = if (runningBotsCount == 0) {
            "TapBot Runner Idle"
        } else {
            "TapBot Running $runningBotsCount Bot${if (runningBotsCount > 1) "s" else ""}"
        }

        val content = if (activeBotNames.isEmpty()) {
            "Background runner service is active"
        } else {
            activeBotNames.joinToString(", ")
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        val stopIntent = Intent(context, BotForegroundService::class.java).apply {
            action = BotForegroundService.ACTION_STOP_ALL
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop All", stopPendingIntent)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "tapbot_runner_channel"
        const val NOTIFICATION_ID = 1001
    }
}
