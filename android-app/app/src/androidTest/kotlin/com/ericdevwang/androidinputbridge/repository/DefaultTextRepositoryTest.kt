package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.ericdevwang.androidinputbridge.model.TextState
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTextRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyDataStoreEmitsDefaultTextState() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = ::testFile,
        )
        val repository = DefaultTextRepository(store)

        assertEquals(TextState("", 0L, 0L), repository.state.first())
    }

    @Test
    fun stateFlowRestoresPersistedTextVersionAndTimestamp() = runTest {
        val file = testFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(
            scope = firstScope,
            produceFile = { file },
        )
        val first = DefaultTextRepository(firstStore)

        first.persist(TextState("hello", 1L, 100L))
        advanceUntilIdle()
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondStore = PreferenceDataStoreFactory.create(
            scope = secondScope,
            produceFile = { file },
        )
        val second = DefaultTextRepository(secondStore)

        assertEquals(TextState("hello", 1L, 100L), second.state.first())
        secondScope.cancel()
    }

    @Test
    fun writeFailureDoesNotChangePersistedState() = runTest {
        val store = FailingDataStore()
        val repository = DefaultTextRepository(store)
        store.failWrites = true

        try {
            repository.persist(TextState("keep", 1L, 100L))
            fail("Expected persistence failure")
        } catch (_: IOException) {
            // Expected: the durable state must remain unchanged.
        }

        assertEquals(TextState("", 0L, 0L), repository.state.first())
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
