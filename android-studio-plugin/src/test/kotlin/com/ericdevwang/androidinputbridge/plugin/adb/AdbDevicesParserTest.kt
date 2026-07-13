package com.ericdevwang.androidinputbridge.plugin.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbDevicesParserTest {
    @Test
    fun parseAcceptsOnlyReadyDevicesAndUsesModelName() {
        val output = """
            List of devices attached
            emulator-5554 device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64_arm64 transport_id:1
            unauthorized-serial unauthorized usb:1-1 transport_id:2
            offline-serial offline transport_id:3
        """.trimIndent()

        val devices = AdbDevicesParser.parse(output)

        assertEquals(1, devices.size)
        assertEquals("emulator-5554", devices.single().serial)
        assertEquals("sdk gphone64 arm64", devices.single().model)
        assertEquals("sdk gphone64 arm64 (emulator-5554)", devices.single().displayName)
    }

    @Test
    fun parseFallsBackToSerialWhenModelIsMissing() {
        val devices = AdbDevicesParser.parse(
            """
            List of devices attached
            physical-serial device usb:1-2 transport_id:4
            """.trimIndent(),
        )

        assertEquals("physical-serial", devices.single().displayName)
    }

    @Test
    fun parseReturnsEmptyForNoReadyDevices() {
        val devices = AdbDevicesParser.parse(
            """
            List of devices attached
            unauthorized-serial unauthorized usb:1-1
            offline-serial offline
            """.trimIndent(),
        )

        assertTrue(devices.isEmpty())
    }
}
