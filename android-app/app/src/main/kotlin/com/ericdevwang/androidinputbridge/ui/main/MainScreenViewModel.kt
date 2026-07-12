package com.ericdevwang.androidinputbridge.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainScreenViewModel(
    private val repository: TextRepository,
) : ViewModel() {
    private val mutationMutex = Mutex()
    private val persistenceMessage = MutableStateFlow<PersistenceMessage?>(null)

    val uiState: StateFlow<MainScreenUiState> =
        combine(repository.state, persistenceMessage) { state, message ->
            state.toUiState(message)
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(
                MainScreenUiState.InitializationError,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainScreenUiState.Loading,
        )

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

sealed interface MainScreenUiState {
    data object Loading : MainScreenUiState

    data class Content(
        val text: String,
        val version: Long,
        val characterCount: Int,
        val persistenceMessage: PersistenceMessage? = null,
    ) : MainScreenUiState

    data object InitializationError : MainScreenUiState
}

private fun TextState.toUiState(persistenceMessage: PersistenceMessage?): MainScreenUiState =
    MainScreenUiState.Content(
        text = text,
        version = version,
        characterCount = text.codePointCount(0, text.length),
        persistenceMessage = persistenceMessage,
    )
