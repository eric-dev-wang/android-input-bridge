package com.ericdevwang.androidinputbridge.ui.main

import com.ericdevwang.androidinputbridge.model.MAX_TEXT_CODE_POINTS
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stateFlowLoadsSavedStateWithoutExplicitInitialization() = runTest {
        val repository = FakeTextRepository(TextState("saved", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)

        assertEquals(
            MainScreenUiState(
                isLoading = false,
                text = "saved",
                version = 1L,
                characterCount = 5,
            ),
            viewModel.uiState.first(),
        )
    }

    @Test
    fun stateFlowFailureShowsEmptyRecoveryState() = runTest {
        val viewModel = MainScreenViewModel(FailingTextRepository())

        val state = viewModel.uiState.first()

        assertEquals("", state.text)
        assertEquals(0L, state.version)
        assertEquals(0, state.characterCount)
        assertTrue(!state.isLoading)
        assertEquals(PersistenceMessage.InitializationFailed, state.persistenceMessage)
    }

    @Test
    fun rejectedOverLimitInputKeepsPreviousText() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)

        viewModel.onTextChanged("a".repeat(MAX_TEXT_CODE_POINTS + 1))
        advanceUntilIdle()

        assertEquals("keep", viewModel.uiState.first().text)
        assertNull(viewModel.uiState.first().persistenceMessage)
    }

    @Test
    fun acceptedInputMirrorsStateAndCountsUnicodeCodePoints() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        val viewModel = MainScreenViewModel(repository)

        viewModel.onTextChanged("A😀")
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("A😀", state.text)
        assertEquals(1L, state.version)
        assertEquals(2, state.characterCount)
        assertNull(state.persistenceMessage)
    }

    @Test
    fun writeFailureKeepsPersistedTextAndShowsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)
        repository.nextMutationFailure = IOException("write failed")

        viewModel.onTextChanged("updated")
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("keep", state.text)
        assertEquals(1L, state.version)
        assertEquals(PersistenceMessage.SaveFailed, state.persistenceMessage)
    }

    @Test
    fun laterSuccessfulMutationClearsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        val viewModel = MainScreenViewModel(repository)
        repository.nextMutationFailure = IOException("write failed")

        viewModel.onTextChanged("first")
        advanceUntilIdle()
        viewModel.onTextChanged("second")
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("second", state.text)
        assertNull(state.persistenceMessage)
    }

    @Test
    fun clearEmptiesTextAndRecordsClearMutation() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)

        viewModel.onClear()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("", state.text)
        assertEquals(2L, state.version)
        assertEquals(listOf(Mutation.Clear), repository.mutations)
    }

    @Test
    fun successfulClearClearsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)
        repository.nextMutationFailure = IOException("write failed")

        viewModel.onTextChanged("updated")
        advanceUntilIdle()
        viewModel.onClear()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("", state.text)
        assertNull(state.persistenceMessage)
    }

    @Test
    fun rapidInputEventsAreAppliedInOrder() = runTest {
        val firstMutationGate = CompletableDeferred<Unit>()
        val secondMutationGate = CompletableDeferred<Unit>()
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        repository.mutationGates.addLast(firstMutationGate)
        repository.mutationGates.addLast(secondMutationGate)
        val viewModel = MainScreenViewModel(repository)

        viewModel.onTextChanged("first")
        runCurrent()
        viewModel.onTextChanged("second")
        runCurrent()
        assertEquals(listOf(Mutation.Change("first")), repository.mutations)

        firstMutationGate.complete(Unit)
        runCurrent()
        assertEquals(
            listOf(Mutation.Change("first"), Mutation.Change("second")),
            repository.mutations,
        )

        secondMutationGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("second", viewModel.uiState.first().text)
    }
}

private class FailingTextRepository : TextRepository {
    override val state: Flow<TextState> = flow {
        throw IOException("read failed")
    }

    override suspend fun changeText(newText: String): TextChangeResult =
        error("unreachable")

    override suspend fun clear(): TextState = error("unreachable")
}

private class FakeTextRepository(
    initialState: TextState,
) : TextRepository {
    private val mutableState = MutableStateFlow(initialState)

    override val state: Flow<TextState> = mutableState
    val mutations = mutableListOf<Mutation>()
    val mutationGates = ArrayDeque<CompletableDeferred<Unit>>()
    var nextMutationFailure: Throwable? = null
    private var nextUpdatedAt = initialState.updatedAt + 1L

    override suspend fun changeText(newText: String): TextChangeResult {
        mutations += Mutation.Change(newText)
        awaitMutationGate()
        val result = mutableState.value.changeText(newText, nextUpdatedAt++)
        nextMutationFailure?.let {
            nextMutationFailure = null
            throw it
        }
        if (result is TextChangeResult.Accepted && result.state != mutableState.value) {
            mutableState.value = result.state
        }
        return result
    }

    override suspend fun clear(): TextState {
        mutations += Mutation.Clear
        awaitMutationGate()
        val cleared = mutableState.value.clear(nextUpdatedAt++)
        nextMutationFailure?.let {
            nextMutationFailure = null
            throw it
        }
        mutableState.value = cleared
        return cleared
    }

    private suspend fun awaitMutationGate() {
        if (mutationGates.isNotEmpty()) {
            mutationGates.removeFirst().await()
        }
    }
}

private sealed interface Mutation {
    data class Change(val text: String) : Mutation
    data object Clear : Mutation
}
