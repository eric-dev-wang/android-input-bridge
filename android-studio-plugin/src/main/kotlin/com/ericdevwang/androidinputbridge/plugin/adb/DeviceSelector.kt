package com.ericdevwang.androidinputbridge.plugin.adb

import kotlin.random.Random

fun interface DeviceSelector {
    fun select(devices: List<AdbDevice>): AdbDevice
}

class RandomDeviceSelector(
    private val random: Random = Random.Default,
) : DeviceSelector {
    override fun select(devices: List<AdbDevice>): AdbDevice =
        devices[random.nextInt(devices.size)]
}
