package com.ericdevwang.androidinputbridge.server

import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.protocol.BridgeError
import com.ericdevwang.androidinputbridge.protocol.BridgeMessage
import com.ericdevwang.androidinputbridge.protocol.ClearCommand
import com.ericdevwang.androidinputbridge.protocol.ClearSucceeded
import com.ericdevwang.androidinputbridge.protocol.GetSnapshotCommand
import com.ericdevwang.androidinputbridge.protocol.HelloAck
import com.ericdevwang.androidinputbridge.protocol.HelloCommand
import com.ericdevwang.androidinputbridge.protocol.ProtocolConstants
import com.ericdevwang.androidinputbridge.protocol.ProtocolJson
import com.ericdevwang.androidinputbridge.protocol.TextChanged
import com.ericdevwang.androidinputbridge.protocol.TextSnapshot
import com.ericdevwang.androidinputbridge.protocol.VersionConflict
import com.ericdevwang.androidinputbridge.repository.ClearResult
import com.ericdevwang.androidinputbridge.repository.TextRepository
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_SERVER_HOST = ProtocolConstants.LOCALHOST
const val DEFAULT_SERVER_PORT = ProtocolConstants.SERVER_PORT
private const val INITIAL_SNAPSHOT_TIMEOUT_MILLIS = 2_000L

data class InputWebSocketServerConfig(
    val host: String = DEFAULT_SERVER_HOST,
    val port: Int = DEFAULT_SERVER_PORT,
)

class InputWebSocketServer(
    private val repository: TextRepository,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: InputWebSocketServerConfig = InputWebSocketServerConfig(),
) {
    private var engine: EmbeddedServer<*, *>? = null

    @Synchronized
    fun start() {
        if (engine != null) return

        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(config.host, config.port))
        }

        engine = embeddedServer(
            factory = CIO,
            host = config.host,
            port = config.port,
            module = { module(repository, appVersion, clock) },
        ).start(wait = false)
    }

    @Synchronized
    fun stop() {
        val currentEngine = engine ?: return
        engine = null
        currentEngine.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
    }
}

