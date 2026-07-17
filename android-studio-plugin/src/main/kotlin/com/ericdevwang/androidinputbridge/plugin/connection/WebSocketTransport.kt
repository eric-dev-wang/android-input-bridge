package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.protocol.BridgeMessage

sealed interface BridgeWebSocketTransportResult {
    data class Connected(val session: BridgeWebSocketSession) : BridgeWebSocketTransportResult

    data class Failure(
        val message: String,
        val retryable: Boolean = true,
        val cause: Throwable? = null,
    ) : BridgeWebSocketTransportResult
}

interface BridgeWebSocketTransportListener {
    fun onMessage(message: BridgeMessage)

    fun onClosed(cause: Throwable?)

    fun onError(cause: Throwable)
}

interface BridgeWebSocketSession {
    fun send(message: BridgeMessage): Boolean

    fun close()
}

interface BridgeWebSocketTransport : AutoCloseable {
    fun connect(listener: BridgeWebSocketTransportListener): BridgeWebSocketTransportResult

    override fun close() = Unit
}
