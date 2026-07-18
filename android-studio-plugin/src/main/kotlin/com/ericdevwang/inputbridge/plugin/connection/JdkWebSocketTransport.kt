package com.ericdevwang.inputbridge.plugin.connection

import com.ericdevwang.inputbridge.protocol.BridgeMessage
import com.ericdevwang.inputbridge.protocol.ProtocolConstants
import com.ericdevwang.inputbridge.protocol.ProtocolJson
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class JdkWebSocketTransport(
    private val httpClient: HttpClient,
    private val endpoint: URI,
    private val connectTimeout: Duration = BridgeNetworkConfig.websocketConnectTimeout,
    private val requestTimeout: Duration = BridgeNetworkConfig.websocketRequestTimeout,
) : BridgeWebSocketTransport {
    override fun connect(listener: BridgeWebSocketTransportListener): BridgeWebSocketTransportResult {
        val webSocket = try {
            httpClient.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(endpoint, Listener(listener))
                .orTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .join()
        } catch (exception: CompletionException) {
            val cause = exception.cause ?: exception
            listener.onError(cause)
            return BridgeWebSocketTransportResult.Failure(
                message = "WebSocket connection failed: ${cause.message ?: "unknown error"}",
                cause = cause,
            )
        } catch (exception: Exception) {
            listener.onError(exception)
            return BridgeWebSocketTransportResult.Failure(
                message = "WebSocket connection failed: ${exception.message ?: "unknown error"}",
                cause = exception,
            )
        }
        return BridgeWebSocketTransportResult.Connected(
            Session(webSocket, listener, requestTimeout),
        )
    }

    override fun close() {
        httpClient.close()
    }

    private class Session(
        private val webSocket: WebSocket,
        private val listener: BridgeWebSocketTransportListener,
        private val requestTimeout: Duration,
    ) : BridgeWebSocketSession {
        override fun send(message: BridgeMessage): Boolean {
            return try {
                webSocket.sendText(
                    ProtocolJson.default.encodeToString<BridgeMessage>(message),
                    true,
                ).orTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS).join()
                true
            } catch (exception: Exception) {
                listener.onError(exception.cause ?: exception)
                false
            }
        }

        override fun close() {
            webSocket.abort()
        }
    }

    private class Listener(
        private val delegate: BridgeWebSocketTransportListener,
    ) : WebSocket.Listener {
        private val text = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(
            webSocket: WebSocket,
            data: CharSequence,
            last: Boolean,
        ): CompletionStage<*> {
            text.append(data)
            if (text.toString().toByteArray(StandardCharsets.UTF_8).size > ProtocolConstants.MAX_WEBSOCKET_MESSAGE_BYTES) {
                val error = IllegalArgumentException("WebSocket message exceeds the maximum size.")
                text.setLength(0)
                delegate.onError(error)
                webSocket.abort()
                return CompletableFuture.completedFuture(null)
            }
            if (last) {
                val message = text.toString()
                text.setLength(0)
                try {
                    delegate.onMessage(ProtocolJson.default.decodeFromString<BridgeMessage>(message))
                } catch (exception: SerializationException) {
                    delegate.onError(exception)
                }
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onBinary(
            webSocket: WebSocket,
            data: java.nio.ByteBuffer,
            last: Boolean,
        ): CompletionStage<*> {
            delegate.onError(IllegalArgumentException("Binary WebSocket messages are not supported."))
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onPing(webSocket: WebSocket, message: java.nio.ByteBuffer): CompletionStage<*> {
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onPong(webSocket: WebSocket, message: java.nio.ByteBuffer): CompletionStage<*> {
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(
            webSocket: WebSocket,
            statusCode: Int,
            reason: String,
        ): CompletionStage<*> {
            delegate.onClosed(IOException("WebSocket closed: $statusCode ${reason.ifBlank { "without a reason" }}"))
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            delegate.onError(error)
        }
    }

    companion object {
        fun create(): JdkWebSocketTransport = JdkWebSocketTransport(
            httpClient = HttpClient.newBuilder().build(),
            endpoint = URI.create(
                "ws://${BridgeNetworkConfig.HOST}:${BridgeNetworkConfig.PORT}${ProtocolConstants.WEBSOCKET_PATH}",
            ),
        )
    }
}
