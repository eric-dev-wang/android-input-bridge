package com.ericdevwang.androidinputbridge.plugin.mock

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockBridgeStateStoreTest {
    @Test
    fun defaultStateIsConnectedWithRepresentativeText() {
        val store = MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") })

        assertEquals(MockConnectionState.CONNECTED, store.state.connectionState)
        assertEquals(17L, store.state.version)
        assertTrue(store.state.text.contains("你好"))
        assertTrue(store.state.text.contains("👋"))
        assertTrue(store.state.text.contains("\n"))
    }

    @Test
    fun refreshUpdatesTimestampWithoutChangingText() {
        var now = Instant.parse("2026-07-13T12:00:00Z")
        val store = MockBridgeStateStore(clock = { now })
        val original = store.state
        now = Instant.parse("2026-07-13T12:01:00Z")

        store.refresh()

        assertEquals(original.text, store.state.text)
        assertEquals(original.version, store.state.version)
        assertEquals(now, store.state.lastRefresh)
    }

    @Test
    fun reconnectRestoresConnectedState() {
        val store = MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") })
        store.replaceState(store.state.copy(connectionState = MockConnectionState.SERVER_OFFLINE))

        store.reconnect()

        assertEquals(MockConnectionState.CONNECTED, store.state.connectionState)
        assertEquals("Online", store.state.serverStatus)
    }

    @Test
    fun copyAndClearClearsTextAndIncrementsVersion() {
        val store = MockBridgeStateStore(clock = { Instant.parse("2026-07-13T12:00:00Z") })
        val originalVersion = store.state.version

        store.copyAndClear()

        assertEquals("", store.state.text)
        assertEquals(originalVersion + 1, store.state.version)
        assertEquals(MockFeedback.COPIED_AND_CLEARED, store.state.feedback)
    }
}
