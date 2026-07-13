package com.ericdevwang.androidinputbridge.plugin.adb

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

data class AdbCommandResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
)

fun interface AdbCommandRunner {
    fun run(arguments: List<String>): AdbCommandResult
}

class ProcessAdbCommandRunner(
    private val adbPath: Path,
    private val timeout: Duration = Duration.ofSeconds(5),
) : AdbCommandRunner {
    override fun run(arguments: List<String>): AdbCommandResult {
        val process = ProcessBuilder(listOf(adbPath.toString()) + arguments)
            .redirectErrorStream(false)
            .start()
        val stdout = StreamReader(process.inputStream)
        val stderr = StreamReader(process.errorStream)
        stdout.start()
        stderr.start()

        val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
        }
        stdout.join()
        stderr.join()

        return AdbCommandResult(
            exitCode = if (finished) process.exitValue() else null,
            stdout = stdout.value,
            stderr = stderr.value,
            timedOut = !finished,
        )
    }

    private class StreamReader(
        private val input: java.io.InputStream,
    ) : Thread("android-input-bridge-adb-stream") {
        @Volatile
        var value: String = ""
            private set

        init {
            isDaemon = true
        }

        override fun run() {
            value = input.use { it.readBytes().toString(StandardCharsets.UTF_8) }
        }
    }
}
