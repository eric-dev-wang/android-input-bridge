package com.ericdevwang.androidinputbridge.plugin.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

fun interface InputBridgeNotifier {
    fun notify(message: String)
}

class IntelliJInputBridgeNotifier(
    private val project: Project,
) : InputBridgeNotifier {
    override fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    private companion object {
        const val NOTIFICATION_GROUP_ID = "Android Input Bridge"
    }
}
