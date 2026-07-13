package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.plugin.adb.AdbClient
import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.ericdevwang.androidinputbridge.plugin.adb.AdbLocator
import com.ericdevwang.androidinputbridge.plugin.adb.AdbResult
import com.ericdevwang.androidinputbridge.plugin.adb.DeviceSelector
import com.ericdevwang.androidinputbridge.plugin.adb.PortForwardManager
import com.ericdevwang.androidinputbridge.plugin.http.BridgeProbe
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeClient
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeResult
import com.ericdevwang.androidinputbridge.plugin.http.ProbeFailureCategory
import com.ericdevwang.androidinputbridge.protocol.TextResponse
import com.intellij.openapi.Disposable
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executor

class BridgeConnectionCoordinator(
    private val adbLocator: AdbLocator,
    private val adbClientFactory: (Path) -> AdbClient,
    private val httpProbeClientFactory: () -> HttpProbeClient,
    private val deviceSelector: DeviceSelector,
    private val executor: Executor,
    private val clock: () -> Instant = { Instant.now() },
) : BridgeConnectionController {
    private val lock = Any()
    private val listeners = mutableListOf<(BridgeState) -> Unit>()

    @Volatile
    private var disposed = false

    @Volatile
    private var activeAdbClient: AdbClient? = null

    @Volatile
    private var activeProbeClient: HttpProbeClient? = null

    @Volatile
    private var selectedSerial: String? = null

    @Volatile
    private var busy = false

    override var state: BridgeState = BridgeState()
        private set

    override fun addListener(listener: (BridgeState) -> Unit) {
        val current = synchronized(lock) {
            if (disposed) return
            listeners += listener
            state
        }
        listener(current)
    }

    override fun removeListener(listener: (BridgeState) -> Unit) {
        synchronized(lock) { listeners -= listener }
    }

    override fun reconnect() {
        synchronized(lock) {
            if (disposed || busy) return
            busy = true
            publishLocked(state.copy(isBusy = true, errorMessage = null))
        }
        executor.execute { runReconnect() }
    }

    override fun refresh() {
        synchronized(lock) {
            if (disposed || busy) return
            busy = true
            publishLocked(state.copy(isBusy = true, errorMessage = null))
        }
        executor.execute { runRefresh() }
    }

    override fun selectDevice(serial: String) {
        val shouldReconnect = synchronized(lock) {
            if (disposed || busy || state.devices.none { it.serial == serial }) return
            selectedSerial = serial
            publishLocked(state.copy(selectedSerial = serial, errorMessage = null))
            true
        }
        if (shouldReconnect) reconnect()
    }

    override fun dispose() {
        synchronized(lock) {
            disposed = true
            busy = false
            activeAdbClient = null
            activeProbeClient = null
            listeners.clear()
        }
    }

    private fun runReconnect() {
        try {
            if (disposed) return
            val adbPath = adbLocator.locate()
            if (adbPath == null) {
                finish(
                    state.copy(
                        connectionState = BridgeConnectionState.ADB_UNAVAILABLE,
                        adbStatus = "Unavailable",
                        forwardStatus = "Not established",
                        serverStatus = "Offline",
                        errorMessage = "ADB executable was not found.",
                    ),
                )
                return
            }

            val adb = adbClientFactory(adbPath)
            activeAdbClient = adb
            val devices = when (val result = adb.devices()) {
                is AdbResult.Failure -> {
                    finishError("ADB device discovery failed: ${result.error.message}")
                    return
                }
                is AdbResult.Success -> result.value
            }
            if (devices.isEmpty()) {
                finish(
                    state.copy(
                        connectionState = BridgeConnectionState.NO_DEVICE,
                        devices = emptyList(),
                        selectedSerial = null,
                        adbStatus = "Available",
                        forwardStatus = "Not established",
                        serverStatus = "Offline",
                        errorMessage = "No Android device connected.",
                    ),
                )
                selectedSerial = null
                return
            }

            val selected = devices.firstOrNull { it.serial == selectedSerial }
                ?: deviceSelector.select(devices)
            selectedSerial = selected.serial
            publish(
                state.copy(
                    connectionState = BridgeConnectionState.FORWARDING,
                    devices = devices,
                    selectedSerial = selected.serial,
                    adbStatus = "Available",
                    forwardStatus = "Forwarding localhost:18080 → device:18080",
                    serverStatus = "Checking",
                    errorMessage = null,
                ),
            )

            val forwardManager = PortForwardManager(adb)
            when (val forward = forwardManager.ensureForward(selected)) {
                is AdbResult.Failure -> {
                    finishError("ADB port forwarding failed: ${forward.error.message}")
                    return
                }
                is AdbResult.Success -> Unit
            }

            val probe = httpProbeClientFactory()
            activeProbeClient = probe
            when (val result = probe.probe()) {
                is HttpProbeResult.Success -> finishConnected(result.value)
                is HttpProbeResult.Failure -> {
                    if (result.error.category != ProbeFailureCategory.CONNECTION || !result.error.retryable) {
                        finishProbeFailure(result.error)
                        return
                    }
                    when (val rebuilt = forwardManager.rebuildForward(selected)) {
                        is AdbResult.Failure -> finishError("ADB port forwarding failed: ${rebuilt.error.message}")
                        is AdbResult.Success -> when (val retry = probe.probe()) {
                            is HttpProbeResult.Success -> finishConnected(retry.value)
                            is HttpProbeResult.Failure -> finishProbeFailure(retry.error)
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            finishError(exception.message ?: "Unexpected connection error.")
        }
    }

    private fun runRefresh() {
        try {
            if (disposed) return
            val probe = activeProbeClient
            if (probe == null || selectedSerial == null) {
                finishProbeFailureMessage("The bridge is not connected.")
                return
            }
            when (val result = probe.fetchText()) {
                is HttpProbeResult.Success -> finishText(result.value)
                is HttpProbeResult.Failure -> finishProbeFailure(result.error)
            }
        } catch (exception: Exception) {
            finishError(exception.message ?: "Unexpected refresh error.")
        }
    }

    private fun finishConnected(probe: BridgeProbe) {
        finish(
            state.copy(
                connectionState = BridgeConnectionState.CONNECTED,
                serverStatus = "Online",
                text = probe.text.text,
                version = probe.text.version,
                lastRefresh = clock(),
                errorMessage = null,
            ),
        )
    }

    private fun finishText(text: TextResponse) {
        finish(
            state.copy(
                connectionState = BridgeConnectionState.CONNECTED,
                serverStatus = "Online",
                text = text.text,
                version = text.version,
                lastRefresh = clock(),
                errorMessage = null,
            ),
        )
    }

    private fun finishProbeFailure(error: com.ericdevwang.androidinputbridge.plugin.http.ProbeError) {
        if (error.category == ProbeFailureCategory.CONNECTION) {
            finish(
                state.copy(
                    connectionState = BridgeConnectionState.SERVER_OFFLINE,
                    serverStatus = "Offline",
                    errorMessage = error.message,
                ),
            )
        } else {
            finishError(error.message)
        }
    }

    private fun finishProbeFailureMessage(message: String) {
        finish(
            state.copy(
                connectionState = BridgeConnectionState.SERVER_OFFLINE,
                serverStatus = "Offline",
                errorMessage = message,
            ),
        )
    }

    private fun finishError(message: String) {
        finish(
            state.copy(
                connectionState = BridgeConnectionState.ERROR,
                errorMessage = message,
            ),
        )
    }

    private fun finish(newState: BridgeState) {
        synchronized(lock) {
            if (disposed) return
            busy = false
            publishLocked(newState.copy(isBusy = false))
        }
    }

    private fun publish(newState: BridgeState) {
        synchronized(lock) { publishLocked(newState) }
    }

    private fun publishLocked(newState: BridgeState) {
        state = newState
        val snapshot = listeners.toList()
        snapshot.forEach { it(newState) }
    }
}
