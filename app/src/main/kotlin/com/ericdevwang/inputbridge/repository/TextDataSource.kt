package com.ericdevwang.inputbridge.repository

import com.ericdevwang.inputbridge.model.TextState
import kotlinx.coroutines.flow.Flow

interface TextDataSource {
    val state: Flow<TextState>

    suspend fun saveIfNewer(state: TextState): Boolean
}
