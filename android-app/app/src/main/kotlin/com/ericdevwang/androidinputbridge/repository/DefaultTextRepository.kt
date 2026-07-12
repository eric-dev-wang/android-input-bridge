package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class DefaultTextRepository(
    private val dataSource: TextDataSource,
    scope: CoroutineScope,
) : TextRepository {
    private data class SaveRequest(
        val state: TextState,
        val result: CompletableDeferred<PersistenceResult>,
    )

    private val requests = Channel<SaveRequest>(
        capacity = Channel.CONFLATED,
        onUndeliveredElement = { request ->
            request.result.complete(PersistenceResult.Superseded(request.state.version))
        },
    )

    override val state: Flow<TextState> = dataSource.state

    init {
        scope.launch {
            for (request in requests) {
                persist(request)
            }
        }
    }

    override suspend fun save(state: TextState): PersistenceResult {
        val request = SaveRequest(state, CompletableDeferred())
        requests.send(request)
        return request.result.await()
    }

    private suspend fun persist(request: SaveRequest) {
        val result = try {
            dataSource.persist(request.state)
            PersistenceResult.Succeeded(request.state.version)
        } catch (error: Exception) {
            if (error is CancellationException) {
                request.result.cancel(error)
                throw error
            }
            PersistenceResult.Failed(request.state.version)
        }
        request.result.complete(result)
    }
}
