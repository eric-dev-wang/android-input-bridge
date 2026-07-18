package com.ericdevwang.inputbridge.plugin.adb

data class AdbDevice(
    val serial: String,
    val model: String?,
    val marketingName: String? = null,
) {
    val displayName: String
        get() = sequenceOf(marketingName, model)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .firstOrNull()
            ?.let { "$it ($serial)" }
            ?: serial
}
