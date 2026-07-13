package com.ericdevwang.androidinputbridge.plugin.adb

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessAdbClientTest {
    @Test
    fun devicesRunsDevicesLongCommandAndParsesReadyDevices() {
        val runner = RecordingCommandRunner(
            AdbCommandResult(
                exitCode = 0,
                stdout = "serial device model:Pixel_8",
                stderr = "",
                timedOut = false,
            ),
        )
        val client = ProcessAdbClient(Path.of("/sdk/platform-tools/adb"), runner)

        val result = client.devices()

        assertEquals(listOf("devices", "-l"), runner.arguments)
        assertEquals(listOf(AdbDevice("serial", "Pixel 8")), (result as AdbResult.Success).value)
    }

    @Test
    fun nonZeroCommandReturnsFailureWithCommandDetails() {
        val runner = RecordingCommandRunner(
            AdbCommandResult(
                exitCode = 1,
                stdout = "",
                stderr = "daemon unavailable",
                timedOut = false,
            ),
        )
        val client = ProcessAdbClient(Path.of("/adb"), runner)

        val result = client.devices()

        assertTrue(result is AdbResult.Failure)
        assertEquals("daemon unavailable", (result as AdbResult.Failure).error.stderr)
    }

    @Test
    fun listForwardsParsesSerialAndPorts() {
        val runner = RecordingCommandRunner(
            AdbCommandResult(
                exitCode = 0,
                stdout = "serial tcp:18080 tcp:18080\nother tcp:19000 tcp:19000",
                stderr = "",
                timedOut = false,
            ),
        )
        val client = ProcessAdbClient(Path.of("/adb"), runner)

        val result = client.listForwards()

        assertEquals(
            listOf(
                PortForward("serial", 18080, 18080),
                PortForward("other", 19000, 19000),
            ),
            (result as AdbResult.Success).value,
        )
        assertEquals(listOf("forward", "--list"), runner.arguments)
    }

    private class RecordingCommandRunner(
        private val result: AdbCommandResult,
    ) : AdbCommandRunner {
        var arguments: List<String> = emptyList()

        override fun run(arguments: List<String>): AdbCommandResult {
            this.arguments = arguments
            return result
        }
    }
}
