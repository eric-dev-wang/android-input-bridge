package com.ericdevwang.inputbridge.core.data.repository

import com.ericdevwang.inputbridge.core.data.model.TextState
import kotlinx.coroutines.flow.Flow

interface TextDataSource {
    val state: Flow<TextState>

    suspend fun saveIfNewer(state: TextState): Boolean
}
