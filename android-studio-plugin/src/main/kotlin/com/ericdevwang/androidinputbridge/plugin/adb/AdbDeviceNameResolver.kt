package com.ericdevwang.androidinputbridge.plugin.adb

import com.ericdevwang.androidinputbridge.plugin.logging.BridgeLog

class AdbDeviceNameResolver(
    private val commandRunner: AdbCommandRunner,
) {
    fun resolve(device: AdbDevice): AdbDevice = device.copy(
        marketingName = MARKETING_PROPERTIES
            .asSequence()
            .mapNotNull { property -> readProperty(device.serial, property) }
            .firstOrNull(),
    )

    private fun readProperty(serial: String, property: String): String? {
        val command = listOf("-s", serial, "shell", "getprop", property)
        val result = runCatching { commandRunner.run(command) }
            .onFailure { BridgeLog.failure("ADB device name lookup", it) }
            .getOrNull()
            ?: return null

        BridgeLog.adbCommand("shell getprop", result.exitCode, result.timedOut)
        if (result.timedOut || result.exitCode != 0) return null

        return result.stdout.trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        val MARKETING_PROPERTIES = listOf(
            "ro.config.marketing_name",
            "ro.product.marketname",
        )
    }
}
