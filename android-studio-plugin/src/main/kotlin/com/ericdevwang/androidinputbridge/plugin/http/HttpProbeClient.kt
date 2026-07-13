package com.ericdevwang.androidinputbridge.plugin.http

import com.ericdevwang.androidinputbridge.protocol.HealthResponse
import com.ericdevwang.androidinputbridge.protocol.ClearResponse
import com.ericdevwang.androidinputbridge.protocol.ProtocolConstants
import com.ericdevwang.androidinputbridge.protocol.TextResponse
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse as JdkHttpResponse
import java.time.Duration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class HttpResponse(
    val statusCode: Int,
    val body: String,
)

interface HttpProbeTransport : AutoCloseable {
    @Throws(IOException::class)
    fun get(path: String): HttpResponse

    @Throws(IOException::class)
    fun post(path: String): HttpResponse

    override fun close() = Unit
}

enum class ProbeFailureCategory {
    CONNECTION,
    INVALID_RESPONSE,
}

data class ProbeError(
    val category: ProbeFailureCategory,
    val message: String,
    val retryable: Boolean,
    val cause: Throwable? = null,
    val statusCode: Int? = null,
)

sealed interface HttpProbeResult<out T> {
    data class Success<T>(val value: T) : HttpProbeResult<T>

    data class Failure(val error: ProbeError) : HttpProbeResult<Nothing>
}

data class BridgeProbe(
    val health: HealthResponse,
    val text: TextResponse,
)

interface HttpProbeClient : AutoCloseable {
    fun probe(): HttpProbeResult<BridgeProbe>

    fun fetchText(): HttpProbeResult<TextResponse>

    fun clearText(expectedVersion: Long): HttpProbeResult<ClearResponse>

    override fun close() = Unit
}

class JdkHttpProbeClient(
    private val transport: HttpProbeTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : HttpProbeClient {
    override fun close() {
        transport.close()
    }

    override fun probe(): HttpProbeResult<BridgeProbe> {
        val health = when (val result = get<HealthResponse>(HEALTH_PATH, requireHttp200 = true)) {
            is HttpProbeResult.Failure -> return result
            is HttpProbeResult.Success -> result.value
        }
        if (health.status != "ok") {
            return invalidResponse("Health response status was '${health.status}'.")
        }
        if (health.protocolVersion != ProtocolConstants.CURRENT_VERSION) {
            return invalidResponse(
                "Health response protocol version ${health.protocolVersion} does not match " +
                    "${ProtocolConstants.CURRENT_VERSION}.",
            )
        }
        val text = when (val result = get<TextResponse>(TEXT_PATH)) {
            is HttpProbeResult.Failure -> return result
            is HttpProbeResult.Success -> result.value
        }
        return HttpProbeResult.Success(BridgeProbe(health = health, text = text))
    }

    override fun fetchText(): HttpProbeResult<TextResponse> = get(TEXT_PATH)

    override fun clearText(expectedVersion: Long): HttpProbeResult<ClearResponse> {
        if (expectedVersion < 0) {
            return invalidResponse("expectedVersion must not be negative.")
        }
        return post("$CLEAR_PATH/$expectedVersion")
    }

    private inline fun <reified T> get(
        path: String,
        requireHttp200: Boolean = false,
    ): HttpProbeResult<T> = request(path, requireHttp200, transport::get)

    private inline fun <reified T> post(path: String): HttpProbeResult<T> =
        request(path, requireHttp200 = true, transport::post)

    private inline fun <reified T> request(
        path: String,
        requireHttp200: Boolean,
        send: (String) -> HttpResponse,
    ): HttpProbeResult<T> {
        val response = try {
            send(path)
        } catch (exception: IOException) {
            return HttpProbeResult.Failure(
                ProbeError(
                    category = ProbeFailureCategory.CONNECTION,
                    message = "HTTP connection failed: ${exception.message ?: "unknown error"}",
                    retryable = true,
                    cause = exception,
                ),
            )
        }
        if ((requireHttp200 && response.statusCode != 200) || (!requireHttp200 && response.statusCode !in 200..299)) {
            return invalidResponse("HTTP $path returned status ${response.statusCode}.", response.statusCode)
        }
        return try {
            HttpProbeResult.Success(json.decodeFromString<T>(response.body))
        } catch (exception: SerializationException) {
            HttpProbeResult.Failure(
                ProbeError(
                    category = ProbeFailureCategory.INVALID_RESPONSE,
                    message = "HTTP $path returned invalid JSON.",
                    retryable = false,
                    cause = exception,
                ),
            )
        }
    }

    private fun invalidResponse(message: String, statusCode: Int? = null): HttpProbeResult.Failure =
        HttpProbeResult.Failure(
            ProbeError(
                category = ProbeFailureCategory.INVALID_RESPONSE,
                message = message,
                retryable = false,
                statusCode = statusCode,
            ),
        )

    private companion object {
        const val HEALTH_PATH = "/api/v1/health"
        const val TEXT_PATH = "/api/v1/text"
        const val CLEAR_PATH = "/api/v1/text/clear"
    }
}

class JdkHttpProbeTransport(
    private val httpClient: HttpClient,
    private val baseUri: URI,
    private val requestTimeout: Duration = Duration.ofSeconds(2),
) : HttpProbeTransport {
    override fun close() {
        httpClient.close()
    }

    override fun get(path: String): HttpResponse {
        val request = HttpRequest.newBuilder(baseUri.resolve(path.removePrefix("/")))
            .timeout(requestTimeout)
            .header("Accept", "application/json")
            .GET()
            .build()
        return try {
            val response = httpClient.send(request, JdkHttpResponse.BodyHandlers.ofString())
            HttpResponse(statusCode = response.statusCode(), body = response.body())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("HTTP request interrupted", exception)
        }
    }

    override fun post(path: String): HttpResponse {
        val request = HttpRequest.newBuilder(baseUri.resolve(path.removePrefix("/")))
            .timeout(requestTimeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        return try {
            val response = httpClient.send(request, JdkHttpResponse.BodyHandlers.ofString())
            HttpResponse(statusCode = response.statusCode(), body = response.body())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("HTTP request interrupted", exception)
        }
    }

    companion object {
        fun create(baseUri: URI = URI.create("http://127.0.0.1:18080/")): JdkHttpProbeTransport =
            JdkHttpProbeTransport(
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .build(),
                baseUri = baseUri,
            )
    }
}
