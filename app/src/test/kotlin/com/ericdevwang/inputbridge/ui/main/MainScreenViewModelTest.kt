package com.ericdevwang.inputbridge.ui.main

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ericdevwang.inputbridge.core.data.model.MAX_TEXT_CODE_POINTS
import com.ericdevwang.inputbridge.core.data.model.TextState
import com.ericdevwang.inputbridge.core.data.repository.ClearResult
import com.ericdevwang.inputbridge.core.data.repository.PersistenceResult
import com.ericdevwang.inputbridge.core.data.repository.TextRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val viewModel = MainScreenViewModel(FakeTextRepository(TextState("saved", 1L, 1L)))

        advanceUntilIdle()

        assertEquals(
            MainScreenUiState.Content(
                textFieldValue = TextFieldValue("saved", selection = TextRange(5)),
                version = 1L,
                characterCount = 5,
            ),
            viewModel.contentState(),
        )
    }

    @Test
    fun inputStateUpdatesBeforePersistenceCompletes() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        val saveGate = CompletableDeferred<Unit>()
        repository.saveGate = saveGate
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("fast", selection = TextRange(4)))

        val stateBeforePersistence = viewModel.contentState()
        assertEquals("fast", stateBeforePersistence.textFieldValue.text)
        assertEquals(TextRange(4), stateBeforePersistence.textFieldValue.selection)
        assertEquals(TextState("fast", 1L, 2L), repository.saved.single())

        saveGate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.contentState().persistenceMessage)
    }

    @Test
    fun stateFlowFailureShowsInitializationError() = runTest {
        val viewModel = MainScreenViewModel(FailingTextRepository())

        advanceUntilIdle()

        assertEquals(MainScreenUiState.InitializationError, viewModel.uiState.value)
    }

    @Test
    fun rejectedOverLimitInputKeepsPreviousText() = runTest {
        val viewModel = MainScreenViewModel(FakeTextRepository(TextState("keep", 1L, 1L)))
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("a".repeat(MAX_TEXT_CODE_POINTS + 1)))

        val state = viewModel.contentState()
        assertEquals("keep", state.textFieldValue.text)
        assertNull(state.persistenceMessage)
    }

    @Test
    fun acceptedInputMirrorsStateAndCountsUnicodeCodePoints() = runTest {
        val viewModel = MainScreenViewModel(FakeTextRepository(TextState("", 0L, 1L)))
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("A😀", selection = TextRange(3)))
        advanceUntilIdle()

        val state = viewModel.contentState()
        assertEquals("A😀", state.textFieldValue.text)
        assertEquals(TextRange(3), state.textFieldValue.selection)
        assertEquals(1L, state.version)
        assertEquals(2, state.characterCount)
        assertNull(state.persistenceMessage)
    }

    @Test
    fun writeFailureKeepsInMemoryTextAndShowsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        repository.nextPersistenceFailure = true
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("updated", selection = TextRange(7)))
        advanceUntilIdle()

        val state = viewModel.contentState()
        assertEquals("updated", state.textFieldValue.text)
        assertEquals(2L, state.version)
        assertEquals(PersistenceMessage.SaveFailed, state.persistenceMessage)
    }

    @Test
    fun laterSuccessfulMutationClearsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        repository.nextPersistenceFailure = true
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("first", selection = TextRange(5)))
        advanceUntilIdle()
        viewModel.onTextChanged(TextFieldValue("second", selection = TextRange(6)))
        advanceUntilIdle()

        val state = viewModel.contentState()
        assertEquals("second", state.textFieldValue.text)
        assertNull(state.persistenceMessage)
    }

    @Test
    fun newInputKeepsSaveFailureUntilItsPersistenceCompletes() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        repository.nextPersistenceFailure = true
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("first", selection = TextRange(5)))
        advanceUntilIdle()
        assertEquals(PersistenceMessage.SaveFailed, viewModel.contentState().persistenceMessage)

        val saveGate = CompletableDeferred<Unit>()
        repository.saveGate = saveGate
        viewModel.onTextChanged(TextFieldValue("second", selection = TextRange(6)))

        assertEquals(PersistenceMessage.SaveFailed, viewModel.contentState().persistenceMessage)

        saveGate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.contentState().persistenceMessage)
    }

    @Test
    fun clearUpdatesMemoryAndSavesEmptySnapshot() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onClear()
        advanceUntilIdle()

        val state = viewModel.contentState()
        assertEquals("", state.textFieldValue.text)
        assertEquals(2L, state.version)
        assertEquals(listOf(1L), repository.clearedVersions)
        assertNull(state.persistenceMessage)
    }

    @Test
    fun clearDelegatesExpectedVersionAfterUpdatingUiImmediately() = runTest {
        val repository = FakeTextRepository(TextState("keep", 4L, 1L))
        val clearGate = CompletableDeferred<Unit>()
        repository.clearGate = clearGate
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onClear()

        assertEquals(listOf(4L), repository.clearedVersions)
        assertEquals("", viewModel.contentState().textFieldValue.text)
        assertEquals(5L, viewModel.contentState().version)

        clearGate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.contentState().persistenceMessage)
    }

    @Test
    fun clearFailureKeepsUiResponsiveAndShowsPersistenceMessage() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        repository.nextClearFailure = true
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onClear()
        advanceUntilIdle()

        assertEquals("", viewModel.contentState().textFieldValue.text)
        assertEquals(PersistenceMessage.SaveFailed, viewModel.contentState().persistenceMessage)
    }

    @Test
    fun sameVersionExternalTextReplacesLocalState() = runTest {
        val repository = FakeTextRepository(TextState("local", 1L, 1L))
        val viewModel = MainScreenViewModel(repository)
        advanceUntilIdle()

        repository.publish(TextState("external", 1L, 2L))
        advanceUntilIdle()

        assertEquals("external", viewModel.contentState().textFieldValue.text)
        assertEquals(1L, viewModel.contentState().version)
        assertEquals(8, viewModel.contentState().characterCount)
    }
}

