package com.ericdevwang.androidinputbridge.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigation3.runtime.NavKey
import com.ericdevwang.androidinputbridge.repository.DefaultTextRepository
import com.ericdevwang.androidinputbridge.theme.AndroidInputBridgeTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel? = null,
) {
  val context = LocalContext.current
  val mainScreenViewModel = viewModel ?: composeViewModel {
    MainScreenViewModel(DefaultTextRepository(context.applicationContext))
  }
  val state by mainScreenViewModel.uiState.collectAsStateWithLifecycle()
  if (!state.isLoading) {
    MainScreen(data = listOf(state.text), modifier = modifier)
  }
}

@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Column(modifier) { data.forEach { Greeting(it) } }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  AndroidInputBridgeTheme { MainScreen(listOf("Android")) }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun MainScreenPortraitPreview() {
  AndroidInputBridgeTheme { MainScreen(listOf("Android")) }
}
