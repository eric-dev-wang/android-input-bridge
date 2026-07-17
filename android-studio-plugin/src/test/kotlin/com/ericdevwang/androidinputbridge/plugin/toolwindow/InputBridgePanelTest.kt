package com.ericdevwang.androidinputbridge.plugin.toolwindow

import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionController
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionState
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeState
import com.ericdevwang.androidinputbridge.plugin.notifications.InputBridgeNotifier
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.SwingUtilities
import javax.swing.UIManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBridgePanelTest {
    @Test
    fun panelStartsIdleAndDisablesOutOfScopeClipboardActions() = onEdt {
        val panel = InputBridgePanel(FakeController())

        assertEquals("Connecting…", panel.statusTextLabel.text)
        assertFalse(panel.copyButton.isEnabled)
        assertFalse(panel.copyAndClearButton.isEnabled)
    }

    @Test
    fun panelRendersConnectionStateAndDeviceOptions() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        controller.replace(
            BridgeState(
                connectionState = BridgeConnectionState.CONNECTED,
                devices = listOf(AdbDevice("serial", "Pixel 8")),
                selectedSerial = "serial",
                adbStatus = "Available",
                forwardStatus = "Forwarding localhost:18080 → device:18080",
                serverStatus = "Online",
                text = "你好\n👋",
                version = 17,
                lastRefresh = Instant.parse("2026-07-13T12:00:00Z"),
            ),
        )

        assertEquals("Connected", panel.statusTextLabel.text)
        assertEquals("Pixel 8 (serial)", (panel.deviceSelector.selectedItem as AdbDevice).displayName)
        assertEquals("你好\n👋", panel.textArea.text)
        val expectedTime = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse("2026-07-13T12:00:00Z"))
        assertEquals("Last synced: $expectedTime", panel.lastSyncLabel.text)
        assertTrue(panel.reconnectButton.isEnabled)
        assertTrue(panel.copyButton.isEnabled)
        assertTrue(panel.copyAndClearButton.isEnabled)
    }

    @Test
    fun panelHighlightsReconnectWhenConnectionNeedsRecovery() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)

        controller.replace(
            BridgeState(
                connectionState = BridgeConnectionState.SERVER_OFFLINE,
                errorMessage = "Server is offline.",
            ),
        )

        assertTrue(panel.reconnectButton.getClientProperty("androidInputBridge.reconnectHighlight") == true)
        assertEquals("Server is offline.", panel.feedbackLabel.text)
    }

    @Test
    fun panelUsesCurrentLookAndFeelColorsAfterThemeChanges() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        val originalButtonForeground = UIManager.get("Button.foreground")
        val originalButtonBackground = UIManager.get("Button.background")
        val originalLabelForeground = UIManager.get("Label.foreground")
        try {
            UIManager.put("Button.foreground", Color.MAGENTA)
            UIManager.put("Button.background", Color.CYAN)
            UIManager.put("Label.foreground", Color.ORANGE)

            controller.replace(connectedState(text = "text"))

            assertEquals(Color.MAGENTA, panel.reconnectButton.foreground)
            assertEquals(Color.CYAN, panel.reconnectButton.background)
            assertEquals(Color.ORANGE, panel.feedbackLabel.foreground)
        } finally {
            restoreUiManagerValue("Button.foreground", originalButtonForeground)
            restoreUiManagerValue("Button.background", originalButtonBackground)
            restoreUiManagerValue("Label.foreground", originalLabelForeground)
        }
    }

    @Test
    fun panelEnablesCopyActionsForConnectedNonEmptyText() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        controller.replace(connectedState(text = "text"))

        assertTrue(panel.copyButton.isEnabled)
        assertTrue(panel.copyAndClearButton.isEnabled)
    }

    @Test
    fun panelDisablesCopyActionsForEmptyText() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        controller.replace(connectedState(text = ""))

        assertFalse(panel.copyButton.isEnabled)
        assertFalse(panel.copyAndClearButton.isEnabled)
    }

    @Test
    fun panelShowsWaitingPlaceholderBeforeFirstSync() = onEdt {
        val panel = InputBridgePanel(FakeController())

        assertEquals("Waiting for the first sync…", panel.contentPlaceholderLabel.text)
        assertEquals("", panel.textArea.text)
    }

    @Test
    fun panelShowsEmptyPlaceholderAfterConnectedSync() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        controller.replace(connectedState(text = "").copy(lastRefresh = Instant.parse("2026-07-13T12:00:00Z")))

        assertEquals("No text available", panel.contentPlaceholderLabel.text)
        assertEquals("", panel.textArea.text)
    }

    @Test
    fun panelDisablesAllBridgeActionsWhileBusy() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        controller.replace(connectedState(text = "text", isBusy = true))

        assertFalse(panel.copyButton.isEnabled)
        assertFalse(panel.copyAndClearButton.isEnabled)
        assertFalse(panel.reconnectButton.isEnabled)
        assertFalse(panel.deviceSelector.isEnabled)
    }

    @Test
    fun actionButtonsWrapWhenToolWindowIsNarrow() = onEdt {
        val panel = InputBridgePanel(FakeController())
        val actionPanel = panel.copyButton.parent as javax.swing.JPanel

        actionPanel.setSize(200, 100)
        actionPanel.doLayout()

        val buttonRows = setOf(
            panel.copyButton.y,
            panel.copyAndClearButton.y,
            panel.reconnectButton.y,
        )
        assertTrue(buttonRows.size > 1)
    }

    @Test
    fun actionButtonsStayOnOneRowWhenToolWindowIsWide() = onEdt {
        val panel = InputBridgePanel(FakeController())
        val actionPanel = panel.copyButton.parent as javax.swing.JPanel

        actionPanel.setSize(800, 100)
        actionPanel.doLayout()

        val buttonRows = setOf(
            panel.copyButton.y,
            panel.copyAndClearButton.y,
            panel.reconnectButton.y,
        )
        assertEquals(1, buttonRows.size)
    }

    @Test
    fun panelRoutesCopyActionsToController() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        controller.replace(connectedState(text = "text"))

        panel.copyButton.doClick()
        panel.copyAndClearButton.doClick()

        assertEquals(1, controller.copyCalls)
        assertEquals(1, controller.copyAndClearCalls)
    }

    @Test
    fun panelShowsInlineFeedbackAndNotifiesOncePerMessage() = onEdt {
        val controller = FakeController()
        val notifier = RecordingNotifier()
        val panel = InputBridgePanel(controller, notifier)

        controller.replace(BridgeState(feedbackMessage = "Copied"))
        controller.replace(BridgeState(feedbackMessage = "Copied"))
        controller.replace(BridgeState(feedbackMessage = "Copied and cleared"))

        assertEquals("Copied and cleared", panel.feedbackLabel.text)
        assertEquals(listOf("Copied", "Copied and cleared"), notifier.messages)
    }

    @Test
    fun disposingPanelStopsFurtherRendering() = onEdt {
        val controller = FakeController()
        val panel = InputBridgePanel(controller)
        panel.dispose()
        controller.replace(BridgeState(text = "changed"))

        assertEquals("", panel.textArea.text)
    }

    private fun <T> onEdt(action: () -> T): T {
        var result: T? = null
        SwingUtilities.invokeAndWait { result = action() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun connectedState(text: String, isBusy: Boolean = false) = BridgeState(
        connectionState = BridgeConnectionState.CONNECTED,
        devices = listOf(AdbDevice("serial", "Pixel 8")),
        selectedSerial = "serial",
        adbStatus = "Available",
        forwardStatus = "Forwarding localhost:18080 → device:18080",
        serverStatus = "Online",
        text = text,
        version = 17,
        isBusy = isBusy,
    )

    private fun restoreUiManagerValue(key: String, value: Any?) {
        if (value == null) {
            UIManager.getDefaults().remove(key)
        } else {
            UIManager.put(key, value)
        }
    }

    private class RecordingNotifier : InputBridgeNotifier {
        val messages = mutableListOf<String>()

        override fun notify(message: String) {
            messages += message
        }
    }

    private class FakeController : BridgeConnectionController {
        override var state: BridgeState = BridgeState()
            private set
        private val listeners = mutableListOf<(BridgeState) -> Unit>()
        var copyCalls = 0
        var copyAndClearCalls = 0

        override fun addListener(listener: (BridgeState) -> Unit) {
            listeners += listener
            listener(state)
        }

        override fun removeListener(listener: (BridgeState) -> Unit) {
            listeners -= listener
        }

        override fun reconnect() = Unit

        override fun selectDevice(serial: String) = Unit

        override fun copy() {
            copyCalls++
        }

        override fun copyAndClear() {
            copyAndClearCalls++
        }

        override fun dispose() = listeners.clear()

        fun replace(newState: BridgeState) {
            state = newState
            listeners.toList().forEach { it(newState) }
        }
    }
}
