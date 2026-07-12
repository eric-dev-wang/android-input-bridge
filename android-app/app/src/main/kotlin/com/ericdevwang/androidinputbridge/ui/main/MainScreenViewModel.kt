package com.ericdevwang.androidinputbridge.ui.main

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ericdevwang.androidinputbridge.model.TextChangeResult
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.TextRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainScreenViewModel(
    private val repository: TextRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    private val persistenceRequests = Channel<TextState>(capacity = Channel.CONFLATED)
    private var currentTextState = TextState.initial(0L)

    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch { loadInitialState() }
        viewModelScope.launch { persistQueuedStates() }
    }

    fun onTextChanged(value: TextFieldValue) {
        val currentContent = mutableUiState.value as? MainScreenUiState.Content ?: return

        if (value.text == currentTextState.text) {
            mutableUiState.value = currentContent.copy(textFieldValue = value)
            return
        }

        when (val result = currentTextState.changeText(value.text, clock())) {
            is TextChangeResult.Accepted -> {
                currentTextState = result.state
                mutableUiState.value = result.state.toContent(value)
                persistenceRequests.trySend(result.state)
            }

            TextChangeResult.RejectedTooLong -> Unit
        }
    }

    fun onClear() {
        val currentContent = mutableUiState.value as? MainScreenUiState.Content ?: return
        val cleared = currentTextState.clear(clock())
        if (cleared == currentTextState) {
            mutableUiState.value = currentContent.copy(
                textFieldValue = TextFieldValue("")
            )
            return
        }

        currentTextState = cleared
        val clearedValue = TextFieldValue(text = "", selection = TextRange.Zero)
        mutableUiState.value = cleared.toContent(clearedValue)
        persistenceRequests.trySend(cleared)
    }

    private suspend fun loadInitialState() {
        try {
            val state = repository.state.first()
            currentTextState = state
            val value = TextFieldValue(
                text = state.text,
                selection = TextRange(state.text.length),
            )
            mutableUiState.value = state.toContent(value)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableUiState.value = MainScreenUiState.InitializationError
        }
    }

    private suspend fun persistQueuedStates() {
        for (state in persistenceRequests) {
            try {
                repository.persist(state)
                if (currentTextState.version == state.version) {
                    updateContent { it.copy(persistenceMessage = null) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (currentTextState.version == state.version) {
                    updateContent { it.copy(persistenceMessage = PersistenceMessage.SaveFailed) }
                }
            }
        }
    }

    private fun updateContent(transform: (MainScreenUiState.Content) -> MainScreenUiState.Content) {
        val current = mutableUiState.value as? MainScreenUiState.Content ?: return
        mutableUiState.value = transform(current)
    }
}

enum class PersistenceMessage {
    InitializationFailed,
    SaveFailed,
}

sealed interface MainScreenUiState {
    data object Loading : MainScreenUiState

    data class Content(
        val textFieldValue: TextFieldValue,
        val version: Long,
        val characterCount: Int,
        val persistenceMessage: PersistenceMessage? = null,
    ) : MainScreenUiState

    data object InitializationError : MainScreenUiState
}

private fun TextState.toContent(
    textFieldValue: TextFieldValue,
    persistenceMessage: PersistenceMessage? = null,
): MainScreenUiState.Content =
    MainScreenUiState.Content(
        textFieldValue = textFieldValue,
        version = version,
        characterCount = text.codePointCount(0, text.length),
        persistenceMessage = persistenceMessage,
    )
