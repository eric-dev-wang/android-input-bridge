package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.protocol.BridgeMessage
import com.ericdevwang.androidinputbridge.protocol.ClearCommand
import com.ericdevwang.androidinputbridge.protocol.ClearSucceeded
import com.ericdevwang.androidinputbridge.protocol.HelloAck
import com.ericdevwang.androidinputbridge.protocol.HelloCommand
import com.ericdevwang.androidinputbridge.protocol.ProtocolConstants
import com.ericdevwang.androidinputbridge.protocol.TextChanged
import com.ericdevwang.androidinputbridge.protocol.TextSnapshot
import com.ericdevwang.androidinputbridge.protocol.VersionConflict
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeWebSocketClientTest {
    @Test
    fun connectSendsHelloAndReturnsInitialSnapshot() {
        val transport = RecordingWebSocketTransport()
        transport.onSend = { message, session ->
            if (message is HelloCommand) {
                session.emit(
                    HelloAck(
                        status = "ok",
                        appVersion = "1.0.1",
                        protocolVersion = ProtocolConstants.CURRENT_VERSION,
                        serverTime = 100L,
                        requestId = message.requestId,
                    ),
                )
                session.emit(TextSnapshot("你好\n😀", 7L, 99L))
            }
        }
        val client = JdkBridgeWebSocketClient(
            transport = transport,
            requestTimeout = Duration.ofMillis(100),
            requestIdFactory = RequestIds(),
        )

        val result = client.connect(object : BridgeWebSocketEventListener {})

        assertEquals(
            BridgeWebSocketResult.Success(TextSnapshot("你好\n😀", 7L, 99L)),
            result,
        )
        assertTrue(transport.sentMessages.first() is HelloCommand)
    }

    @Test
    fun pushedTextChangedIsDeliveredToListener() {
        val transport = RecordingWebSocketTransport()
        val listener = RecordingWebSocketEventListener()
        val client = connectedClient(transport, listener)

        transport.session.emit(TextChanged("updated", 8L, 101L))

        assertEquals(listOf(TextChanged("updated", 8L, 101L)), listener.textChanges)
        client.close()
    }

    @Test
    fun snapshotResponseMustMatchItsRequestId() {
        val transport = RecordingWebSocketTransport()
        val client = connectedClient(transport, RecordingWebSocketEventListener())
        transport.onSend = { message, session ->
            if (message is com.ericdevwang.androidinputbridge.protocol.GetSnapshotCommand) {
                session.emit(TextSnapshot("updated", 8L, 101L, requestId = message.requestId))
            }
        }

        assertEquals(
            BridgeWebSocketResult.Success(TextSnapshot("updated", 8L, 101L, requestId = "request-1")),
            client.getSnapshot(),
        )
    }

    @Test
    fun mismatchedSnapshotResponseFailsWithInvalidResponse() {
        val transport = RecordingWebSocketTransport()
        val client = connectedClient(transport, RecordingWebSocketEventListener())
        transport.onSend = { message, session ->
            if (message is com.ericdevwang.androidinputbridge.protocol.GetSnapshotCommand) {
                session.emit(TextSnapshot("updated", 8L, 101L, requestId = "wrong-request"))
            }
        }

        assertEquals("INVALID_RESPONSE", (client.getSnapshot() as BridgeWebSocketResult.Failure).code)
    }

    @Test
    fun transportLifecycleEventsAreForwardedToListener() {
        val transport = RecordingWebSocketTransport()
        val listener = RecordingWebSocketEventListener()
        val client = connectedClient(transport, listener)
        val closeCause = IllegalStateException("closed")
        val errorCause = IllegalStateException("failed")

        transport.session.emitClosed(closeCause)
        transport.session.emitError(errorCause)

        assertEquals(listOf(closeCause), listener.closedCauses)
        assertEquals(listOf(errorCause), listener.errors)
        client.close()
    }

    @Test
    fun closeClosesSessionAndTransport() {
        val transport = RecordingWebSocketTransport()
        val client = connectedClient(transport, RecordingWebSocketEventListener())

        client.close()

        assertEquals(1, transport.session.closeCalls)
        assertEquals(1, transport.closeCalls)
    }

    @Test
    fun clearReturnsVersionConflictWithoutTreatingItAsTransportFailure() {
        val transport = RecordingWebSocketTransport()
        transport.onSend = { message, session ->
            if (message is ClearCommand) {
                session.emit(VersionConflict(currentVersion = 8L, requestId = message.requestId))
            }
        }
        val client = connectedClient(transport, RecordingWebSocketEventListener())

        val result = client.clearText(expectedVersion = 7L)

        assertEquals(
            BridgeWebSocketResult.Success(BridgeClearResult.VersionConflict(currentVersion = 8L)),
            result,
        )
    }

    @Test
    fun clearSuccessPreservesResponseVersions() {
        val transport = RecordingWebSocketTransport()
        transport.onSend = { message, session ->
            if (message is ClearCommand) {
                session.emit(
                    ClearSucceeded(
                        clearedVersion = message.expectedVersion,
                        newVersion = message.expectedVersion + 1L,
                        requestId = message.requestId,
                    ),
                )
            }
        }
        val client = connectedClient(transport, RecordingWebSocketEventListener())

        assertEquals(
            BridgeWebSocketResult.Success(
                BridgeClearResult.Cleared(clearedVersion = 7L, newVersion = 8L),
            ),
            client.clearText(expectedVersion = 7L),
        )
    }

    @Test
    fun connectRejectsUnsupportedProtocolVersion() {
        val transport = RecordingWebSocketTransport()
        transport.onSend = { message, session ->
            if (message is HelloCommand) {
                session.emit(
                    HelloAck(
                        status = "ok",
                        appVersion = "1.0.1",
                        protocolVersion = ProtocolConstants.CURRENT_VERSION + 1,
                        serverTime = 100L,
                        requestId = message.requestId,
                    ),
                )
            }
        }
        val result = JdkBridgeWebSocketClient(
            transport = transport,
            requestTimeout = Duration.ofMillis(100),
            requestIdFactory = RequestIds(),
        ).connect(object : BridgeWebSocketEventListener {})

        assertEquals("UNSUPPORTED_PROTOCOL_VERSION", (result as BridgeWebSocketResult.Failure).code)
    }

    @Test
    fun requestTimeoutReturnsBoundedFailure() {
        val transport = RecordingWebSocketTransport()
        val result = JdkBridgeWebSocketClient(
            transport = transport,
            requestTimeout = Duration.ofMillis(20),
            requestIdFactory = RequestIds(),
        ).connect(object : BridgeWebSocketEventListener {})

        assertEquals("REQUEST_TIMEOUT", (result as BridgeWebSocketResult.Failure).code)
    }

    @Test
    fun clearRejectsNegativeExpectedVersionBeforeSending() {
        val transport = RecordingWebSocketTransport()
        val client = JdkBridgeWebSocketClient(transport = transport)

        val result = client.clearText(expectedVersion = -1L)

        assertEquals("INVALID_EXPECTED_VERSION", (result as BridgeWebSocketResult.Failure).code)
        assertTrue(transport.sentMessages.isEmpty())
    }

    private fun connectedClient(
        transport: RecordingWebSocketTransport,
        listener: RecordingWebSocketEventListener,
    ): JdkBridgeWebSocketClient {
        val existingOnSend = transport.onSend
        transport.onSend = { message, session ->
            if (message is HelloCommand) {
                session.emit(
                    HelloAck(
                        status = "ok",
                        appVersion = "1.0.1",
                        protocolVersion = ProtocolConstants.CURRENT_VERSION,
                        serverTime = 100L,
                        requestId = message.requestId,
                    ),
                )
                session.emit(TextSnapshot("initial", 7L, 99L))
            } else {
                existingOnSend?.invoke(message, session)
            }
        }
        val client = JdkBridgeWebSocketClient(
            transport = transport,
            requestTimeout = Duration.ofMillis(100),
            requestIdFactory = RequestIds(),
        )
        assertEquals(
            BridgeWebSocketResult.Success(TextSnapshot("initial", 7L, 99L)),
            client.connect(listener),
        )
        return client
    }
}

