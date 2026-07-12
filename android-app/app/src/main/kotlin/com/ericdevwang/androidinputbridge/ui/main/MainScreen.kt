package com.ericdevwang.androidinputbridge.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ericdevwang.androidinputbridge.R
import com.ericdevwang.androidinputbridge.theme.AndroidInputBridgeTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel: MainScreenViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainScreenContent(
        uiState = uiState,
        onTextChanged = viewModel::onTextChanged,
        onClear = viewModel::onClear,
        modifier = modifier,
    )
}

@Composable
fun MainScreenContent(
    uiState: MainScreenUiState,
    onTextChanged: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldValue: TextFieldValue
    val version: Long
    val characterCount: Int
    val isLoading: Boolean
    val isEnabled: Boolean
    val persistenceMessage: PersistenceMessage?
    when (uiState) {
        MainScreenUiState.Loading -> {
            textFieldValue = TextFieldValue()
            version = 0L
            characterCount = 0
            isLoading = true
            isEnabled = false
            persistenceMessage = null
        }

        is MainScreenUiState.Content -> {
            textFieldValue = uiState.textFieldValue
            version = uiState.version
            characterCount = uiState.characterCount
            isLoading = false
            isEnabled = true
            persistenceMessage = uiState.persistenceMessage
        }

        MainScreenUiState.InitializationError -> {
            textFieldValue = TextFieldValue()
            version = 0L
            characterCount = 0
            isLoading = false
            isEnabled = false
            persistenceMessage = PersistenceMessage.InitializationFailed
        }
    }

    Column(
        modifier = modifier.safeDrawingPadding().padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.input_bridge_title))

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = onTextChanged,
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth().testTag("input_text"),
            minLines = 8,
            maxLines = 16,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
            ),
        )

        Text(
            text = stringResource(R.string.characters_count, characterCount),
            modifier = Modifier.testTag("character_count"),
        )
        Text(
            text = stringResource(R.string.version_format, version),
            modifier = Modifier.testTag("version_text"),
        )

        if (isLoading) CircularProgressIndicator()

        persistenceMessage?.let { message ->
            val messageRes = when (message) {
                PersistenceMessage.InitializationFailed -> R.string.initialization_error
                PersistenceMessage.SaveFailed -> R.string.persistence_error
            }
            Text(
                text = stringResource(messageRes),
                modifier = Modifier.testTag("persistence_error"),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onClear,
            enabled = isEnabled,
            modifier = Modifier.testTag("clear_button"),
        ) {
            Text(text = stringResource(R.string.clear))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    AndroidInputBridgeTheme {
        MainScreenContent(
            uiState = MainScreenUiState.Content(
                textFieldValue = TextFieldValue(),
                version = 0L,
                characterCount = 0,
            ),
            onTextChanged = {},
            onClear = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
