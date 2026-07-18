package com.ericdevwang.inputbridge.server

import com.ericdevwang.inputbridge.model.TextState
import com.ericdevwang.inputbridge.protocol.BridgeError
import com.ericdevwang.inputbridge.protocol.BridgeMessage
import com.ericdevwang.inputbridge.protocol.ClearCommand
import com.ericdevwang.inputbridge.protocol.ClearSucceeded
import com.ericdevwang.inputbridge.protocol.GetSnapshotCommand
import com.ericdevwang.inputbridge.protocol.HelloAck
import com.ericdevwang.inputbridge.protocol.HelloCommand
import com.ericdevwang.inputbridge.protocol.ProtocolConstants
import com.ericdevwang.inputbridge.protocol.ProtocolJson
import com.ericdevwang.inputbridge.protocol.TextChanged
import com.ericdevwang.inputbridge.protocol.TextSnapshot
import com.ericdevwang.inputbridge.protocol.VersionConflict
import com.ericdevwang.inputbridge.repository.ClearResult
import com.ericdevwang.inputbridge.repository.PersistenceResult
import com.ericdevwang.inputbridge.repository.TextRepository
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.BindException
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputWebSocketServerTest {
    @Test(expected = BindException::class)
    fun occupiedPortFailsWithoutSelectingFallbackPort() {
        ServerSocket(0).use { occupiedSocket ->
            InputWebSocketServer(
                repository = WebSocketFakeTextRepository(TextState.initial(0L)),
                appVersion = "1.0.1",
                config = InputWebSocketServerConfig(port = occupiedSocket.localPort),
            ).start()
        }
    }

    @Test
    fun serverCanStartStopAndReleaseItsPort() {
        val port = ServerSocket(0).use { it.localPort }
        val server = InputWebSocketServer(
            repository = WebSocketFakeTextRepository(TextState.initial(0L)),
            appVersion = "1.0.1",
            config = InputWebSocketServerConfig(port = port),
        )

        server.start()
        server.start()
        server.stop()

        ServerSocket(port).use { }
    }

    @Test
    fun helloReturnsMetadataAndInitialSnapshot() = testApplication {
        val repository = WebSocketFakeTextRepository(TextState("中文\n😀", 7L, 123L))
        application {
            module(repository, appVersion = "1.0.1", clock = { 456L })
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))

            assertEquals(
                HelloAck(
                    status = "ok",
                    appVersion = "1.0.1",
                    protocolVersion = ProtocolConstants.CURRENT_VERSION,
                    serverTime = 456L,
                    requestId = "hello-1",
                ),
                incoming.receiveMessage(),
            )
            assertEquals(
                TextSnapshot("中文\n😀", 7L, 123L),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun initialSnapshotTimeoutReturnsError() = testApplication {
        application {
            module(
                repository = NeverEmittingTextRepository(),
                appVersion = "1.0.1",
                clock = { 0L },
                initialSnapshotTimeoutMillis = 50L,
            )
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            withTimeout(1_000L) {
                sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
                assertEquals(
                    HelloAck(
                        status = "ok",
                        appVersion = "1.0.1",
                        protocolVersion = ProtocolConstants.CURRENT_VERSION,
                        serverTime = 0L,
                        requestId = "hello-1",
                    ),
                    incoming.receiveMessage(),
                )
                assertEquals(
                    BridgeError(
                        code = "INITIAL_SNAPSHOT_TIMEOUT",
                        message = "Current text snapshot was not available in time.",
                    ),
                    incoming.receiveMessage(),
                )
            }
        }
    }

    @Test
    fun repositoryChangesArePushedAsTextChanged() = testApplication {
        val repository = WebSocketFakeTextRepository(TextState("before", 1L, 1L))
        application { module(repository, appVersion = "1.0.1") }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()

            repository.emit(TextState("after", 2L, 2L))

            assertEquals(
                TextChanged("after", 2L, 2L),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun snapshotAndClearCommandsKeepRequestIds() = testApplication {
        val repository = WebSocketFakeTextRepository(
            initialState = TextState("text", 3L, 10L),
            clearResult = ClearResult.Cleared(clearedVersion = 3L, newVersion = 4L),
        )
        application { module(repository, appVersion = "1.0.1") }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()

            sendMessage(GetSnapshotCommand("snapshot-1"))
            assertEquals(
                TextSnapshot("text", 3L, 10L, requestId = "snapshot-1"),
                incoming.receiveMessage(),
            )

            sendMessage(ClearCommand(expectedVersion = 3L, requestId = "clear-1"))
            assertEquals(
                ClearSucceeded(clearedVersion = 3L, newVersion = 4L, requestId = "clear-1"),
                incoming.receiveMessage(),
            )
            assertEquals(
                TextChanged("", 4L, 11L),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun staleClearReturnsVersionConflict() = testApplication {
        val repository = WebSocketFakeTextRepository(
            initialState = TextState("new", 4L, 10L),
            clearResult = ClearResult.VersionConflict(currentVersion = 4L),
        )
        application { module(repository, appVersion = "1.0.1") }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()

            sendMessage(ClearCommand(expectedVersion = 3L, requestId = "clear-1"))

            assertEquals(
                VersionConflict(currentVersion = 4L, requestId = "clear-1"),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun negativeClearVersionReturnsErrorWithoutCallingRepository() = testApplication {
        val repository = WebSocketFakeTextRepository(TextState("text", 3L, 10L))
        application { module(repository, appVersion = "1.0.1") }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()

            sendMessage(ClearCommand(expectedVersion = -1L, requestId = "clear-1"))

            assertEquals(
                BridgeError(
                    code = "INVALID_EXPECTED_VERSION",
                    message = "Expected version must be a non-negative integer.",
                    requestId = "clear-1",
                ),
                incoming.receiveMessage(),
            )
            assertEquals(0, repository.clearCalls)

            sendMessage(GetSnapshotCommand("snapshot-1"))
            assertEquals(
                TextSnapshot("text", 3L, 10L, requestId = "snapshot-1"),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun unsupportedProtocolVersionReturnsError() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION + 1, "hello-1"))

            assertEquals(
                BridgeError(
                    code = "UNSUPPORTED_PROTOCOL_VERSION",
                    message = "Unsupported protocol version.",
                    requestId = "hello-1",
                ),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun firstMessageMustBeHello() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(GetSnapshotCommand("snapshot-before-hello"))

            assertEquals(
                BridgeError(
                    code = "INVALID_HANDSHAKE",
                    message = "The first WebSocket message must be hello.",
                    requestId = "snapshot-before-hello",
                ),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun malformedMessageReturnsError() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            send(Frame.Text("not-json"))

            assertEquals(
                BridgeError(
                    code = "MALFORMED_MESSAGE",
                    message = "WebSocket message is not valid protocol JSON.",
                ),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun binaryMessageReturnsMalformedError() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            send(Frame.Binary(fin = true, data = byteArrayOf(1, 2, 3)))

            assertEquals(
                BridgeError(
                    code = "MALFORMED_MESSAGE",
                    message = "WebSocket message is not a supported text frame.",
                ),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun malformedMessageAfterHandshakeReturnsError() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()
            send(Frame.Text("not-json"))

            assertEquals(
                BridgeError(
                    code = "MALFORMED_MESSAGE",
                    message = "WebSocket message is not valid protocol JSON.",
                ),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun unexpectedMessageAfterHandshakeReturnsError() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-2"))

            assertEquals(
                BridgeError(
                    code = "UNEXPECTED_MESSAGE",
                    message = "This message is not valid after the handshake.",
                    requestId = "hello-2",
                ),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun oversizedTextFrameIsRejected() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()
            send(Frame.Text("x".repeat(ProtocolConstants.MAX_WEBSOCKET_MESSAGE_BYTES + 1)))

            assertTrue(incoming.receiveCatching().isClosed)
        }
    }

    @Test
    fun clearFailureReturnsErrorAndKeepsSessionUsable() = testApplication {
        val repository = WebSocketFakeTextRepository(
            initialState = TextState("text", 3L, 10L),
            clearFailure = IllegalStateException("persistence failed"),
        )
        application { module(repository, appVersion = "1.0.1") }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()
            sendMessage(ClearCommand(expectedVersion = 3L, requestId = "clear-1"))

            assertEquals(
                BridgeError(
                    code = "TEXT_CLEAR_FAILED",
                    message = "Current text could not be cleared.",
                    requestId = "clear-1",
                ),
                incoming.receiveMessage(),
            )

            sendMessage(GetSnapshotCommand("snapshot-1"))
            assertEquals(
                TextSnapshot("text", 3L, 10L, requestId = "snapshot-1"),
                incoming.receiveMessage(),
            )
        }
    }

    @Test
    fun multipleSessionsReceiveTheSameTextChange() = testApplication {
        val repository = WebSocketFakeTextRepository(TextState("before", 1L, 1L))
        application { module(repository, appVersion = "1.0.1") }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-1"))
            incoming.receiveMessage()
            incoming.receiveMessage()
            val firstIncoming = incoming

            client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
                sendMessage(HelloCommand(ProtocolConstants.CURRENT_VERSION, "hello-2"))
                incoming.receiveMessage()
                incoming.receiveMessage()

                repository.emit(TextState("after", 2L, 2L))

                assertEquals(TextChanged("after", 2L, 2L), incoming.receiveMessage())
                assertEquals(TextChanged("after", 2L, 2L), firstIncoming.receiveMessage())
            }
        }
    }

    @Test
    fun unknownMessageTypeReturnsError() = testApplication {
        application {
            module(WebSocketFakeTextRepository(TextState.initial(0L)), appVersion = "1.0.1")
        }

        val client = createClient { install(WebSockets) }
        client.webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            send(Frame.Text("{\"type\":\"unknown\"}"))

            assertEquals(
                BridgeError(
                    code = "MALFORMED_MESSAGE",
                    message = "WebSocket message is not valid protocol JSON.",
                ),
                incoming.receiveMessage(),
            )
        }
    }
}

private suspend fun ReceiveChannel<Frame>.receiveMessage(): BridgeMessage {
    val frame = receive() as Frame.Text
    return ProtocolJson.default.decodeFromString(frame.data.decodeToString())
}

private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.sendMessage(
    message: BridgeMessage,
) {
    send(Frame.Text(ProtocolJson.default.encodeToString<BridgeMessage>(message)))
}

private class WebSocketFakeTextRepository(
    initialState: TextState,
    private val clearResult: ClearResult = ClearResult.Cleared(
        clearedVersion = initialState.version,
        newVersion = initialState.version + 1L,
    ),
    private val clearFailure: Throwable? = null,
) : TextRepository {
    private val mutableState = MutableStateFlow(initialState)

    var clearCalls: Int = 0
        private set

    override val state: Flow<TextState> = mutableState

    override suspend fun save(state: TextState): PersistenceResult =
        PersistenceResult.Succeeded(state.version)

    override suspend fun clear(expectedVersion: Long): ClearResult {
        clearCalls += 1
        clearFailure?.let { throw it }
        if (clearResult is ClearResult.Cleared) {
            mutableState.value = mutableState.value.copy(
                text = "",
                version = clearResult.newVersion,
                updatedAt = mutableState.value.updatedAt + 1L,
            )
        }
        return clearResult
    }

    fun emit(state: TextState) {
        mutableState.value = state
    }
}

private class NeverEmittingTextRepository : TextRepository {
    override val state: Flow<TextState> = flow {
        awaitCancellation()
    }

    override suspend fun save(state: TextState): PersistenceResult =
        PersistenceResult.Succeeded(state.version)

    override suspend fun clear(expectedVersion: Long): ClearResult =
        ClearResult.VersionConflict(currentVersion = 0L)
}
