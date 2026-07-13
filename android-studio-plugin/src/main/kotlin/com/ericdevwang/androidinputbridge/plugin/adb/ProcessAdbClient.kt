package com.ericdevwang.androidinputbridge.plugin.adb

import java.nio.file.Path

data class PortForward(
    val serial: String,
    val localPort: Int,
    val remotePort: Int,
)

interface AdbClient {
    fun devices(): AdbResult<List<AdbDevice>>

    fun listForwards(): AdbResult<List<PortForward>>

    fun createForward(serial: String): AdbResult<Unit>

    fun removeForward(serial: String): AdbResult<Unit>
}

class ProcessAdbClient(
    private val adbPath: Path,
    private val commandRunner: AdbCommandRunner = ProcessAdbCommandRunner(adbPath),
) : AdbClient {
    override fun devices(): AdbResult<List<AdbDevice>> =
        runCommand(listOf("devices", "-l")) { AdbDevicesParser.parse(it.stdout) }

    override fun listForwards(): AdbResult<List<PortForward>> =
        runCommand(listOf("forward", "--list")) { parseForwards(it.stdout) }

    override fun createForward(serial: String): AdbResult<Unit> =
        runCommand(listOf("-s", serial, "forward", "tcp:18080", "tcp:18080")) { }

    override fun removeForward(serial: String): AdbResult<Unit> =
        runCommand(listOf("-s", serial, "forward", "--remove", "tcp:18080")) { }

    private fun <T> runCommand(
        command: List<String>,
        transform: (AdbCommandResult) -> T,
    ): AdbResult<T> {
        val result = commandRunner.run(command)
        if (result.timedOut || result.exitCode != 0) {
            return AdbResult.Failure(
                AdbError(
                    message = if (result.timedOut) {
                        "ADB command timed out after 5 seconds."
                    } else {
                        "ADB command failed with exit code ${result.exitCode}."
                    },
                    command = command,
                    exitCode = result.exitCode,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    timedOut = result.timedOut,
                ),
            )
        }
        return AdbResult.Success(transform(result))
    }

    private fun parseForwards(output: String): List<PortForward> = output.lineSequence()
        .mapNotNull { line ->
            val columns = line.trim().split(WHITESPACE)
            if (columns.size < 3) return@mapNotNull null
            val localPort = columns[1].removePrefix("tcp:").toIntOrNull() ?: return@mapNotNull null
            val remotePort = columns[2].removePrefix("tcp:").toIntOrNull() ?: return@mapNotNull null
            PortForward(columns[0], localPort, remotePort)
        }
        .toList()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