private class RequestIds : () -> String {
    private var next = 0

    override fun invoke(): String = "request-${next++}"
}

private class RecordingWebSocketEventListener : BridgeWebSocketEventListener {
    val textChanges = mutableListOf<TextChanged>()
    val closedCauses = mutableListOf<Throwable?>()
    val errors = mutableListOf<Throwable>()

    override fun onTextChanged(message: TextChanged) {
        textChanges += message
    }

    override fun onClosed(cause: Throwable?) {
        closedCauses += cause
    }

    override fun onError(cause: Throwable) {
        errors += cause
    }
}

private class RecordingWebSocketTransport : BridgeWebSocketTransport {
    val session = RecordingWebSocketSession()
    var onSend: ((BridgeMessage, RecordingWebSocketSession) -> Unit)? = null
    var closeCalls = 0

    val sentMessages: List<BridgeMessage>
        get() = session.sentMessages

    override fun connect(listener: BridgeWebSocketTransportListener): BridgeWebSocketTransportResult {
        session.listener = listener
        session.onSend = { message, currentSession -> onSend?.invoke(message, currentSession) }
        return BridgeWebSocketTransportResult.Connected(session)
    }

    override fun close() {
        closeCalls++
    }
}

private class RecordingWebSocketSession : BridgeWebSocketSession {
    var listener: BridgeWebSocketTransportListener? = null
    val sentMessages = mutableListOf<BridgeMessage>()
    var closeCalls = 0
    var onSend: ((BridgeMessage, RecordingWebSocketSession) -> Unit)? = null

    override fun send(message: BridgeMessage): Boolean {
        sentMessages += message
        onSend?.invoke(message, this)
        return true
    }

    fun emit(message: BridgeMessage) {
        listener?.onMessage(message)
    }

    fun emitClosed(cause: Throwable?) {
        listener?.onClosed(cause)
    }

    fun emitError(cause: Throwable) {
        listener?.onError(cause)
    }

    override fun close() {
        closeCalls++
    }
}