private fun MainScreenViewModel.contentState(): MainScreenUiState.Content =
    uiState.value as MainScreenUiState.Content

private class FailingTextRepository : TextRepository {
    override val state: Flow<TextState> = flow {
        error("read failed")
    }

    override suspend fun save(state: TextState): PersistenceResult =
        PersistenceResult.Failed(state.version)

    override suspend fun clear(expectedVersion: Long): ClearResult =
        error("clear failed")
}

private class FakeTextRepository(
    initialState: TextState,
) : TextRepository {
    private val mutableState = MutableStateFlow(initialState)

    override val state: Flow<TextState> = mutableState
    val saved = mutableListOf<TextState>()
    val clearedVersions = mutableListOf<Long>()
    var saveGate: CompletableDeferred<Unit>? = null
    var clearGate: CompletableDeferred<Unit>? = null
    var nextPersistenceFailure = false
    var nextClearFailure = false

    fun publish(state: TextState) {
        mutableState.value = state
    }

    override suspend fun save(state: TextState): PersistenceResult {
        saved += state
        saveGate?.let { gate ->
            saveGate = null
            gate.await()
        }
        if (nextPersistenceFailure) {
            nextPersistenceFailure = false
            return PersistenceResult.Failed(state.version)
        }
        mutableState.value = state
        return PersistenceResult.Succeeded(state.version)
    }

    override suspend fun clear(expectedVersion: Long): ClearResult {
        clearedVersions += expectedVersion
        clearGate?.let { gate ->
            clearGate = null
            gate.await()
        }
        if (nextClearFailure) {
            nextClearFailure = false
            error("clear failed")
        }
        val current = mutableState.value
        if (current.version != expectedVersion) {
            return ClearResult.VersionConflict(current.version)
        }
        val cleared = current.clear(current.updatedAt + 1L)
        mutableState.value = cleared
        return ClearResult.Cleared(current.version, cleared.version)
    }
}
