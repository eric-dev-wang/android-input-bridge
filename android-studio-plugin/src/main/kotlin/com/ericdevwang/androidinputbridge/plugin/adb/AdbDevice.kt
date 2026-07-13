package com.ericdevwang.androidinputbridge.plugin.adb

data class AdbDevice(
    val serial: String,
    val model: String?,
) {
    val displayName: String
        get() = model?.takeIf { it.isNotBlank() }?.let { "$it ($serial)" } ?: serial
}
