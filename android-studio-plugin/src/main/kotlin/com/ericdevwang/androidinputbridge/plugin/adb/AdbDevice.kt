package com.ericdevwang.androidinputbridge.plugin.adb

data class AdbDevice(
    val serial: String,
    val model: String?,
    val marketingName: String? = null,
) {
    val displayName: String
        get() = (marketingName ?: model)
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it ($serial)" }
            ?: serial
}
