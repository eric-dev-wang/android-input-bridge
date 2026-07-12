package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.ericdevwang.androidinputbridge.model.MAX_TEXT_CODE_POINTS
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTextRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun initializeRestoresTextVersionAndTimestamp() = runTest {
        val file = testFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(
            scope = firstScope,
            produceFile = { file },
        )
        val first = DefaultTextRepository(firstStore) { 100L }

        first.initialize()
        first.changeText("hello")
        advanceUntilIdle()
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondStore = PreferenceDataStoreFactory.create(
            scope = secondScope,
            produceFile = { file },
        )
        val second = DefaultTextRepository(secondStore) { 200L }
        second.initialize()
        secondScope.cancel()

        assertEquals(TextState("hello", 1L, 101L), second.state.value)
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
        advanceUntilIdle()

        assertEquals(TextChangeResult.RejectedTooLong, result)
        assertEquals(TextState("", 0L, 100L), repository.state.value)

        val restored = DefaultTextRepository(store) { 200L }
        restored.initialize()
        assertEquals(TextState("", 0L, 100L), restored.state.value)
    }

    @Test
    fun writeFailurePreservesInMemoryState() = runTest {
        val store = FailingDataStore()
        val repository = DefaultTextRepository(store) { 100L }

        repository.initialize()
        store.failWrites = true

        try {
            repository.changeText("keep")
            fail("Expected persistence failure")
        } catch (_: IOException) {
            // Expected: the in-memory state must remain updated.
        }
        assertEquals(TextState("keep", 1L, 101L), repository.state.value)
    }

    private fun testFile(): File =
        File(context.cacheDir, "input-bridge-${UUID.randomUUID()}.preferences_pb")
}

private class FailingDataStore : DataStore<Preferences> {
    private var preferences: Preferences = emptyPreferences()
    var failWrites: Boolean = false

    override val data: Flow<Preferences> = flowOf(preferences)

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences {
        if (failWrites) throw IOException("write failed")
        preferences = transform(preferences)
        return preferences
    }
}
