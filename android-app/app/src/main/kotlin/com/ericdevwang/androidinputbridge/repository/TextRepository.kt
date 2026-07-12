package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.flow.Flow

interface TextRepository {
    val state: Flow<TextState>
    suspend fun persist(state: TextState)
}
