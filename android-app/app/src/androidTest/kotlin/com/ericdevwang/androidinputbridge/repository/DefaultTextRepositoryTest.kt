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
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTextRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyDataStoreEmitsDefaultTextState() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = ::testFile,
        )
        val repositoryScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val repository = DefaultTextRepository(DataStoreTextDataSource(store), repositoryScope)

        assertEquals(TextState("", 0L, 0L), repository.state.first())
        repositoryScope.cancel()
    }

    @Test
    fun stateFlowRestoresPersistedTextVersionAndTimestamp() = runTest {
        val file = testFile()
        val firstDataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(
            scope = firstDataStoreScope,
            produceFile = { file },
        )
        val firstRepositoryScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val first = DefaultTextRepository(DataStoreTextDataSource(firstStore), firstRepositoryScope)

        val save = async { first.save(TextState("hello", 1L, 100L)) }
        advanceUntilIdle()
        assertEquals(PersistenceResult.Succeeded(1L), save.await())
        firstRepositoryScope.cancel()
        firstDataStoreScope.cancel()

        val secondDataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondStore = PreferenceDataStoreFactory.create(
            scope = secondDataStoreScope,
            produceFile = { file },
        )
        val secondRepositoryScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val second = DefaultTextRepository(DataStoreTextDataSource(secondStore), secondRepositoryScope)

        assertEquals(TextState("hello", 1L, 100L), second.state.first())
        secondRepositoryScope.cancel()
        secondDataStoreScope.cancel()
    }

    @Test
    fun writeFailureReturnsFailureAndDoesNotChangePersistedState() = runTest {
        val store = FailingDataStore()
        val repositoryScope = CoroutineScope(
            SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        val repository = DefaultTextRepository(DataStoreTextDataSource(store), repositoryScope)
        store.failWrites = true

        assertEquals(
            PersistenceResult.Failed(1L),
            repository.save(TextState("keep", 1L, 100L)),
        )
        assertEquals(TextState("", 0L, 0L), repository.state.first())
        repositoryScope.cancel()
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
