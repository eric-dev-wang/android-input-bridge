package com.ericdevwang.androidinputbridge.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ericdevwang.androidinputbridge.model.MAX_TEXT_CODE_POINTS
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import com.ericdevwang.androidinputbridge.theme.AndroidInputBridgeTheme
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inputUpdatesTextCharacterCountAndVersion() {
        setScreen(FakeTextRepository(TextState("", 0L, 1L)))

        composeTestRule.onNodeWithTag(INPUT_TEXT).performTextInput("A😀")

        composeTestRule.onNodeWithTag(INPUT_TEXT).assertTextEquals("A😀")
        composeTestRule.onNodeWithTag(CHARACTER_COUNT).assertTextEquals("Characters: 2")
        composeTestRule.onNodeWithTag(VERSION_TEXT).assertTextEquals("Version: 1")
    }

    @Test
    fun clearButtonClearsText() {
        setScreen(FakeTextRepository(TextState("hello", 1L, 1L)))

        composeTestRule.onNodeWithTag(CLEAR_BUTTON).performClick()

        composeTestRule.onNodeWithTag(INPUT_TEXT).assertTextEquals("")
        composeTestRule.onNodeWithTag(CHARACTER_COUNT).assertTextEquals("Characters: 0")
        composeTestRule.onNodeWithTag(VERSION_TEXT).assertTextEquals("Version: 2")
    }

    @Test
    fun loadingDisablesInputAndClearButton() {
        val initializeGate = CompletableDeferred<Unit>()
        setScreen(FakeTextRepository(TextState("saved", 1L, 1L), initializeGate))

        composeTestRule.onNodeWithTag(INPUT_TEXT).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(CLEAR_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun emptyTextKeepsClearButtonEnabled() {
        setScreen(FakeTextRepository(TextState("", 0L, 1L)))

        composeTestRule.onNodeWithTag(CLEAR_BUTTON).assertIsEnabled()
        composeTestRule.onNodeWithTag(CLEAR_BUTTON).performClick()
        composeTestRule.onNodeWithTag(VERSION_TEXT).assertTextEquals("Version: 0")
    }

    @Test
    fun rejectedOverLimitEditRestoresPreviousText() {
        setScreen(FakeTextRepository(TextState("keep", 1L, 1L)))

        composeTestRule.onNodeWithTag(INPUT_TEXT)
            .performTextInput("a".repeat(MAX_TEXT_CODE_POINTS + 1))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(INPUT_TEXT).assertTextEquals("keep")
    }

    @Test
    fun persistenceErrorIsRenderedWithoutBlockingInput() {
        val repository = FakeTextRepository(TextState("", 0L, 1L))
        repository.nextMutationFailure = IOException("write failed")
        setScreen(repository)

        composeTestRule.onNodeWithTag(INPUT_TEXT).performTextInput("saved")

        composeTestRule.onNodeWithTag(PERSISTENCE_ERROR)
            .assertTextEquals("Text could not be saved.")
        composeTestRule.onNodeWithTag(INPUT_TEXT).assertIsEnabled()
    }

    private fun setScreen(repository: FakeTextRepository) {
        setScreen(MainScreenViewModel(repository))
    }

    private fun setScreen(viewModel: MainScreenViewModel) {
        composeTestRule.setContent {
            AndroidInputBridgeTheme {
                MainScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val INPUT_TEXT = "input_text"
        const val CHARACTER_COUNT = "character_count"
        const val VERSION_TEXT = "version_text"
        const val CLEAR_BUTTON = "clear_button"
        const val PERSISTENCE_ERROR = "persistence_error"
    }
}

private class FakeTextRepository(
    initialState: TextState,
    private val initializeGate: CompletableDeferred<Unit>? = null,
) : TextRepository {
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<TextState> = mutableState.asStateFlow()
    var nextMutationFailure: Throwable? = null
    private var nextUpdatedAt = initialState.updatedAt + 1L

    override suspend fun initialize() {
        initializeGate?.await()
    }

    override suspend fun changeText(newText: String): TextChangeResult {
        val result = mutableState.value.changeText(newText, nextUpdatedAt++)
        if (result is TextChangeResult.Accepted && result.state != mutableState.value) {
            mutableState.value = result.state
            nextMutationFailure?.also { failure ->
                nextMutationFailure = null
                throw failure
            }
        }
        return result
    }

    override suspend fun clear(): TextState {
        val cleared = mutableState.value.clear(nextUpdatedAt++)
        if (cleared != mutableState.value) {
            mutableState.value = cleared
            nextMutationFailure?.also { failure ->
                nextMutationFailure = null
                throw failure
            }
        }
        return cleared
    }
}
