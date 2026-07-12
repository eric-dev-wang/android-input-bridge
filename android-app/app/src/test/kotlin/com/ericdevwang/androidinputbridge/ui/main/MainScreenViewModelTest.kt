package com.ericdevwang.androidinputbridge.ui.main

import com.ericdevwang.androidinputbridge.model.MAX_TEXT_CODE_POINTS
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    fun loadingStateBlocksInputUntilInitializationCompletes() = runTest {
        val initializeGate = CompletableDeferred<Unit>()
        val repository = FakeTextRepository(TextState("saved", 1L, 1L), initializeGate)
        val viewModel = MainScreenViewModel(repository)

        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.onTextChanged("ignored")
        assertTrue(repository.mutations.isEmpty())

        initializeGate.complete(Unit)
        viewModel.initializeJob.join()

        assertEquals(
            MainScreenUiState(
                isLoading = false,
                text = "saved",
                version = 1L,
                characterCount = 5,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun initializationFailureShowsEmptyRecoveryState() = runTest {
        val repository = FakeTextRepository(TextState("saved", 1L, 1L))
        repository.initializeFailure = IOException("read failed")
        val viewModel = MainScreenViewModel(repository)

        viewModel.initializeJob.join()

        assertEquals("", viewModel.uiState.value.text)
        assertEquals(0L, viewModel.uiState.value.version)
        assertEquals(0, viewModel.uiState.value.characterCount)
        assertTrue(!viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.persistenceMessage != null)
    }

    @Test
    fun rejectedOverLimitInputKeepsPreviousText() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)
        viewModel.initializeJob.join()

        viewModel.onTextChanged("a".repeat(MAX_TEXT_CODE_POINTS + 1))
        advanceUntilIdle()

        assertEquals("keep", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.persistenceMessage)
    }

    @Test
    fun acceptedInputMirrorsStateAndCountsUnicodeCodePoints() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        val viewModel = MainScreenViewModel(repository)
        viewModel.initializeJob.join()

        viewModel.onTextChanged("A😀")
        advanceUntilIdle()

        assertEquals("A😀", viewModel.uiState.value.text)
        assertEquals(1L, viewModel.uiState.value.version)
        assertEquals(2, viewModel.uiState.value.characterCount)
        assertNull(viewModel.uiState.value.persistenceMessage)
    }

    @Test
    fun writeFailureKeepsInMemoryTextAndShowsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)
        viewModel.initializeJob.join()
        repository.nextMutationFailure = IOException("write failed")

        viewModel.onTextChanged("updated")
        advanceUntilIdle()

        assertEquals("updated", viewModel.uiState.value.text)
        assertEquals(2L, viewModel.uiState.value.version)
        assertEquals(PersistenceMessage.SaveFailed, viewModel.uiState.value.persistenceMessage)
    }

    @Test
    fun laterSuccessfulMutationClearsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        val viewModel = MainScreenViewModel(repository)
        viewModel.initializeJob.join()
        repository.nextMutationFailure = IOException("write failed")

        viewModel.onTextChanged("first")
        advanceUntilIdle()
        viewModel.onTextChanged("second")
        advanceUntilIdle()

        assertEquals("second", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.persistenceMessage)
    }

    @Test
    fun clearEmptiesTextAndRecordsClearMutation() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)
        viewModel.initializeJob.join()

        viewModel.onClear()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.text)
        assertEquals(2L, viewModel.uiState.value.version)
        assertEquals(listOf(Mutation.Clear), repository.mutations)
    }

    @Test
    fun successfulClearClearsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)
        viewModel.initializeJob.join()
        repository.nextMutationFailure = IOException("write failed")

        viewModel.onTextChanged("updated")
        advanceUntilIdle()
        viewModel.onClear()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.persistenceMessage)
    }

    @Test
    fun rapidInputEventsAreAppliedInOrder() = runTest {
        val firstMutationGate = CompletableDeferred<Unit>()
        val secondMutationGate = CompletableDeferred<Unit>()
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        repository.mutationGates.addLast(firstMutationGate)
        repository.mutationGates.addLast(secondMutationGate)
        val viewModel = MainScreenViewModel(repository)
        viewModel.initializeJob.join()

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

        assertEquals("second", viewModel.uiState.value.text)
    }
}

private class FakeTextRepository(
    initialState: TextState,
    private val initializeGate: CompletableDeferred<Unit>? = null,
) : TextRepository {
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<TextState> = mutableState.asStateFlow()
    val mutations = mutableListOf<Mutation>()
    val mutationGates = ArrayDeque<CompletableDeferred<Unit>>()
    var initializeFailure: Throwable? = null
    var nextMutationFailure: Throwable? = null
    private var nextUpdatedAt = initialState.updatedAt + 1L

    override suspend fun initialize() {
        initializeGate?.await()
        initializeFailure?.let { throw it }
    }

    override suspend fun changeText(newText: String): TextChangeResult {
        mutations += Mutation.Change(newText)
        awaitMutationGate()
        val result = mutableState.value.changeText(newText, nextUpdatedAt++)
        if (result is TextChangeResult.Accepted) {
            mutableState.value = result.state
        }
        nextMutationFailure?.let {
            nextMutationFailure = null
            throw it
        }
        return result
    }

    override suspend fun clear(): TextState {
        mutations += Mutation.Clear
        awaitMutationGate()
        val cleared = mutableState.value.clear(nextUpdatedAt++)
        mutableState.value = cleared
        nextMutationFailure?.let {
            nextMutationFailure = null
            throw it
        }
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
