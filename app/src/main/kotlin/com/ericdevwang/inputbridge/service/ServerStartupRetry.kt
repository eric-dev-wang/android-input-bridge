package com.ericdevwang.inputbridge.service

import kotlinx.coroutines.CancellationException

internal class ServerStartupRetry(
    private val start: () -> Unit,
    private val wait: suspend (Long) -> Unit,
    private val onFailure: (attempt: Int, error: Exception) -> Unit = { _, _ -> },
) {
    suspend fun run(): Boolean {
        repeat(MAX_ATTEMPTS) { attemptIndex ->
            try {
                start()
                return true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val attempt = attemptIndex + 1
                onFailure(attempt, error)
                if (attempt < MAX_ATTEMPTS) {
                    wait(RETRY_DELAYS_MILLIS[attemptIndex])
                }
            }
        }
        return false
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        val RETRY_DELAYS_MILLIS = longArrayOf(1_000L, 2_000L)
    }
}
