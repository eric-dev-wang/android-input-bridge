package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.intellij.openapi.Disposable
import java.time.Instant

enum class BridgeConnectionState(val displayName: String) {
    IDLE("Idle"),
    ADB_UNAVAILABLE("ADB unavailable"),
    NO_DEVICE("No device"),
    FORWARDING("Forwarding"),
    SERVER_OFFLINE("Server offline"),
    CONNECTED("Connected"),
    ERROR("Error"),
}

data class BridgeState(
    val connectionState: BridgeConnectionState = BridgeConnectionState.IDLE,
    val devices: List<AdbDevice> = emptyList(),
    val selectedSerial: String? = null,
    val adbStatus: String = "Not checked",
    val forwardStatus: String = "Not established",
    val serverStatus: String = "Offline",
    val text: String = "",
    val version: Long? = null,
    val lastRefresh: Instant? = null,
    val errorMessage: String? = null,
    val feedbackMessage: String? = null,
    val isBusy: Boolean = false,
)

interface BridgeConnectionController : Disposable {
    val state: BridgeState

    fun addListener(listener: (BridgeState) -> Unit)

    fun removeListener(listener: (BridgeState) -> Unit)

    fun reconnect()

    fun selectDevice(serial: String)

    fun copy()

    fun copyAndClear()
}
