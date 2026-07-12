package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.storage.TEXT_KEY
import com.ericdevwang.androidinputbridge.storage.UPDATED_AT_KEY
import com.ericdevwang.androidinputbridge.storage.VERSION_KEY
import com.ericdevwang.androidinputbridge.storage.inputBridgeDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreTextDataSource(
    private val dataStore: DataStore<Preferences>,
) : TextDataSource {
    constructor(context: Context) : this(context.inputBridgeDataStore)

    override val state: Flow<TextState> =
        dataStore.data
            .map { preferences -> preferences.toTextState() }
            .distinctUntilChanged()

    override suspend fun saveIfNewer(state: TextState): Boolean {
        var saved = false
        dataStore.edit { preferences ->
            if (state.version > (preferences[VERSION_KEY] ?: 0L)) {
                state.writeTo(preferences)
                saved = true
            }
        }
        return saved
    }

    override suspend fun clearIfVersion(expectedVersion: Long, nowMillis: Long): ClearResult {
        var result: ClearResult? = null
        dataStore.edit { preferences ->
            val current = preferences.toTextState()
            if (current.version != expectedVersion) {
                result = ClearResult.VersionConflict(current.version)
                return@edit
            }

            val cleared = current.clear(nowMillis)
            if (cleared != current) cleared.writeTo(preferences)
            result = ClearResult.Cleared(
                clearedVersion = current.version,
                newVersion = cleared.version,
            )
        }
        return checkNotNull(result)
    }
}

private fun Preferences.toTextState(): TextState =
    TextState(
        text = this[TEXT_KEY] ?: "",
        version = this[VERSION_KEY] ?: 0L,
        updatedAt = this[UPDATED_AT_KEY] ?: 0L,
    )

private fun TextState.writeTo(preferences: MutablePreferences) {
    preferences[TEXT_KEY] = text
    preferences[VERSION_KEY] = version
    preferences[UPDATED_AT_KEY] = updatedAt
}
