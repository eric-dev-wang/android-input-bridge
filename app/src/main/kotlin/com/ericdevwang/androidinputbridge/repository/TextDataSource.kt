package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.flow.Flow

interface TextDataSource {
    val state: Flow<TextState>

    suspend fun saveIfNewer(state: TextState): Boolean
}
