package com.ericdevwang.inputbridge.plugin.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

fun interface InputBridgeNotifier {
    fun notify(message: String)
}

internal class IntelliJInputBridgeNotifier(
    private val project: Project?,
    private val notificationGroupLookup: (String) -> NotificationGroup? = { groupId ->
        NotificationGroupManager.getInstance().getNotificationGroup(groupId)
    },
) : InputBridgeNotifier {
    override fun notify(message: String) {
        val group = notificationGroupLookup(NOTIFICATION_GROUP_ID)
        if (group == null) {
            logger.warn("Notification group is not registered: $NOTIFICATION_GROUP_ID")
            return
        }
        group.createNotification(message, NotificationType.INFORMATION).notify(project)
    }

    private companion object {
        const val NOTIFICATION_GROUP_ID = "Input Bridge"
        val logger = Logger.getInstance(IntelliJInputBridgeNotifier::class.java)
    }
}
