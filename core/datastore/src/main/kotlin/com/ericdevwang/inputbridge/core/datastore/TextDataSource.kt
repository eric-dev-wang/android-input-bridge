package com.ericdevwang.inputbridge.core.datastore

import kotlinx.coroutines.flow.Flow

interface TextDataSource {
    val state: Flow<PersistedTextState>

    suspend fun saveIfNewer(state: PersistedTextState): Boolean
}
