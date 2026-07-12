package com.ericdevwang.androidinputbridge.http

import com.ericdevwang.androidinputbridge.repository.ClearResult
import com.ericdevwang.androidinputbridge.repository.TextRepository
import kotlinx.coroutines.flow.first

class HttpTextRepository(
    private val repository: TextRepository,
    private val appVersion: String = DEFAULT_APP_VERSION,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun text(): TextResponse {
        return repository.state.first().let { state ->
            TextResponse(
                text = state.text,
                version = state.version,
                updatedAt = state.updatedAt,
            )
        }
    }

    fun health(): HealthResponse = HealthResponse(
        status = "ok",
        appVersion = appVersion,
        protocolVersion = PROTOCOL_VERSION,
        serverTime = clock(),
    )

    suspend fun clear(expectedVersion: Long): HttpClearResult =
        when (val result = repository.clear(expectedVersion)) {
            is ClearResult.Cleared -> HttpClearResult.Cleared(
                ClearResponse(
                    clearedVersion = result.clearedVersion,
                    newVersion = result.newVersion,
                ),
            )

            is ClearResult.VersionConflict -> HttpClearResult.VersionConflict(
                currentVersion = result.currentVersion,
            )
        }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val DEFAULT_APP_VERSION = "1.0.0"
    }
}

sealed interface HttpClearResult {
    data class Cleared(val response: ClearResponse) : HttpClearResult

    data class VersionConflict(val currentVersion: Long) : HttpClearResult
}
