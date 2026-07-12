package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultTextRepository(
    private val dataSource: TextDataSource,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : TextRepository {
    private sealed interface WriteRequest {
        val result: CompletableDeferred<*>
    }

    private class SaveRequest(
        val state: TextState,
        override val result: CompletableDeferred<PersistenceResult>,
    ) : WriteRequest

    private class ClearRequest(
        val expectedVersion: Long,
        override val result: CompletableDeferred<ClearResult>,
    ) : WriteRequest

    private val requests = ArrayDeque<WriteRequest>()
    private val requestsMutex = Mutex()
    private val requestSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    override val state: Flow<TextState> = dataSource.state

    init {
        scope.launch { processRequests() }
    }

    override suspend fun save(state: TextState): PersistenceResult {
        val request = SaveRequest(state, CompletableDeferred())
        enqueueSave(request)
        return request.result.await()
    }

    override suspend fun clear(expectedVersion: Long): ClearResult {
        val request = ClearRequest(expectedVersion, CompletableDeferred())
        enqueue(request)
        return request.result.await()
    }

    private suspend fun enqueueSave(request: SaveRequest) {
        requestsMutex.withLock {
            val last = if (requests.isEmpty()) null else requests.last()
            if (last is SaveRequest) {
                requests.removeLast()
                last.result.complete(PersistenceResult.Superseded(last.state.version))
            }
            requests.addLast(request)
            requestSignal.trySend(Unit)
        }
    }

    private suspend fun enqueue(request: WriteRequest) {
        requestsMutex.withLock {
            requests.addLast(request)
            requestSignal.trySend(Unit)
        }
    }

    private suspend fun processRequests() {
        try {
            while (currentCoroutineContext().isActive) {
                val request = requestsMutex.withLock {
                    if (requests.isEmpty()) null else requests.removeFirst()
                }
                if (request == null) {
                    requestSignal.receive()
                    continue
                }
                process(request)
            }
        } finally {
            requestsMutex.withLock {
                requests.forEach { request -> request.result.cancel() }
                requests.clear()
            }
        }
    }

    private suspend fun process(request: WriteRequest) {
        when (request) {
            is SaveRequest -> processSave(request)
            is ClearRequest -> processClear(request)
        }
    }

    private suspend fun processSave(request: SaveRequest) {
        val result = try {
            if (dataSource.saveIfNewer(request.state)) {
                PersistenceResult.Succeeded(request.state.version)
            } else {
                PersistenceResult.Superseded(request.state.version)
            }
        } catch (error: Exception) {
            if (error is CancellationException) {
                request.result.cancel(error)
                throw error
            }
            PersistenceResult.Failed(request.state.version)
        }
        request.result.complete(result)
    }

    private suspend fun processClear(request: ClearRequest) {
        try {
            request.result.complete(
                dataSource.clearIfVersion(
                    expectedVersion = request.expectedVersion,
                    nowMillis = clock(),
                ),
            )
        } catch (error: Exception) {
            if (error is CancellationException) {
                request.result.cancel(error)
                throw error
            }
            request.result.completeExceptionally(error)
        }
    }
}