fun Application.module(
    repository: TextRepository,
    appVersion: String,
    clock: () -> Long = System::currentTimeMillis,
    initialSnapshotTimeoutMillis: Long = INITIAL_SNAPSHOT_TIMEOUT_MILLIS,
) {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = ProtocolConstants.MAX_WEBSOCKET_MESSAGE_BYTES.toLong()
    }
    routing {
        webSocket(ProtocolConstants.WEBSOCKET_PATH) {
            handleBridgeSession(repository, appVersion, clock, initialSnapshotTimeoutMillis)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleBridgeSession(
    repository: TextRepository,
    appVersion: String,
    clock: () -> Long,
    initialSnapshotTimeoutMillis: Long,
) {
    val sendMutex = Mutex()

    suspend fun sendMessageUnlocked(message: BridgeMessage) {
        send(Frame.Text(ProtocolJson.default.encodeToString<BridgeMessage>(message)))
    }

    suspend fun sendMessage(message: BridgeMessage) {
        sendMutex.withLock { sendMessageUnlocked(message) }
    }

    suspend fun reject(
        code: String,
        message: String,
        requestId: String? = null,
    ) {
        sendMessage(BridgeError(code = code, message = message, requestId = requestId))
        close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, code))
    }

    val hello = try {
        val frame = incoming.receiveCatching().getOrNull() ?: return
        decodeMessage(frame)
    } catch (_: SerializationException) {
        reject(
            code = "MALFORMED_MESSAGE",
            message = "WebSocket message is not valid protocol JSON.",
        )
        return
    } catch (_: ProtocolMessageException) {
        reject(
            code = "MALFORMED_MESSAGE",
            message = "WebSocket message is not a supported text frame.",
        )
        return
    }

    val helloCommand = hello as? HelloCommand
    if (helloCommand == null) {
        reject(
            code = "INVALID_HANDSHAKE",
            message = "The first WebSocket message must be hello.",
            requestId = hello.requestIdOrNull(),
        )
        return
    }
    if (helloCommand.protocolVersion != ProtocolConstants.CURRENT_VERSION) {
        reject(
            code = "UNSUPPORTED_PROTOCOL_VERSION",
            message = "Unsupported protocol version.",
            requestId = helloCommand.requestId,
        )
        return
    }

    sendMessage(
        HelloAck(
            status = "ok",
            appVersion = appVersion,
            protocolVersion = ProtocolConstants.CURRENT_VERSION,
            serverTime = clock(),
            requestId = helloCommand.requestId,
        ),
    )

    val updates = Channel<TextState>(capacity = Channel.CONFLATED)
    kotlinx.coroutines.coroutineScope {
        val observationJob = launch {
            repository.state.collect { state -> updates.send(state) }
        }

        try {
            val initialState = try {
                withTimeout(initialSnapshotTimeoutMillis) { updates.receive() }
            } catch (_: TimeoutCancellationException) {
                reject(
                    code = "INITIAL_SNAPSHOT_TIMEOUT",
                    message = "Current text snapshot was not available in time.",
                )
                return@coroutineScope
            }

            sendMessage(initialState.toSnapshot())
            val pushJob = launch {
                for (state in updates) {
                    sendMessage(state.toChanged())
                }
            }

            try {
                for (frame in incoming) {
                    val message = try {
                        decodeMessage(frame)
                    } catch (_: SerializationException) {
                        reject(
                            code = "MALFORMED_MESSAGE",
                            message = "WebSocket message is not valid protocol JSON.",
                        )
                        break
                    } catch (_: ProtocolMessageException) {
                        reject(
                            code = "MALFORMED_MESSAGE",
                            message = "WebSocket message is not a supported text frame.",
                        )
                        break
                    }

                    when (message) {
                        is GetSnapshotCommand -> {
                            sendMessage(repository.state.first().toSnapshot(message.requestId))
                        }

                        is ClearCommand -> {
                            if (message.expectedVersion < 0L) {
                                sendMessage(
                                    BridgeError(
                                        code = "INVALID_EXPECTED_VERSION",
                                        message = "Expected version must be a non-negative integer.",
                                        requestId = message.requestId,
                                    ),
                                )
                            } else {
                                handleClear(message, repository, sendMutex, ::sendMessageUnlocked)
                            }
                        }

                        else -> {
                            reject(
                                code = "UNEXPECTED_MESSAGE",
                                message = "This message is not valid after the handshake.",
                                requestId = message.requestIdOrNull(),
                            )
                            break
                        }
                    }
                }
            } finally {
                pushJob.cancel()
            }
        } finally {
            observationJob.cancel()
            updates.cancel()
        }
    }
}

private suspend fun handleClear(
    command: ClearCommand,
    repository: TextRepository,
    sendMutex: Mutex,
    sendMessage: suspend (BridgeMessage) -> Unit,
) {
    sendMutex.withLock {
        try {
            when (val result = repository.clear(command.expectedVersion)) {
                is ClearResult.Cleared -> sendMessage(
                    ClearSucceeded(
                        clearedVersion = result.clearedVersion,
                        newVersion = result.newVersion,
                        requestId = command.requestId,
                    ),
                )

                is ClearResult.VersionConflict -> sendMessage(
                    VersionConflict(
                        currentVersion = result.currentVersion,
                        requestId = command.requestId,
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            sendMessage(
                BridgeError(
                    code = "TEXT_CLEAR_FAILED",
                    message = "Current text could not be cleared.",
                    requestId = command.requestId,
                ),
            )
        }
    }
}

private fun decodeMessage(frame: Frame): BridgeMessage {
    if (frame !is Frame.Text) throw ProtocolMessageException()
    return ProtocolJson.default.decodeFromString(frame.data.decodeToString())
}

private fun TextState.toSnapshot(requestId: String? = null): TextSnapshot =
    TextSnapshot(
        text = text,
        version = version,
        updatedAt = updatedAt,
        requestId = requestId,
    )

private fun TextState.toChanged(): TextChanged =
    TextChanged(
        text = text,
        version = version,
        updatedAt = updatedAt,
    )

private fun BridgeMessage.requestIdOrNull(): String? = when (this) {
    is HelloCommand -> requestId
    is GetSnapshotCommand -> requestId
    is ClearCommand -> requestId
    is HelloAck -> requestId
    is TextSnapshot -> requestId
    is ClearSucceeded -> requestId
    is VersionConflict -> requestId
    is BridgeError -> requestId
    is TextChanged -> null
}

private class ProtocolMessageException : Exception()
