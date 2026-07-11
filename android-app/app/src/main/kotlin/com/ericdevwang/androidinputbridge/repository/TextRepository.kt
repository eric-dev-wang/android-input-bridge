package com.ericdevwang.androidinputbridge.repository

import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import kotlinx.coroutines.flow.StateFlow

interface TextRepository {
    val state: StateFlow<TextState>
    suspend fun initialize()
    suspend fun changeText(newText: String): TextChangeResult
    suspend fun clear(): TextState
}
