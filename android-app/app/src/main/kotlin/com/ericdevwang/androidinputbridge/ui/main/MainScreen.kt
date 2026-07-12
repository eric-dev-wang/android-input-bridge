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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(
        initialValue = MainScreenUiState(),
    )

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
    onTextChanged: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.safeDrawingPadding().padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.input_bridge_title))

        OutlinedTextField(
            value = uiState.text,
            onValueChange = onTextChanged,
            enabled = !uiState.isLoading,
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
            text = stringResource(R.string.characters_count, uiState.characterCount),
            modifier = Modifier.testTag("character_count"),
        )
        Text(
            text = stringResource(R.string.version_format, uiState.version),
            modifier = Modifier.testTag("version_text"),
        )

        if (uiState.isLoading) CircularProgressIndicator()

        uiState.persistenceMessage?.let { message ->
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
            enabled = !uiState.isLoading,
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
            uiState = MainScreenUiState(isLoading = false),
            onTextChanged = {},
            onClear = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
