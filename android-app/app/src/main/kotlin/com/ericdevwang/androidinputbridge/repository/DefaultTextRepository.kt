package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.storage.TEXT_KEY
import com.ericdevwang.androidinputbridge.storage.UPDATED_AT_KEY
import com.ericdevwang.androidinputbridge.storage.VERSION_KEY
import com.ericdevwang.androidinputbridge.storage.inputBridgeDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultTextRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) : TextRepository {
    constructor(
        context: Context,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(context.inputBridgeDataStore, clock)

    private val mutex = Mutex()
    private val _state = MutableStateFlow(TextState.initial(clock()))

    override val state: StateFlow<TextState> = _state.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            val preferences = dataStore.data.first()
            val hasAllValues =
                preferences[TEXT_KEY] != null &&
                    preferences[VERSION_KEY] != null &&
                    preferences[UPDATED_AT_KEY] != null
            val restored = TextState(
                text = preferences[TEXT_KEY].orEmpty(),
                version = preferences[VERSION_KEY] ?: 0L,
                updatedAt = preferences[UPDATED_AT_KEY] ?: clock(),
            )
            _state.value = restored
            if (!hasAllValues) persist(restored)
        }
    }

    override suspend fun changeText(newText: String): TextChangeResult =
        mutex.withLock {
            val result = _state.value.changeText(newText, clock())
            if (result is TextChangeResult.Accepted && result.state != _state.value) {
                _state.value = result.state
                persist(result.state)
            }
            result
        }

    override suspend fun clear(): TextState =
        mutex.withLock {
            val cleared = _state.value.clear(clock())
            if (cleared != _state.value) {
                _state.value = cleared
                persist(cleared)
            }
            cleared
        }

    private suspend fun persist(state: TextState) {
        dataStore.edit { preferences ->
            preferences[TEXT_KEY] = state.text
            preferences[VERSION_KEY] = state.version
            preferences[UPDATED_AT_KEY] = state.updatedAt
        }
    }
}
