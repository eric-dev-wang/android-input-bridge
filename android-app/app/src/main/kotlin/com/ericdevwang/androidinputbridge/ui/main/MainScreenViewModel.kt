package com.ericdevwang.androidinputbridge.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainScreenViewModel(
    private val repository: TextRepository,
) : ViewModel() {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(MainScreenUiState())

    val uiState: StateFlow<MainScreenUiState> = mutableUiState.asStateFlow()

    private val repositoryStateJob = viewModelScope.launch {
        repository.state.collect { state ->
            if (!mutableUiState.value.isLoading) {
                mutableUiState.update { current ->
                    state.toUiState(persistenceMessage = current.persistenceMessage)
                }
            }
        }
    }

    val initializeJob: Job = viewModelScope.launch {
        try {
            repository.initialize()
            mutableUiState.value = repository.state.value.toUiState()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableUiState.value = MainScreenUiState(
                isLoading = false,
                persistenceMessage = INITIALIZATION_FAILURE_MESSAGE,
            )
        }
    }

    fun onTextChanged(newText: String) {
        launchMutation {
            when (val result = repository.changeText(newText)) {
                is TextChangeResult.Accepted -> {
                    mutableUiState.value = result.state.toUiState()
                }

                TextChangeResult.RejectedTooLong -> Unit
            }
        }
    }

    fun onClear() {
        launchMutation {
            mutableUiState.value = repository.clear().toUiState()
        }
    }

    private fun launchMutation(action: suspend () -> Unit) {
        if (mutableUiState.value.isLoading) return

        viewModelScope.launch {
            mutationMutex.withLock {
                if (mutableUiState.value.isLoading) return@withLock

                try {
                    action()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutableUiState.value = repository.state.value.toUiState(
                        persistenceMessage = PERSISTENCE_FAILURE_MESSAGE,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        repositoryStateJob.cancel()
        super.onCleared()
    }

    private companion object {
        const val INITIALIZATION_FAILURE_MESSAGE = "Saved text could not be loaded."
        const val PERSISTENCE_FAILURE_MESSAGE = "Text could not be saved."
    }
}

data class MainScreenUiState(
    val isLoading: Boolean = true,
    val text: String = "",
    val version: Long = 0L,
    val characterCount: Int = 0,
    val persistenceMessage: String? = null,
)

private fun TextState.toUiState(persistenceMessage: String? = null): MainScreenUiState =
    MainScreenUiState(
        isLoading = false,
        text = text,
        version = version,
        characterCount = text.codePointCount(0, text.length),
        persistenceMessage = persistenceMessage,
    )
