package com.ericdevwang.androidinputbridge.plugin.adb

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSelectorTest {
    @Test
    fun randomSelectorUsesInjectedRandomSource() {
        val devices = listOf(
            AdbDevice(serial = "first", model = "First"),
            AdbDevice(serial = "second", model = "Second"),
        )

        val selector = RandomDeviceSelector(
            object : Random() {
                override fun nextBits(bitCount: Int): Int = 0
            },
        )

        assertEquals("first", selector.select(devices).serial)
    }
}
