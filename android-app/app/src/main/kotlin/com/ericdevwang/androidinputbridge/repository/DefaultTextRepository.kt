package com.ericdevwang.androidinputbridge.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.storage.TEXT_KEY
import com.ericdevwang.androidinputbridge.storage.UPDATED_AT_KEY
import com.ericdevwang.androidinputbridge.storage.VERSION_KEY
import com.ericdevwang.androidinputbridge.storage.inputBridgeDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    override val state: Flow<TextState> =
        dataStore.data
            .map { preferences -> preferences.toTextState() }
            .distinctUntilChanged()

    override suspend fun changeText(newText: String): TextChangeResult =
        mutex.withLock {
            var result: TextChangeResult? = null
            dataStore.edit { preferences ->
                val current = preferences.toTextState()
                val changed = current.changeText(newText, clock())
                result = changed
                if (changed is TextChangeResult.Accepted && changed.state != current) {
                    changed.state.writeTo(preferences)
                }
            }
            return@withLock result
                ?: error("DataStore edit did not produce a text change result")
        }

    override suspend fun clear(): TextState =
        mutex.withLock {
            var cleared: TextState? = null
            dataStore.edit { preferences ->
                val current = preferences.toTextState()
                val next = current.clear(clock())
                cleared = next
                if (next != current) {
                    next.writeTo(preferences)
                }
            }
            return@withLock cleared
                ?: error("DataStore edit did not produce a cleared text state")
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
