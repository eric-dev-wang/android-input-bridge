package com.ericdevwang.androidinputbridge.server

import com.ericdevwang.androidinputbridge.http.HttpClearResult
import com.ericdevwang.androidinputbridge.http.HttpTextRepository
import com.ericdevwang.androidinputbridge.protocol.ClearResponse
import com.ericdevwang.androidinputbridge.protocol.ErrorResponse
import com.ericdevwang.androidinputbridge.protocol.HealthResponse
import com.ericdevwang.androidinputbridge.protocol.TextResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import android.util.Log
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val DEFAULT_SERVER_HOST = "127.0.0.1"
const val DEFAULT_SERVER_PORT = 18080

data class InputHttpServerConfig(
    val host: String = DEFAULT_SERVER_HOST,
    val port: Int = DEFAULT_SERVER_PORT,
)

class InputHttpServer(
    private val repository: HttpTextRepository,
    private val config: InputHttpServerConfig = InputHttpServerConfig(),
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
            module = { module(repository) },
        ).start(wait = false).also {
            Log.i(TAG, "Listening on ${config.host}:${config.port}")
        }
    }

    @Synchronized
    fun stop() {
        val currentEngine = engine ?: return
        engine = null
        currentEngine.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
        Log.i(TAG, "Stopped on ${config.host}:${config.port}")
    }
}

fun Application.module(repository: HttpTextRepository) {
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = true
            },
        )
    }
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(
                status = HttpStatusCode.NotFound,
                code = "NOT_FOUND",
                message = "The requested endpoint was not found.",
            )
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respondError(
                status = HttpStatusCode.MethodNotAllowed,
                code = "METHOD_NOT_ALLOWED",
                message = "The HTTP method is not allowed for this endpoint.",
            )
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.respondError(
                status = HttpStatusCode.InternalServerError,
                code = "INTERNAL_SERVER_ERROR",
                message = "Unexpected server error.",
            )
        }
    }

    routing {
        get("/api/v1/health") {
            call.respondSuccess(repository.health())
        }

        get("/api/v1/text") {
            try {
                call.respondSuccess(repository.text())
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                call.respondError(
                    status = HttpStatusCode.InternalServerError,
                    code = "TEXT_READ_FAILED",
                    message = "Current text could not be read.",
                )
            }
        }

        post("/api/v1/text/clear/{expectedVersion}") {
            val expectedVersion = call.parameters["expectedVersion"]
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
            if (expectedVersion == null) {
                call.respondError(
                    status = HttpStatusCode.BadRequest,
                    code = "INVALID_EXPECTED_VERSION",
                    message = "Expected version must be a non-negative integer.",
                )
                return@post
            }

            try {
                when (val result = repository.clear(expectedVersion)) {
                    is HttpClearResult.Cleared -> call.respondSuccess(result.response)
                    is HttpClearResult.VersionConflict -> call.respondError(
                        status = HttpStatusCode.Conflict,
                        code = "VERSION_CONFLICT",
                        message = "Text changed after the client loaded it.",
                        details = buildJsonObject {
                            put("currentVersion", result.currentVersion)
                        },
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                call.respondError(
                    status = HttpStatusCode.InternalServerError,
                    code = "TEXT_CLEAR_FAILED",
                    message = "Current text could not be cleared.",
                )
            }
        }
    }
}

fun Application.module(
    repository: com.ericdevwang.androidinputbridge.repository.TextRepository,
    appVersion: String,
    clock: () -> Long = System::currentTimeMillis,
) {
    module(HttpTextRepository(repository, appVersion, clock))
}

private suspend fun io.ktor.server.application.ApplicationCall.respondSuccess(data: HealthResponse) {
    respond(HttpStatusCode.OK, data)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondSuccess(data: TextResponse) {
    respond(HttpStatusCode.OK, data)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondSuccess(data: ClearResponse) {
    respond(HttpStatusCode.OK, data)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
    details: JsonElement = buildJsonObject { },
) {
    respond(status, ErrorResponse(code, message, details))
}

private const val TAG = "InputHttpServer"
