package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.plugin.adb.AdbClient
import com.ericdevwang.androidinputbridge.plugin.adb.AdbLocator
import com.ericdevwang.androidinputbridge.plugin.adb.AdbResult
import com.ericdevwang.androidinputbridge.plugin.adb.DeviceSelector
import com.ericdevwang.androidinputbridge.plugin.adb.PortForwardManager
import com.ericdevwang.androidinputbridge.plugin.clipboard.ClipboardWriteResult
import com.ericdevwang.androidinputbridge.plugin.clipboard.ClipboardWriter
import com.ericdevwang.androidinputbridge.plugin.logging.BridgeLog
import com.ericdevwang.androidinputbridge.protocol.TextChanged
import com.ericdevwang.androidinputbridge.protocol.TextSnapshot
import com.intellij.openapi.Disposable
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executor

class BridgeConnectionCoordinator(
    private val adbLocator: AdbLocator,
    private val adbClientFactory: (Path) -> AdbClient,
    private val webSocketClientFactory: () -> BridgeWebSocketClient,
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
    private var activeWebSocketClient: BridgeWebSocketClient? = null

    @Volatile
    private var selectedSerial: String? = null

    @Volatile
    private var busy = false

    private data class StateNotification(
        val state: BridgeState,
        val listeners: List<(BridgeState) -> Unit>,
    )

    private data class DisplayedTextSnapshot(
        val text: String,
        val version: Long?,
    )

    private data class OperationStart(
        val snapshot: DisplayedTextSnapshot,
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
        val clientToClose = synchronized(lock) {
            if (disposed) return
            disposed = true
            busy = false
            activeAdbClient = null
            val client = activeWebSocketClient
            activeWebSocketClient = null
            listeners.clear()
            client
        }
        clientToClose?.let(::closeWebSocketAsync)
    }

    private fun runReconnect() {
        closeActiveWebSocket()
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

            var result = connectWebSocket()
            if (result is BridgeWebSocketResult.Failure && result.code == "WEBSOCKET_CONNECTION_FAILED") {
                result = when (val rebuilt = forwardManager.rebuildForward(selected)) {
                    is AdbResult.Failure -> {
                        finishError("ADB port forwarding failed: ${rebuilt.error.message}")
                        return
                    }
                    is AdbResult.Success -> connectWebSocket()
                }
            }
            when (result) {
                is BridgeWebSocketResult.Success -> finishConnected(result.value)
                is BridgeWebSocketResult.Failure -> finishConnectionFailure(result)
            }
        } catch (exception: Exception) {
            finishError(exception.message ?: "Unexpected connection error.")
        }
    }

    private fun runRefresh() {
        try {
            if (disposed) return
            val client = activeWebSocketClient
            if (client == null || selectedSerial == null) {
                finishConnectionFailure(
                    BridgeWebSocketResult.Failure("The bridge is not connected.", "NOT_CONNECTED"),
                )
                return
            }
            when (val result = client.getSnapshot()) {
                is BridgeWebSocketResult.Success -> finishSnapshot(result.value)
                is BridgeWebSocketResult.Failure -> finishConnectionFailure(result)
            }
        } catch (exception: Exception) {
            finishError(exception.message ?: "Unexpected refresh error.")
        }
    }

    private fun runCopy(snapshot: DisplayedTextSnapshot) {
        if (disposed) return
        val result = runCatching { clipboardWriter.write(snapshot.text) }
            .getOrElse { ClipboardWriteResult.Failure(CLIPBOARD_FAILURE_MESSAGE, it) }
        when (result) {
            ClipboardWriteResult.Success -> finish(
                state.copy(errorMessage = null, feedbackMessage = COPIED_MESSAGE),
            )
            is ClipboardWriteResult.Failure -> finish(
                state.copy(errorMessage = null, feedbackMessage = result.message),
            )
        }
    }

    private fun runCopyAndClear(snapshot: DisplayedTextSnapshot) {
        if (disposed) return
        val clipboardResult = runCatching { clipboardWriter.write(snapshot.text) }
            .getOrElse { ClipboardWriteResult.Failure(CLIPBOARD_FAILURE_MESSAGE, it) }
        when (clipboardResult) {
            is ClipboardWriteResult.Failure -> {
                finish(state.copy(errorMessage = null, feedbackMessage = clipboardResult.message))
                return
            }
            ClipboardWriteResult.Success -> Unit
        }

        if (disposed) return
        val client = activeWebSocketClient
        val expectedVersion = snapshot.version
        if (client == null || expectedVersion == null) {
            finishClearFailure()
            return
        }

        when (val result = runCatching { client.clearText(expectedVersion) }
            .getOrElse { BridgeWebSocketResult.Failure("Clear request failed.", cause = it) }) {
            is BridgeWebSocketResult.Success -> when (val clear = result.value) {
                is BridgeClearResult.Cleared -> finish(
                    state.copy(
                        text = "",
                        version = clear.newVersion,
                        lastRefresh = clock(),
                        errorMessage = null,
                        feedbackMessage = COPIED_AND_CLEARED_MESSAGE,
                    ),
                )
                is BridgeClearResult.VersionConflict -> refreshAfterConflict(client)
            }
            is BridgeWebSocketResult.Failure -> finishClearFailure()
        }
    }

    private fun refreshAfterConflict(client: BridgeWebSocketClient) {
        if (disposed) return
        when (val result = client.getSnapshot()) {
            is BridgeWebSocketResult.Success -> finish(
                state.copy(
                    text = result.value.text,
                    version = result.value.version,
                    lastRefresh = clock(),
                    errorMessage = null,
                    feedbackMessage = VERSION_CONFLICT_MESSAGE,
                ),
            )
            is BridgeWebSocketResult.Failure -> finishClearFailure()
        }
    }

    private fun beginTextOperation(requireVersion: Boolean): OperationStart? = synchronized(lock) {
        if (disposed || busy || state.text.isEmpty()) return@synchronized null
        if (requireVersion && state.version == null) return@synchronized null
        busy = true
        val snapshot = DisplayedTextSnapshot(text = state.text, version = state.version)
        val newState = state.copy(isBusy = true, errorMessage = null, feedbackMessage = null)
        OperationStart(snapshot, StateNotification(newState, publishLocked(newState)))
    }

    private fun connectWebSocket(): BridgeWebSocketResult<TextSnapshot> {
        val client = webSocketClientFactory()
        val installed = synchronized(lock) {
            if (disposed) false else {
                activeWebSocketClient = client
                true
            }
        }
        if (!installed) {
            client.close()
            return BridgeWebSocketResult.Failure("Project has been disposed.", "DISPOSED")
        }

        val result = client.connect(object : BridgeWebSocketEventListener {
            override fun onTextChanged(message: TextChanged) {
                submit { handleTextChanged(client, message) }
            }

            override fun onClosed(cause: Throwable?) {
                submit { handleWebSocketClosed(client, cause) }
            }

            override fun onError(cause: Throwable) {
                submit { handleWebSocketError(client, cause) }
            }
        })
        if (result is BridgeWebSocketResult.Failure) {
            detachWebSocket(client)
            closeWebSocketAsync(client)
        }
        return result
    }

    private fun handleTextChanged(client: BridgeWebSocketClient, message: TextChanged) {
        val notification = synchronized(lock) {
            if (disposed || activeWebSocketClient !== client) return
            if (state.text == message.text && state.version == message.version) return
            val newState = state.copy(
                connectionState = BridgeConnectionState.CONNECTED,
                serverStatus = "Online",
                text = message.text,
                version = message.version,
                lastRefresh = clock(),
                errorMessage = null,
                feedbackMessage = null,
            )
            StateNotification(newState, publishLocked(newState))
        }
        BridgeLog.textFetched(version = message.version, length = message.text.length)
        notifyListeners(notification)
    }

    private fun handleWebSocketClosed(client: BridgeWebSocketClient, cause: Throwable?) {
        if (!detachWebSocket(client)) return
        finish(
            state.copy(
                connectionState = BridgeConnectionState.SERVER_OFFLINE,
                serverStatus = "Offline",
                errorMessage = cause?.message ?: "WebSocket connection closed.",
                feedbackMessage = null,
            ),
        )
        closeWebSocketAsync(client)
    }

    private fun handleWebSocketError(client: BridgeWebSocketClient, cause: Throwable) {
        if (!detachWebSocket(client)) return
        BridgeLog.failure("WebSocket connection", cause)
        finish(
            state.copy(
                connectionState = BridgeConnectionState.ERROR,
                serverStatus = "Offline",
                errorMessage = "WebSocket connection failed.",
                feedbackMessage = null,
            ),
        )
        closeWebSocketAsync(client)
    }

    private fun finishConnected(snapshot: TextSnapshot) {
        BridgeLog.textFetched(version = snapshot.version, length = snapshot.text.length)
        finishSnapshot(snapshot)
    }

    private fun finishSnapshot(snapshot: TextSnapshot) {
        finish(
            state.copy(
                connectionState = BridgeConnectionState.CONNECTED,
                serverStatus = "Online",
                text = snapshot.text,
                version = snapshot.version,
                lastRefresh = clock(),
                errorMessage = null,
                feedbackMessage = null,
            ),
        )
    }

    private fun finishConnectionFailure(result: BridgeWebSocketResult.Failure) {
        if (result.code == "WEBSOCKET_CONNECTION_FAILED" || result.code == "WEBSOCKET_CLOSED") {
            finish(
                state.copy(
                    connectionState = BridgeConnectionState.SERVER_OFFLINE,
                    serverStatus = "Offline",
                    errorMessage = result.message,
                    feedbackMessage = null,
                ),
            )
        } else {
            finishError(result.message)
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

    private fun finishError(message: String) {
        finish(state.copy(connectionState = BridgeConnectionState.ERROR, errorMessage = message, feedbackMessage = null))
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

    private fun detachWebSocket(client: BridgeWebSocketClient): Boolean = synchronized(lock) {
        if (activeWebSocketClient !== client) return@synchronized false
        activeWebSocketClient = null
        true
    }

    private fun closeActiveWebSocket() {
        val client = synchronized(lock) {
            val current = activeWebSocketClient
            activeWebSocketClient = null
            current
        }
        client?.let(::closeWebSocketAsync)
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

    private fun closeWebSocketAsync(client: BridgeWebSocketClient) {
        val closeTask = Runnable {
            runCatching { client.close() }
                .onFailure { BridgeLog.failure("WebSocket client close", it) }
        }
        try {
            executor.execute(closeTask)
        } catch (exception: RuntimeException) {
            BridgeLog.failure("WebSocket client close scheduling", exception)
            Thread(closeTask, "android-input-bridge-websocket-close").apply {
                isDaemon = true
                start()
            }
        }
    }

    private companion object {
        const val CLIPBOARD_FAILURE_MESSAGE = "Clipboard write failed."
        const val CLEAR_FAILURE_MESSAGE = "Text was copied, but the phone content could not be cleared."
        const val VERSION_CONFLICT_MESSAGE =
            "Text was copied, but the phone content changed and was not cleared."
        const val COPIED_MESSAGE = "Copied"
        const val COPIED_AND_CLEARED_MESSAGE = "Copied and cleared"
    }
}
