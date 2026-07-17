package com.ericdevwang.androidinputbridge.plugin.notifications

import org.junit.Test

class InputBridgeNotifierTest {
    @Test
    fun missingNotificationGroupDoesNotThrowFromNotifier() {
        IntelliJInputBridgeNotifier(
            project = null,
            notificationGroupLookup = { null },
        ).notify("Connection failed")
    }
}
