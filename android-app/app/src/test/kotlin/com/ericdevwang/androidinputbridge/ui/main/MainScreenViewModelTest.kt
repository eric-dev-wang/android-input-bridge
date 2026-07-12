package com.ericdevwang.androidinputbridge.ui.main

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.ericdevwang.androidinputbridge.model.MAX_TEXT_CODE_POINTS
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.persistence.TextPersistenceCoordinator
import com.ericdevwang.androidinputbridge.repository.TextRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
        val persistGate = CompletableDeferred<Unit>()
        repository.persistGate = persistGate
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("fast", selection = TextRange(4)))

        val stateBeforePersistence = viewModel.contentState()
        assertEquals("fast", stateBeforePersistence.textFieldValue.text)
        assertEquals(TextRange(4), stateBeforePersistence.textFieldValue.selection)
        assertTrue(repository.persisted.isEmpty())

        persistGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(TextState("fast", 1L, 2L), repository.persisted.last())
    }

    @Test
    fun pendingPersistenceSurvivesViewModelClear() = runTest {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        val persistGate = CompletableDeferred<Unit>()
        repository.persistGate = persistGate
        val coordinatorScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val coordinator = TextPersistenceCoordinator(repository, coordinatorScope)
        val viewModel = MainScreenViewModel(
            repository = repository,
            persistenceCoordinator = coordinator,
            clock = { 2L },
        )
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("fast", selection = TextRange(4)))
        viewModel.viewModelScope.cancel()

        val recreatedViewModel = MainScreenViewModel(
            repository = repository,
            persistenceCoordinator = coordinator,
            clock = { 3L },
        )
        runCurrent()

        assertEquals("fast", recreatedViewModel.contentState().textFieldValue.text)
        assertTrue(repository.persisted.isEmpty())

        persistGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(TextState("fast", 1L, 2L), repository.persisted.last())
        coordinatorScope.cancel()
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
        repository.nextPersistenceFailure = IOException("write failed")
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
        repository.nextPersistenceFailure = IOException("write failed")
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
        repository.nextPersistenceFailure = IOException("write failed")
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onTextChanged(TextFieldValue("first", selection = TextRange(5)))
        advanceUntilIdle()
        assertEquals(PersistenceMessage.SaveFailed, viewModel.contentState().persistenceMessage)

        val persistGate = CompletableDeferred<Unit>()
        repository.persistGate = persistGate
        viewModel.onTextChanged(TextFieldValue("second", selection = TextRange(6)))

        assertEquals(PersistenceMessage.SaveFailed, viewModel.contentState().persistenceMessage)

        persistGate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.contentState().persistenceMessage)
    }

    @Test
    fun clearUpdatesMemoryAndQueuesEmptySnapshot() = runTest {
        val repository = FakeTextRepository(TextState("keep", 1L, 1L))
        val viewModel = MainScreenViewModel(repository, clock = { 2L })
        advanceUntilIdle()

        viewModel.onClear()
        advanceUntilIdle()

        val state = viewModel.contentState()
        assertEquals("", state.textFieldValue.text)
        assertEquals(2L, state.version)
        assertEquals(TextState("", 2L, 2L), repository.persisted.last())
    }
}

private fun MainScreenViewModel.contentState(): MainScreenUiState.Content =
    uiState.value as MainScreenUiState.Content

private class FailingTextRepository : TextRepository {
    override val state: Flow<TextState> = flow {
        throw IOException("read failed")
    }

    override suspend fun persist(state: TextState) = error("unreachable")
}

private class FakeTextRepository(
    initialState: TextState,
) : TextRepository {
    private val mutableState = MutableStateFlow(initialState)

    override val state: Flow<TextState> = mutableState
    val persisted = mutableListOf<TextState>()
    var persistGate: CompletableDeferred<Unit>? = null
    var nextPersistenceFailure: Throwable? = null

    override suspend fun persist(state: TextState) {
        persistGate?.await()
        nextPersistenceFailure?.let {
            nextPersistenceFailure = null
            throw it
        }
        persisted += state
        mutableState.value = state
    }
}
