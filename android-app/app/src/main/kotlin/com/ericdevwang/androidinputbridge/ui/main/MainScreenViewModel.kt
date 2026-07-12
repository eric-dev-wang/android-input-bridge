package com.ericdevwang.androidinputbridge.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainScreenViewModel(
    private val repository: TextRepository,
) : ViewModel() {
    private val mutationMutex = Mutex()
    private val persistenceMessage = MutableStateFlow<PersistenceMessage?>(null)

    val uiState: Flow<MainScreenUiState> =
        combine(repository.state, persistenceMessage) { state, message ->
            state.toUiState(persistenceMessage = message)
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(
                MainScreenUiState(
                    isLoading = false,
                    persistenceMessage = PersistenceMessage.InitializationFailed,
                ),
            )
        }

    fun onTextChanged(newText: String) {
        launchMutation {
            when (val result = repository.changeText(newText)) {
                is TextChangeResult.Accepted -> persistenceMessage.value = null
                TextChangeResult.RejectedTooLong -> Unit
            }
        }
    }

    fun onClear() {
        launchMutation {
            repository.clear()
            persistenceMessage.value = null
        }
    }

    private fun launchMutation(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                try {
                    action()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    persistenceMessage.value = PersistenceMessage.SaveFailed
                }
            }
        }
    }
}

enum class PersistenceMessage {
    InitializationFailed,
    SaveFailed,
}

data class MainScreenUiState(
    val isLoading: Boolean = true,
    val text: String = "",
    val version: Long = 0L,
    val characterCount: Int = 0,
    val persistenceMessage: PersistenceMessage? = null,
)

private fun TextState.toUiState(persistenceMessage: PersistenceMessage? = null): MainScreenUiState =
    MainScreenUiState(
        isLoading = false,
        text = text,
        version = version,
        characterCount = text.codePointCount(0, text.length),
        persistenceMessage = persistenceMessage,
    )
