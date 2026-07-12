package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTextRepositoryTest {
    @Test
    fun saveReturnsSuccessAfterDataSourceCompletes() = runTest {
        val dataSource = FakeTextDataSource(TextState("", 0L, 1L))
        val writeGate = CompletableDeferred<Unit>()
        dataSource.writeGate = writeGate
        val repositoryScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val repository = DefaultTextRepository(dataSource, repositoryScope)
        val save = async { repository.save(TextState("saved", 1L, 2L)) }

        runCurrent()

        assertFalse(save.isCompleted)
        writeGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(PersistenceResult.Succeeded(1L), save.await())
        assertEquals(listOf(TextState("saved", 1L, 2L)), dataSource.persisted)
        repositoryScope.cancel()
    }

    @Test
    fun supersededSaveCompletesWithoutHanging() = runTest {
        val dataSource = FakeTextDataSource(TextState("", 0L, 1L))
        val writeGate = CompletableDeferred<Unit>()
        dataSource.writeGate = writeGate
        val repositoryScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val repository = DefaultTextRepository(dataSource, repositoryScope)
        val first = async { repository.save(TextState("first", 1L, 2L)) }
        runCurrent()
        val second = async { repository.save(TextState("second", 2L, 3L)) }
        runCurrent()
        val third = async { repository.save(TextState("third", 3L, 4L)) }
        runCurrent()

        assertEquals(PersistenceResult.Superseded(2L), second.await())
        writeGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(PersistenceResult.Succeeded(1L), first.await())
        assertEquals(PersistenceResult.Succeeded(3L), third.await())
        assertEquals(
            listOf(
                TextState("first", 1L, 2L),
                TextState("third", 3L, 4L),
            ),
            dataSource.persisted,
        )
        repositoryScope.cancel()
    }

    @Test
    fun saveReturnsFailureWhenDataSourceWriteFails() = runTest {
        val repositoryScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val repository = DefaultTextRepository(
            dataSource = FailingTextDataSource(),
            scope = repositoryScope,
        )

        assertEquals(
            PersistenceResult.Failed(1L),
            repository.save(TextState("failed", 1L, 2L)),
        )
        repositoryScope.cancel()
    }
}

private class FakeTextDataSource(initialState: TextState) : TextDataSource {
    private val mutableState = MutableStateFlow(initialState)

    var writeGate: CompletableDeferred<Unit>? = null
    val persisted = mutableListOf<TextState>()

    override val state: Flow<TextState> = mutableState

    override suspend fun persist(state: TextState) {
        writeGate?.let { gate ->
            writeGate = null
            gate.await()
        }
        persisted += state
        mutableState.value = state
    }
}

private class FailingTextDataSource : TextDataSource {
    override val state: Flow<TextState> = MutableStateFlow(TextState("", 0L, 0L))

    override suspend fun persist(state: TextState) {
        error("write failed")
    }
}
