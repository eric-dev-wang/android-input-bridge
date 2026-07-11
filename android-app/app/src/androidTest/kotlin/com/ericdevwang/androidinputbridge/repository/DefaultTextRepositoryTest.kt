package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.ericdevwang.androidinputbridge.model.MAX_TEXT_CODE_POINTS
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import junit.framework.TestCase.assertEquals

class DefaultTextRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun initializeRestoresTextVersionAndTimestamp() = runTest {
        val file = testFile()
        val firstStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val first = DefaultTextRepository(firstStore) { 100L }

        first.initialize()
        first.changeText("hello")

        val second = DefaultTextRepository(firstStore) { 200L }
        second.initialize()

        assertEquals(TextState("hello", 1L, 100L), second.state.value)
    }

    @Test
    fun overLimitTextDoesNotChangePersistedState() = runTest {
        val file = testFile()
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val repository = DefaultTextRepository(store) { 100L }

        repository.initialize()
        val result = repository.changeText("😀".repeat(MAX_TEXT_CODE_POINTS + 1))

        assertEquals(TextChangeResult.RejectedTooLong, result)
        assertEquals(TextState("", 0L, 100L), repository.state.value)
    }

    private fun testFile(): File =
        File(context.cacheDir, "input-bridge-${UUID.randomUUID()}.preferences_pb")
}
