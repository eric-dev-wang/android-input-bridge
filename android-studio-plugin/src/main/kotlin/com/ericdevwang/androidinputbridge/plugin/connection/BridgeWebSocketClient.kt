package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.protocol.BridgeError
import com.ericdevwang.androidinputbridge.protocol.BridgeMessage
import com.ericdevwang.androidinputbridge.protocol.ClearCommand
import com.ericdevwang.androidinputbridge.protocol.ClearSucceeded
import com.ericdevwang.androidinputbridge.protocol.GetSnapshotCommand
import com.ericdevwang.androidinputbridge.protocol.HelloAck
import com.ericdevwang.androidinputbridge.protocol.HelloCommand
import com.ericdevwang.androidinputbridge.protocol.ProtocolConstants
import com.ericdevwang.androidinputbridge.protocol.TextChanged
import com.ericdevwang.androidinputbridge.protocol.TextSnapshot
import com.ericdevwang.androidinputbridge.protocol.VersionConflict
import java.time.Duration
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

sealed interface BridgeWebSocketResult<out T> {
    data class Success<T>(val value: T) : BridgeWebSocketResult<T>

    data class Failure(
        val message: String,
        val code: String? = null,
        val cause: Throwable? = null,
    ) : BridgeWebSocketResult<Nothing>
}

sealed interface BridgeClearResult {
    data class Cleared(
        val clearedVersion: Long,
        val newVersion: Long,
    ) : BridgeClearResult

    data class VersionConflict(
        val currentVersion: Long,
    ) : BridgeClearResult
}

interface BridgeWebSocketEventListener {
    fun onTextChanged(message: TextChanged) = Unit

    fun onClosed(cause: Throwable?) = Unit

    fun onError(cause: Throwable) = Unit
}

interface BridgeWebSocketClient : AutoCloseable {
    fun connect(listener: BridgeWebSocketEventListener): BridgeWebSocketResult<TextSnapshot>

    fun getSnapshot(): BridgeWebSocketResult<TextSnapshot>

    fun clearText(expectedVersion: Long): BridgeWebSocketResult<BridgeClearResult>

    override fun close()
}

