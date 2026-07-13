package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.plugin.adb.AdbClient
import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.ericdevwang.androidinputbridge.plugin.adb.AdbLocator
import com.ericdevwang.androidinputbridge.plugin.adb.AdbResult
import com.ericdevwang.androidinputbridge.plugin.adb.DeviceSelector
import com.ericdevwang.androidinputbridge.plugin.adb.PortForwardManager
import com.ericdevwang.androidinputbridge.plugin.clipboard.ClipboardWriteResult
import com.ericdevwang.androidinputbridge.plugin.clipboard.ClipboardWriter
import com.ericdevwang.androidinputbridge.plugin.http.BridgeProbe
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeClient
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeResult
import com.ericdevwang.androidinputbridge.plugin.http.ProbeError
import com.ericdevwang.androidinputbridge.plugin.http.ProbeFailureCategory
import com.ericdevwang.androidinputbridge.plugin.logging.BridgeLog
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
    private val clipboardWriter: ClipboardWriter,
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

    private data class StateNotification(
        val state: BridgeState,
        val listeners: List<(BridgeState) -> Unit>,
    )

    private data class TextSnapshot(
        val text: String,
        val version: Long?,
    )

    private data class OperationStart(
        val snapshot: TextSnapshot,
        val notification: StateNotification,
    )

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
        val notification = synchronized(lock) {
            if (disposed || busy) return
            busy = true
            val newState = state.copy(isBusy = true, errorMessage = null)
            StateNotification(newState, publishLocked(newState))
        }
        notifyListeners(notification)
        submit { runReconnect() }
    }

    override fun refresh() {
        val notification = synchronized(lock) {
            if (disposed || busy) return
            busy = true
            val newState = state.copy(isBusy = true, errorMessage = null)
            StateNotification(newState, publishLocked(newState))
        }
        notifyListeners(notification)
        submit { runRefresh() }
    }

    override fun selectDevice(serial: String) {
        val notification = synchronized(lock) {
            if (disposed || busy || state.devices.none { it.serial == serial }) return
            selectedSerial = serial
            val newState = state.copy(selectedSerial = serial, errorMessage = null)
            StateNotification(newState, publishLocked(newState))
        }
        notifyListeners(notification)
        reconnect()
    }

    override fun copy() {
        val operation = beginTextOperation(requireVersion = false) ?: return
        notifyListeners(operation.notification)
        submit { runCopy(operation.snapshot) }
    }

    override fun copyAndClear() {
        val operation = beginTextOperation(requireVersion = true) ?: return
        notifyListeners(operation.notification)
        submit { runCopyAndClear(operation.snapshot) }
    }

    override fun dispose() {
        val probeToClose = synchronized(lock) {
            if (disposed) return
            disposed = true
            busy = false
            activeAdbClient = null
            val probe = activeProbeClient
            activeProbeClient = null
            listeners.clear()
            probe
        }
        runCatching {
            probeToClose?.close()
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
                    forwardStatus = "Forwarding ${BridgeNetworkConfig.HOST}:${BridgeNetworkConfig.PORT} → device:${BridgeNetworkConfig.PORT}",
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

            val probe = getOrCreateProbe() ?: return
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

    private fun runCopy(snapshot: TextSnapshot) {
        if (disposed) return
        val result = runCatching { clipboardWriter.write(snapshot.text) }
            .getOrElse { ClipboardWriteResult.Failure(CLIPBOARD_FAILURE_MESSAGE, it) }
        when (result) {
            ClipboardWriteResult.Success -> finish(
                state.copy(
                    errorMessage = null,
                    feedbackMessage = COPIED_MESSAGE,
                ),
            )
            is ClipboardWriteResult.Failure -> finish(
                state.copy(
                    errorMessage = null,
                    feedbackMessage = result.message,
                ),
            )
        }
    }

    private fun runCopyAndClear(snapshot: TextSnapshot) {
        if (disposed) return
        val clipboardResult = runCatching { clipboardWriter.write(snapshot.text) }
            .getOrElse { ClipboardWriteResult.Failure(CLIPBOARD_FAILURE_MESSAGE, it) }
        when (clipboardResult) {
            is ClipboardWriteResult.Failure -> {
                finish(
                    state.copy(
                        errorMessage = null,
                        feedbackMessage = clipboardResult.message,
                    ),
                )
                return
            }
            ClipboardWriteResult.Success -> Unit
        }

        if (disposed) return
        val probe = activeProbeClient
        if (probe == null || snapshot.version == null) {
            finishClearFailure()
            return
        }

        val clearResult = runCatching { probe.clearText(snapshot.version) }
            .getOrElse {
                HttpProbeResult.Failure(
                    ProbeError(
                        category = ProbeFailureCategory.CONNECTION,
                        message = it.message ?: "Clear request failed.",
                        retryable = true,
                        cause = it,
                    ),
                )
            }
        when (clearResult) {
            is HttpProbeResult.Success -> finish(
                state.copy(
                    text = "",
                    version = clearResult.value.newVersion,
                    lastRefresh = clock(),
                    errorMessage = null,
                    feedbackMessage = COPIED_AND_CLEARED_MESSAGE,
                ),
            )
            is HttpProbeResult.Failure -> {
                if (clearResult.error.statusCode == VERSION_CONFLICT_STATUS) {
                    refreshAfterConflict(probe)
                } else {
                    finishClearFailure()
                }
            }
        }
    }

    private fun refreshAfterConflict(probe: HttpProbeClient) {
        if (disposed) return
        val refreshed = runCatching { probe.fetchText() }
            .getOrElse {
                HttpProbeResult.Failure(
                    ProbeError(
                        category = ProbeFailureCategory.CONNECTION,
                        message = it.message ?: "Refresh request failed.",
                        retryable = true,
                        cause = it,
                    ),
                )
            }
        when (refreshed) {
            is HttpProbeResult.Success -> finish(
                state.copy(
                    text = refreshed.value.text,
                    version = refreshed.value.version,
                    lastRefresh = clock(),
                    errorMessage = null,
                    feedbackMessage = VERSION_CONFLICT_MESSAGE,
                ),
            )
            is HttpProbeResult.Failure -> finishClearFailure()
        }
    }

    private fun finishClearFailure() {
        finish(
            state.copy(
                errorMessage = null,
                feedbackMessage = CLEAR_FAILURE_MESSAGE,
            ),
        )
    }

    private fun beginTextOperation(requireVersion: Boolean): OperationStart? = synchronized(lock) {
        if (disposed || busy || state.text.isEmpty()) return@synchronized null
        if (requireVersion && state.version == null) return@synchronized null
        busy = true
        val snapshot = TextSnapshot(text = state.text, version = state.version)
        val newState = state.copy(
            isBusy = true,
            errorMessage = null,
            feedbackMessage = null,
        )
        OperationStart(
            snapshot = snapshot,
            notification = StateNotification(newState, publishLocked(newState)),
        )
    }

    private fun getOrCreateProbe(): HttpProbeClient? {
        synchronized(lock) {
            if (disposed) return null
            activeProbeClient?.let { return it }
        }

        val created = httpProbeClientFactory()
        val selected = synchronized(lock) {
            if (disposed) null else activeProbeClient ?: created.also { activeProbeClient = it }
        }
        if (selected !== created) created.close()
        return selected
    }

    private fun finishConnected(probe: BridgeProbe) {
        BridgeLog.textFetched(version = probe.text.version, length = probe.text.text.length)
        finish(
            state.copy(
                connectionState = BridgeConnectionState.CONNECTED,
                serverStatus = "Online",
                text = probe.text.text,
                version = probe.text.version,
                lastRefresh = clock(),
                errorMessage = null,
                feedbackMessage = null,
            ),
        )
    }

    private fun finishText(text: TextResponse) {
        BridgeLog.textFetched(version = text.version, length = text.text.length)
        finish(
            state.copy(
                connectionState = BridgeConnectionState.CONNECTED,
                serverStatus = "Online",
                text = text.text,
                version = text.version,
                lastRefresh = clock(),
                errorMessage = null,
                feedbackMessage = null,
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
                    feedbackMessage = null,
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
                feedbackMessage = null,
            ),
        )
    }

    private fun finishError(message: String) {
        finish(
            state.copy(
                connectionState = BridgeConnectionState.ERROR,
                errorMessage = message,
                feedbackMessage = null,
            ),
        )
    }

    private fun finish(newState: BridgeState) {
        val notification = synchronized(lock) {
            if (disposed) return
            busy = false
            val finishedState = newState.copy(isBusy = false)
            StateNotification(finishedState, publishLocked(finishedState))
        }
        notifyListeners(notification)
    }

    private fun publish(newState: BridgeState) {
        val notification = synchronized(lock) {
            if (disposed) return
            StateNotification(newState, publishLocked(newState))
        }
        notifyListeners(notification)
    }

    private fun publishLocked(newState: BridgeState): List<(BridgeState) -> Unit> {
        state = newState
        return listeners.toList()
    }

    private fun notifyListeners(notification: StateNotification) {
        notification.listeners.forEach { it(notification.state) }
    }

    private fun submit(task: () -> Unit) {
        try {
            executor.execute(task)
        } catch (exception: RuntimeException) {
            finishError("Background task could not be scheduled.")
        }
    }

    private companion object {
        const val CLIPBOARD_FAILURE_MESSAGE = "Clipboard write failed."
        const val CLEAR_FAILURE_MESSAGE = "Text was copied, but the phone content could not be cleared."
        const val VERSION_CONFLICT_MESSAGE =
            "Text was copied, but the phone content changed and was not cleared."
        const val COPIED_MESSAGE = "Copied"
        const val COPIED_AND_CLEARED_MESSAGE = "Copied and cleared"
        const val VERSION_CONFLICT_STATUS = 409
    }
}
