package com.ericdevwang.androidinputbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ericdevwang.androidinputbridge.MainActivity
import com.ericdevwang.androidinputbridge.R

internal object InputBridgeNotification {
    const val CHANNEL_ID = "input_bridge_server"
    const val NOTIFICATION_ID = 18080

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.server_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun build(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_input_bridge)
            .setContentTitle(context.getString(R.string.server_notification_title))
            .setContentText(context.getString(R.string.server_notification_text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    createLaunchIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    internal fun createLaunchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
