package com.ericdevwang.androidinputbridge.plugin.adb

sealed interface AdbResult<out T> {
    data class Success<T>(val value: T) : AdbResult<T>

    data class Failure(val error: AdbError) : AdbResult<Nothing>
}

data class AdbError(
    val message: String,
    val command: List<String>,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
)
