package com.ericdevwang.androidinputbridge.plugin.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockConnectionStateTest {
    @Test
    fun allSupportedStatesHaveTheSpecifiedDisplayName() {
        val expected = mapOf(
            MockConnectionState.IDLE to "Idle",
            MockConnectionState.ADB_UNAVAILABLE to "ADB unavailable",
            MockConnectionState.NO_DEVICE to "No device",
            MockConnectionState.MULTIPLE_DEVICES to "Multiple devices",
            MockConnectionState.UNAUTHORIZED to "Unauthorized",
            MockConnectionState.OFFLINE to "Offline",
            MockConnectionState.FORWARDING to "Forwarding",
            MockConnectionState.SERVER_OFFLINE to "Server offline",
            MockConnectionState.CONNECTED to "Connected",
            MockConnectionState.ERROR to "Error",
        )

        assertEquals(expected, expected.keys.associateWith { it.displayName })
        assertTrue(expected.values.all(String::isNotEmpty))
    }
}
