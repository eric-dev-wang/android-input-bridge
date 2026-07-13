package com.ericdevwang.androidinputbridge.plugin.toolwindow

import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionController
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionState
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.SwingUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBridgePanelTest {
    @Test
    fun panelStartsIdleAndDisablesOutOfScopeClipboardActions() = onEdt {
        val panel = InputBridgePanel(FakeController())

        assertEquals("Status: Idle", panel.statusTextLabel.text)
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

        assertEquals("Status: Connected", panel.statusTextLabel.text)
        assertEquals("Pixel 8 (serial)", (panel.deviceSelector.selectedItem as AdbDevice).displayName)
        assertEquals("你好\n👋", panel.textArea.text)
        val expectedTime = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse("2026-07-13T12:00:00Z"))
        assertEquals("Last refresh: $expectedTime", panel.lastRefreshLabel.text)
        assertTrue(panel.refreshButton.isEnabled)
        assertTrue(panel.reconnectButton.isEnabled)
        assertFalse(panel.copyButton.isEnabled)
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

    private class FakeController : BridgeConnectionController {
        override var state: BridgeState = BridgeState()
            private set
        private val listeners = mutableListOf<(BridgeState) -> Unit>()

        override fun addListener(listener: (BridgeState) -> Unit) {
            listeners += listener
            listener(state)
        }

        override fun removeListener(listener: (BridgeState) -> Unit) {
            listeners -= listener
        }

        override fun reconnect() = Unit

        override fun refresh() = Unit

        override fun selectDevice(serial: String) = Unit

        override fun dispose() = listeners.clear()

        fun replace(newState: BridgeState) {
            state = newState
            listeners.toList().forEach { it(newState) }
        }
    }
}
