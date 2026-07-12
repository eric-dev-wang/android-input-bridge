package com.ericdevwang.androidinputbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ericdevwang.androidinputbridge.R
import com.ericdevwang.androidinputbridge.theme.AndroidInputBridgeTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      AndroidInputBridgeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = colorResource(R.color.app_background)) {
          MainNavigation()
        }
      }
    }
  }
}
