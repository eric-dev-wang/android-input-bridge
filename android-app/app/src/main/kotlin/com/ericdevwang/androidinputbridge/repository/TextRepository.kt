package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.flow.Flow

interface TextRepository {
    val state: Flow<TextState>
    suspend fun save(state: TextState): PersistenceResult
}

sealed interface PersistenceResult {
    data class Succeeded(val version: Long) : PersistenceResult

    data class Failed(val version: Long) : PersistenceResult

    data class Superseded(val version: Long) : PersistenceResult
}
