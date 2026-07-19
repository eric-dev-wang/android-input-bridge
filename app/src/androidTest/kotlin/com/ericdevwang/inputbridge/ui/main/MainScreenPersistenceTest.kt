package com.ericdevwang.inputbridge.ui.main

import android.Manifest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ericdevwang.inputbridge.MainActivity
import com.ericdevwang.inputbridge.core.data.repository.ClearResult
import com.ericdevwang.inputbridge.core.data.repository.TextRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext

class MainScreenPersistenceTest {
    @get:Rule
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun clearPersistedState() {
        resetRepositoryState()
    }

    @Test
    fun multilineUnicodeTextRestoresAfterActivityRecreation() {
        val clearedVersion = resetRepositoryState()
        recreateActivity()

        val text = "Hello\n你好 👋🏽\n😀"
        composeTestRule.onNodeWithTag(INPUT_TEXT).performTextInput(text)
        composeTestRule.waitForIdle()

        assertRestoredState(text, clearedVersion + 1L)

        recreateActivity()

        assertRestoredState(text, clearedVersion + 1L)
    }

    private fun recreateActivity() {
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    private fun assertRestoredState(text: String, version: Long) {
        composeTestRule.onNodeWithTag(INPUT_TEXT).assertTextEquals(text)
        composeTestRule.onNodeWithTag(VERSION_TEXT).assertTextEquals("Version: $version")
        composeTestRule.onNodeWithTag(CHARACTER_COUNT).assertTextEquals("Characters: 13")
    }

    private fun resetRepositoryState(): Long = runBlocking {
        val repository = GlobalContext.get().get<TextRepository>()
        val currentState = repository.state.first()
        when (val result = repository.clear(currentState.version)) {
            is ClearResult.Cleared -> result.newVersion
            is ClearResult.VersionConflict -> {
                error("Unable to reset repository state at version ${currentState.version}: $result")
            }
        }
    }

    private companion object {
        const val INPUT_TEXT = "input_text"
        const val CHARACTER_COUNT = "character_count"
        const val VERSION_TEXT = "version_text"
    }
}
