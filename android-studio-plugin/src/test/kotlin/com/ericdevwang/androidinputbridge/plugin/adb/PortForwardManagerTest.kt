package com.ericdevwang.androidinputbridge.plugin.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortForwardManagerTest {
    @Test
    fun ensureForwardReusesMappingForSelectedDevice() {
        val adb = FakeAdbClient(
            forwards = mutableListOf(PortForward("selected", 18080, 18080)),
        )

        val result = PortForwardManager(adb).ensureForward(AdbDevice("selected", "Pixel"))

        assertTrue(result is AdbResult.Success)
        assertEquals(emptyList<String>(), adb.actions)
    }

    @Test
    fun ensureForwardRemovesWrongMappingBeforeCreatingSelectedMapping() {
        val adb = FakeAdbClient(
            forwards = mutableListOf(PortForward("old", 18080, 18080)),
        )

        PortForwardManager(adb).ensureForward(AdbDevice("selected", "Pixel"))

        assertEquals(listOf("remove:old", "create:selected"), adb.actions)
    }

    @Test
    fun rebuildForwardRemovesAllMappingsOnDesktopPort() {
        val adb = FakeAdbClient(
            forwards = mutableListOf(
                PortForward("first", 18080, 18080),
                PortForward("other-port", 19000, 19000),
            ),
        )

        PortForwardManager(adb).rebuildForward(AdbDevice("selected", "Pixel"))

        assertEquals(listOf("remove:first", "create:selected"), adb.actions)
    }

    private class FakeAdbClient(
        private val forwards: MutableList<PortForward>,
    ) : AdbClient {
        val actions = mutableListOf<String>()

        override fun devices(): AdbResult<List<AdbDevice>> = AdbResult.Success(emptyList())

        override fun listForwards(): AdbResult<List<PortForward>> = AdbResult.Success(forwards.toList())

        override fun createForward(serial: String): AdbResult<Unit> {
            actions += "create:$serial"
            return AdbResult.Success(Unit)
        }

        override fun removeForward(serial: String): AdbResult<Unit> {
            actions += "remove:$serial"
            return AdbResult.Success(Unit)
        }
    }
}
