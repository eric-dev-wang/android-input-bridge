package com.ericdevwang.androidinputbridge.plugin.toolwindow

import com.ericdevwang.androidinputbridge.plugin.mock.MockBridgeStateStore
import com.ericdevwang.androidinputbridge.plugin.mock.MockFeedback
import com.ericdevwang.androidinputbridge.plugin.mock.MockConnectionState
import java.time.Instant
import javax.swing.SwingUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBridgePanelTest {
    @Test
    fun panelExposesExpectedControlsAndDefaultState() {
        val panel = InputBridgePanel(MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") }))

        assertNotNull(panel.refreshButton)
        assertNotNull(panel.copyButton)
        assertNotNull(panel.copyAndClearButton)
        assertNotNull(panel.reconnectButton)
        assertTrue(panel.textArea.text.contains("你好"))
        assertTrue(panel.copyButton.isEnabled)
    }

    @Test
    fun copyKeepsDisplayedTextAndShowsCopiedFeedback() {
        val store = MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") })
        val panel = InputBridgePanel(store)
        val originalText = panel.textArea.text
        val originalVersion = store.state.version

        runOnEdt { panel.copyButton.doClick() }

        assertEquals(originalText, panel.textArea.text)
        assertEquals(originalVersion, store.state.version)
        assertEquals(MockFeedback.COPIED, store.state.feedback)
    }

    @Test
    fun copyAndClearDisablesCopyAfterTextIsCleared() {
        val store = MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") })
        val panel = InputBridgePanel(store)

        runOnEdt { panel.copyAndClearButton.doClick() }

        assertEquals("", panel.textArea.text)
        assertFalse(panel.copyButton.isEnabled)
        assertFalse(panel.copyAndClearButton.isEnabled)
        assertEquals("Copied and cleared", panel.feedbackLabel.text)
    }

    @Test
    fun panelRendersInjectedConnectionState() {
        val store = MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") })
        val panel = InputBridgePanel(store)
        store.replaceState(store.state.copy(connectionState = MockConnectionState.SERVER_OFFLINE))

        assertEquals("Status: Server offline", panel.statusTextLabel.text)
        assertFalse(panel.copyButton.isEnabled)
        assertTrue(panel.reconnectButton.isEnabled)
    }

    @Test
    fun disposingPanelStopsFurtherRendering() {
        val store = MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") })
        val panel = InputBridgePanel(store)
        val originalText = panel.textArea.text
        panel.dispose()
        store.replaceState(store.state.copy(text = "changed"))

        assertEquals(originalText, panel.textArea.text)
    }

    private fun runOnEdt(action: () -> Unit) {
        SwingUtilities.invokeAndWait(action)
    }
}
