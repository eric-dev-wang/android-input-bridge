package com.ericdevwang.inputbridge.plugin.adb

import java.nio.file.Path
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

class ProcessAdbClientTest {
    @Test
    fun processRunnerReturnsAfterTimeoutEvenWhenChildKeepsStreamsOpen() {
        assumeFalse(System.getProperty("os.name").contains("win", ignoreCase = true))
        val runner = ProcessAdbCommandRunner(Path.of("/bin/sh"), timeout = Duration.ofMillis(100))
        val startedAt = System.nanoTime()

        val result = runner.run(listOf("-c", "sleep 10 & wait"))

        val elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        assertTrue(result.timedOut)
        assertTrue("ADB process exceeded its timeout: ${elapsedMillis}ms", elapsedMillis < 2_000)
    }

    @Test
    fun devicesRunsDevicesLongCommandAndParsesReadyDevices() {
        val runner = RecordingCommandRunner(
            AdbCommandResult(
                exitCode = 0,
                stdout = "serial device model:Pixel_8",
                stderr = "",
                timedOut = false,
            ),
            AdbCommandResult(
                exitCode = 0,
                stdout = "\n",
                stderr = "",
                timedOut = false,
            ),
            AdbCommandResult(
                exitCode = 0,
                stdout = "\n",
                stderr = "",
                timedOut = false,
            ),
        )
        val client = ProcessAdbClient(Path.of("/sdk/platform-tools/adb"), runner)

        val result = client.devices()

        assertEquals(listOf("devices", "-l"), runner.arguments.first())
        assertEquals(
            listOf("-s", "serial", "shell", "getprop", "ro.config.marketing_name"),
            runner.arguments[1],
        )
        assertEquals(
            listOf("-s", "serial", "shell", "getprop", "ro.product.marketname"),
            runner.arguments[2],
        )
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
    fun timedOutCommandReturnsBoundedFailure() {
        val runner = RecordingCommandRunner(
            AdbCommandResult(
                exitCode = null,
                stdout = "",
                stderr = "",
                timedOut = true,
            ),
        )
        val client = ProcessAdbClient(Path.of("/adb"), runner)

        val result = client.devices()

        assertEquals("ADB command timed out after 5 seconds.", (result as AdbResult.Failure).error.message)
        assertTrue(result.error.timedOut)
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
        assertEquals(listOf("forward", "--list"), runner.arguments.last())
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
}
