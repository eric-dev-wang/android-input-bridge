package com.ericdevwang.androidinputbridge.plugin.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbDeviceNameResolverTest {
    @Test
    fun resolveUsesConfigMarketingNameBeforeProductMarketNameAndModel() {
        val runner = RecordingCommandRunner(
            result("  HUAWEI P40 Pro  \n"),
            result("Xiaomi 10\n"),
        )

        val device = AdbDeviceNameResolver(runner).resolve(AdbDevice("serial", "ELS-NX9"))

        assertEquals("HUAWEI P40 Pro (serial)", device.displayName)
        assertEquals(
            listOf(
                listOf("-s", "serial", "shell", "getprop", "ro.config.marketing_name"),
            ),
            runner.arguments,
        )
    }

    @Test
    fun resolveUsesProductMarketNameWhenConfigMarketingNameIsBlank() {
        val runner = RecordingCommandRunner(
            result(" \t\n"),
            result("  Xiaomi 10  \n"),
        )

        val device = AdbDeviceNameResolver(runner).resolve(AdbDevice("serial", "M2002J9G"))

        assertEquals("Xiaomi 10 (serial)", device.displayName)
    }

    @Test
    fun resolveFallsBackToModelWhenMarketingNameQueriesFail() {
        val runner = RecordingCommandRunner(
            result(exitCode = 1),
            result(timedOut = true),
        )

        val device = AdbDeviceNameResolver(runner).resolve(AdbDevice("serial", "Pixel 8"))

        assertEquals("Pixel 8 (serial)", device.displayName)
    }

    @Test
    fun displayNameTrimsEachCandidateBeforeSelectingAndFormatting() {
        val device = AdbDevice(serial = "serial", model = "  Pixel 8  ", marketingName = " \t")

        assertEquals("Pixel 8 (serial)", device.displayName)
    }

    @Test
    fun displayNameTrimsMarketingNameBeforeFormatting() {
        val device = AdbDevice(serial = "serial", model = "Pixel 8", marketingName = "  HUAWEI P40 Pro  ")

        assertEquals("HUAWEI P40 Pro (serial)", device.displayName)
    }

    @Test
    fun displayNameFallsBackToSerialWhenModelAndMarketingNameAreBlank() {
        val device = AdbDevice(serial = "serial", model = "  ", marketingName = "\t")

        assertEquals("serial", device.displayName)
    }

    private class RecordingCommandRunner(
        vararg results: AdbCommandResult,
    ) : AdbCommandRunner {
        private val results = ArrayDeque(results.toList())
        var arguments: List<List<String>> = emptyList()

        override fun run(arguments: List<String>): AdbCommandResult {
            this.arguments += listOf(arguments)
            return if (results.size > 1) results.removeFirst() else results.first()
        }
    }

    private companion object {
        fun result(
            stdout: String = "",
            exitCode: Int? = 0,
            timedOut: Boolean = false,
        ) = AdbCommandResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = "",
            timedOut = timedOut,
        )
    }
}
