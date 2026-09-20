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
 * Configured with non-aggressive IMPORTANCE_LOW to minimize user disruption.
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
                description = "Shows active Telegram bots running locally in the background"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the foreground service notification when a Telegram bot is actively running.
     * Follows Phase 3 specifications: displays bot handle and provides "Open" and "Stop" actions.
     */
    fun buildBotRunningNotification(
        botDisplayName: String,
        statusDetail: String = "Running locally on device"
    ): Notification {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = if (launchIntent != null) {
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
            action = BotForegroundService.ACTION_STOP_BOT
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("TapBot Runner")
            .setContentText("$botDisplayName is running")
            .setSubText(statusDetail)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Action 1: Open Application UI
        if (openPendingIntent != null) {
            builder.setContentIntent(openPendingIntent)
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "Open",
                openPendingIntent
            )
        }

        // Action 2: Stop Local Bot Runtime
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        )

        return builder.build()
    }

    fun buildForegroundNotification(
        runningBotsCount: Int,
        activeBotNames: List<String>
    ): Notification {
        val summaryText = if (runningBotsCount <= 1) {
            activeBotNames.firstOrNull() ?: "Telegram Bot"
        } else {
            "$runningBotsCount Bots (${activeBotNames.take(3).joinToString(", ")})"
        }
        return buildBotRunningNotification(summaryText, "Running locally in background")
    }

    companion object {
        const val CHANNEL_ID = "tapbot_runner_channel"
        const val NOTIFICATION_ID = 1001
    }
}
