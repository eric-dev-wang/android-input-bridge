package com.ericdevwang.androidinputbridge.plugin.mock

import java.time.Instant

enum class MockConnectionState(val displayName: String) {
    IDLE("Idle"),
    ADB_UNAVAILABLE("ADB unavailable"),
    NO_DEVICE("No device"),
    MULTIPLE_DEVICES("Multiple devices"),
    UNAUTHORIZED("Unauthorized"),
    OFFLINE("Offline"),
    FORWARDING("Forwarding"),
    SERVER_OFFLINE("Server offline"),
    CONNECTED("Connected"),
    ERROR("Error"),
}

enum class MockFeedback(val displayText: String) {
    COPIED("Copied"),
    COPIED_AND_CLEARED("Copied and cleared"),
    NOTHING_TO_COPY("Nothing to copy"),
}

data class MockBridgeState(
    val connectionState: MockConnectionState = MockConnectionState.CONNECTED,
    val adbStatus: String = "Connected",
    val deviceSerial: String? = "mock-device",
    val forwardStatus: String = "localhost:18080 → device:18080",
    val serverStatus: String = "Online",
    val text: String = DEFAULT_TEXT,
    val version: Long = 17,
    val lastRefresh: Instant? = null,
    val feedback: MockFeedback? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false,
) {
    companion object {
        const val DEFAULT_TEXT = "你好，Android Input Bridge 👋\n\nfun main() {\n    println(\"Hello from Android Studio\")\n}"
    }
}

class MockBridgeStateStore(
    private val clock: () -> Instant = { Instant.now() },
) {
    private val listeners = mutableListOf<(MockBridgeState) -> Unit>()

    var state: MockBridgeState = MockBridgeState(lastRefresh = clock())
        private set

    fun addListener(listener: (MockBridgeState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (MockBridgeState) -> Unit) {
        listeners -= listener
    }

    fun replaceState(newState: MockBridgeState) {
        state = newState
        notifyListeners()
    }

    fun refresh() {
        if (state.connectionState != MockConnectionState.CONNECTED || state.isBusy) return
        state = state.copy(lastRefresh = clock(), feedback = null)
        notifyListeners()
    }

    fun reconnect() {
        if (state.isBusy) return
        state = state.copy(
            connectionState = MockConnectionState.CONNECTED,
            adbStatus = "Connected",
            deviceSerial = "mock-device",
            forwardStatus = "localhost:18080 → device:18080",
            serverStatus = "Online",
            lastRefresh = clock(),
            feedback = null,
            errorMessage = null,
        )
        notifyListeners()
    }

    fun copy() {
        if (!canCopy()) {
            state = state.copy(feedback = MockFeedback.NOTHING_TO_COPY)
            notifyListeners()
            return
        }
        state = state.copy(feedback = MockFeedback.COPIED)
        notifyListeners()
    }

    fun copyAndClear() {
        if (!canCopy()) {
            state = state.copy(feedback = MockFeedback.NOTHING_TO_COPY)
            notifyListeners()
            return
        }
        state = state.copy(
            text = "",
            version = state.version + 1,
            lastRefresh = clock(),
            feedback = MockFeedback.COPIED_AND_CLEARED,
        )
        notifyListeners()
    }

    fun dispose() {
        listeners.clear()
    }

    private fun canCopy(): Boolean =
        state.connectionState == MockConnectionState.CONNECTED &&
            !state.isBusy &&
            state.text.isNotEmpty()

    private fun notifyListeners() {
        listeners.toList().forEach { it(state) }
    }
}
