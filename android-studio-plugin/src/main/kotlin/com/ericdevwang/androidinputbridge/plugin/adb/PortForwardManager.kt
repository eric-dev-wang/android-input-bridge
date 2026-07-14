package com.ericdevwang.androidinputbridge.plugin.adb

import com.ericdevwang.androidinputbridge.plugin.connection.BridgeNetworkConfig

class PortForwardManager(
    private val adbClient: AdbClient,
) {
    fun ensureForward(device: AdbDevice): AdbResult<Unit> {
        return when (val forwards = adbClient.listForwards()) {
            is AdbResult.Failure -> forwards
            is AdbResult.Success -> {
                val desktopMappings = forwards.value.filter { it.localPort == DESKTOP_PORT }
                val matching = desktopMappings.singleOrNull {
                    it.serial == device.serial && it.remotePort == ANDROID_PORT
                }
                if (matching != null && desktopMappings.size == 1) {
                    AdbResult.Success(Unit)
                } else {
                    rebuildAfterRemoving(desktopMappings, device)
                }
            }
        }
    }

    fun rebuildForward(device: AdbDevice): AdbResult<Unit> = when (val forwards = adbClient.listForwards()) {
        is AdbResult.Failure -> forwards
        is AdbResult.Success -> rebuildAfterRemoving(
            forwards.value.filter { it.localPort == DESKTOP_PORT },
            device,
        )
    }

    private fun rebuildAfterRemoving(
        mappings: List<PortForward>,
        device: AdbDevice,
    ): AdbResult<Unit> {
        mappings.distinctBy { it.serial }.forEach { mapping ->
            when (val removed = adbClient.removeForward(mapping.serial)) {
                is AdbResult.Failure -> return removed
                is AdbResult.Success -> Unit
            }
        }
        return adbClient.createForward(device.serial)
    }

    private companion object {
        const val DESKTOP_PORT = BridgeNetworkConfig.PORT
        const val ANDROID_PORT = BridgeNetworkConfig.PORT
    }
}
