package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.flow.Flow

interface TextRepository {
    val state: Flow<TextState>
    suspend fun save(state: TextState): PersistenceResult
    suspend fun clear(expectedVersion: Long): ClearResult
}

sealed interface PersistenceResult {
    data class Succeeded(val version: Long) : PersistenceResult

    data class Failed(val version: Long) : PersistenceResult

    data class Superseded(val version: Long) : PersistenceResult
}

sealed interface ClearResult {
    data class Cleared(
        val clearedVersion: Long,
        val newVersion: Long,
    ) : ClearResult

    data class VersionConflict(val currentVersion: Long) : ClearResult
}