class JdkBridgeWebSocketClient(
    private val transport: BridgeWebSocketTransport,
    private val requestTimeout: Duration = BridgeNetworkConfig.websocketRequestTimeout,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) : BridgeWebSocketClient {
    private sealed interface IncomingEvent {
        data class Message(val message: BridgeMessage) : IncomingEvent

        data class Closed(val cause: Throwable?) : IncomingEvent

        data class Failed(val cause: Throwable) : IncomingEvent
    }

    private val incoming = LinkedBlockingQueue<IncomingEvent>()
    @Volatile
    private var session: BridgeWebSocketSession? = null

    @Volatile
    private var eventListener: BridgeWebSocketEventListener? = null

    override fun connect(listener: BridgeWebSocketEventListener): BridgeWebSocketResult<TextSnapshot> {
        closeSession()
        incoming.clear()
        eventListener = listener

        val connection = when (val result = transport.connect(object : BridgeWebSocketTransportListener {
            override fun onMessage(message: BridgeMessage) {
                if (message is TextChanged) {
                    eventListener?.onTextChanged(message)
                } else {
                    incoming.offer(IncomingEvent.Message(message))
                }
            }

            override fun onClosed(cause: Throwable?) {
                session = null
                incoming.offer(IncomingEvent.Closed(cause))
                eventListener?.onClosed(cause)
            }

            override fun onError(cause: Throwable) {
                incoming.offer(IncomingEvent.Failed(cause))
                eventListener?.onError(cause)
            }
        })) {
            is BridgeWebSocketTransportResult.Connected -> result.session
            is BridgeWebSocketTransportResult.Failure -> return BridgeWebSocketResult.Failure(
                message = result.message,
                code = "WEBSOCKET_CONNECTION_FAILED",
                cause = result.cause,
            )
        }
        session = connection

        val helloRequestId = requestIdFactory()
        if (!send(HelloCommand(ProtocolConstants.CURRENT_VERSION, helloRequestId))) {
            return failure("WebSocket hello could not be sent.", code = "HANDSHAKE_SEND_FAILED")
        }

        val hello = when (val result = awaitMessage()) {
            is BridgeWebSocketResult.Failure -> return result
            is BridgeWebSocketResult.Success -> result.value as? HelloAck
                ?: return failure("WebSocket handshake returned an unexpected message.", "INVALID_HANDSHAKE")
        }
        if (hello.requestId != helloRequestId || hello.status != "ok") {
            return failure("WebSocket handshake was rejected.", "INVALID_HANDSHAKE")
        }
        if (hello.protocolVersion != ProtocolConstants.CURRENT_VERSION) {
            return failure("Unsupported WebSocket protocol version.", "UNSUPPORTED_PROTOCOL_VERSION")
        }

        return when (val result = awaitMessage()) {
            is BridgeWebSocketResult.Failure -> result
            is BridgeWebSocketResult.Success -> {
                val snapshot = result.value as? TextSnapshot
                    ?: return failure("WebSocket handshake returned an invalid snapshot.", "INVALID_HANDSHAKE")
                if (snapshot.requestId != null) {
                    failure("Initial WebSocket snapshot must not have a request ID.", "INVALID_HANDSHAKE")
                } else {
                    BridgeWebSocketResult.Success(snapshot)
                }
            }
        }
    }

    override fun getSnapshot(): BridgeWebSocketResult<TextSnapshot> {
        val requestId = requestIdFactory()
        if (!send(GetSnapshotCommand(requestId))) {
            return failure("WebSocket snapshot request could not be sent.", "REQUEST_SEND_FAILED")
        }
        return when (val result = awaitMessage()) {
            is BridgeWebSocketResult.Failure -> result
            is BridgeWebSocketResult.Success -> when (val message = result.value) {
                is TextSnapshot -> if (message.requestId == requestId) {
                    BridgeWebSocketResult.Success(message)
                } else {
                    failure("WebSocket snapshot response request ID did not match.", "INVALID_RESPONSE")
                }
                is BridgeError -> failure(message.message, message.code)
                else -> failure("WebSocket snapshot response was unexpected.", "INVALID_RESPONSE")
            }
        }
    }

    override fun clearText(expectedVersion: Long): BridgeWebSocketResult<BridgeClearResult> {
        if (expectedVersion < 0L) {
            return failure("Expected version must be a non-negative integer.", "INVALID_EXPECTED_VERSION")
        }
        val requestId = requestIdFactory()
        if (!send(ClearCommand(expectedVersion, requestId))) {
            return failure("WebSocket clear request could not be sent.", "REQUEST_SEND_FAILED")
        }
        return when (val result = awaitMessage()) {
            is BridgeWebSocketResult.Failure -> result
            is BridgeWebSocketResult.Success -> when (val message = result.value) {
                is ClearSucceeded -> if (message.requestId == requestId) {
                    BridgeWebSocketResult.Success(
                        BridgeClearResult.Cleared(message.clearedVersion, message.newVersion),
                    )
                } else {
                    failure("WebSocket clear response request ID did not match.", "INVALID_RESPONSE")
                }
                is VersionConflict -> if (message.requestId == requestId) {
                    BridgeWebSocketResult.Success(BridgeClearResult.VersionConflict(message.currentVersion))
                } else {
                    failure("WebSocket clear response request ID did not match.", "INVALID_RESPONSE")
                }
                is BridgeError -> failure(message.message, message.code)
                else -> failure("WebSocket clear response was unexpected.", "INVALID_RESPONSE")
            }
        }
    }

    override fun close() {
        closeSession()
        eventListener = null
        transport.close()
    }

    private fun send(message: BridgeMessage): Boolean = session?.send(message) == true

    private fun closeSession() {
        session?.close()
        session = null
    }

    private fun awaitMessage(): BridgeWebSocketResult<BridgeMessage> {
        val event = try {
            incoming.poll(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            return failure("WebSocket request was interrupted.", "REQUEST_INTERRUPTED", interrupted)
        } ?: return failure("WebSocket request timed out.", "REQUEST_TIMEOUT")
        return when (event) {
            is IncomingEvent.Message -> BridgeWebSocketResult.Success(event.message)
            is IncomingEvent.Closed -> failure(
                "WebSocket connection closed.",
                "WEBSOCKET_CLOSED",
                event.cause,
            )
            is IncomingEvent.Failed -> failure(
                "WebSocket connection failed.",
                "WEBSOCKET_FAILED",
                event.cause,
            )
        }
    }

    private fun failure(
        message: String,
        code: String,
        cause: Throwable? = null,
    ): BridgeWebSocketResult.Failure =
        BridgeWebSocketResult.Failure(message = message, code = code, cause = cause)
}
