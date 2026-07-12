package com.ericdevwang.androidinputbridge.persistence

import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TextPersistenceCoordinator(
    private val repository: TextRepository,
    scope: CoroutineScope,
) {
    private val requests = Channel<TextState>(capacity = Channel.CONFLATED)
    private val mutablePendingState = MutableStateFlow<TextState?>(null)
    private val mutableLastResult = MutableStateFlow<PersistenceResult?>(null)

    val pendingState: StateFlow<TextState?> = mutablePendingState.asStateFlow()
    val lastResult: StateFlow<PersistenceResult?> = mutableLastResult.asStateFlow()

    init {
        scope.launch {
            for (state in requests) {
                persist(state)
            }
        }
    }

    fun enqueue(state: TextState) {
        mutablePendingState.update { pendingState ->
            if (pendingState == null || state.version >= pendingState.version) state else pendingState
        }
        requests.trySend(state)
    }

    private suspend fun persist(state: TextState) {
        try {
            repository.persist(state)
            mutablePendingState.update { pendingState ->
                pendingState?.takeIf { it.version > state.version }
            }
            mutableLastResult.value = PersistenceResult.Succeeded(state.version)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableLastResult.value = PersistenceResult.Failed(state.version)
        }
    }
}

sealed interface PersistenceResult {
    val version: Long

    data class Succeeded(override val version: Long) : PersistenceResult

    data class Failed(override val version: Long) : PersistenceResult
}
