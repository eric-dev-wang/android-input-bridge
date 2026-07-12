package com.ericdevwang.androidinputbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
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
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
