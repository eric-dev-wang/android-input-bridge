package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.storage.TEXT_KEY
import com.ericdevwang.androidinputbridge.storage.UPDATED_AT_KEY
import com.ericdevwang.androidinputbridge.storage.VERSION_KEY
import com.ericdevwang.androidinputbridge.storage.inputBridgeDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DefaultTextRepository(
    private val dataStore: DataStore<Preferences>,
) : TextRepository {
    constructor(context: Context) : this(context.inputBridgeDataStore)

    override val state: Flow<TextState> =
        dataStore.data
            .map { preferences -> preferences.toTextState() }
            .distinctUntilChanged()

    override suspend fun persist(state: TextState) {
        dataStore.edit { preferences ->
            state.writeTo(preferences)
        }
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
