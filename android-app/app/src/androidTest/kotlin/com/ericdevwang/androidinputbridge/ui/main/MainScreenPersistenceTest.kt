package com.ericdevwang.androidinputbridge.ui.main

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.ericdevwang.androidinputbridge.MainActivity
import com.ericdevwang.androidinputbridge.storage.inputBridgeDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

class MainScreenPersistenceTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun clearPersistedState() {
        clearDataStore()
    }

    @Test
    fun multilineUnicodeTextRestoresAfterActivityRecreation() {
        clearDataStore()
        recreateActivity()

        val text = "Hello\n你好 👋🏽\n😀"
        composeTestRule.onNodeWithTag(INPUT_TEXT).performTextInput(text)
        composeTestRule.waitForIdle()

        assertRestoredState(text)

        recreateActivity()

        assertRestoredState(text)
    }

    private fun recreateActivity() {
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    private fun assertRestoredState(text: String) {
        composeTestRule.onNodeWithTag(INPUT_TEXT).assertTextEquals(text)
        composeTestRule.onNodeWithTag(VERSION_TEXT).assertTextEquals("Version: 1")
        composeTestRule.onNodeWithTag(CHARACTER_COUNT).assertTextEquals("Characters: 13")
    }

    private fun clearDataStore() {
        runBlocking {
            InstrumentationRegistry.getInstrumentation().targetContext.inputBridgeDataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    private companion object {
        const val INPUT_TEXT = "input_text"
        const val CHARACTER_COUNT = "character_count"
        const val VERSION_TEXT = "version_text"
    }
}
