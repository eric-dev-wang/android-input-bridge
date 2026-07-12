package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.ericdevwang.androidinputbridge.model.TextState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreTextDataSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun saveIfNewerAcceptsNewerStateAndRejectsStaleState() = runTest {
        val dataSource = DataStoreTextDataSource(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = ::testFile,
            ),
        )

        assertTrue(dataSource.saveIfNewer(TextState("new", 2L, 20L)))
        assertFalse(dataSource.saveIfNewer(TextState("stale", 1L, 10L)))
        assertEquals(TextState("new", 2L, 20L), dataSource.state.first())
    }

    @Test
    fun clearIfVersionClearsMatchingVersionAndIncrementsVersion() = runTest {
        val dataSource = DataStoreTextDataSource(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = ::testFile,
            ),
        )
        dataSource.saveIfNewer(TextState("keep", 3L, 30L))

        assertEquals(
            ClearResult.Cleared(clearedVersion = 3L, newVersion = 4L),
            dataSource.clearIfVersion(expectedVersion = 3L, nowMillis = 40L),
        )
        assertEquals(TextState("", 4L, 40L), dataSource.state.first())
    }

    @Test
    fun clearIfVersionReturnsConflictAndPreservesStateForStaleVersion() = runTest {
        val dataSource = DataStoreTextDataSource(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = ::testFile,
            ),
        )
        dataSource.saveIfNewer(TextState("current", 3L, 30L))

        assertEquals(
            ClearResult.VersionConflict(currentVersion = 3L),
            dataSource.clearIfVersion(expectedVersion = 2L, nowMillis = 40L),
        )
        assertEquals(TextState("current", 3L, 30L), dataSource.state.first())
    }

    @Test
    fun clearIfVersionIsNoOpForEmptyState() = runTest {
        val dataSource = DataStoreTextDataSource(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = ::testFile,
            ),
        )

        assertEquals(
            ClearResult.Cleared(clearedVersion = 0L, newVersion = 0L),
            dataSource.clearIfVersion(expectedVersion = 0L, nowMillis = 40L),
        )
        assertEquals(TextState("", 0L, 0L), dataSource.state.first())
    }

    private fun testFile(): File =
        File(context.cacheDir, "input-bridge-data-source-${UUID.randomUUID()}.preferences_pb")
}
