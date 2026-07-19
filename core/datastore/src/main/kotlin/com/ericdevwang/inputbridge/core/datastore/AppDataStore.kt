package com.ericdevwang.inputbridge.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.inputBridgeDataStore by preferencesDataStore(name = "input_bridge")

val TEXT_KEY = stringPreferencesKey("text")
val VERSION_KEY = longPreferencesKey("version")
val UPDATED_AT_KEY = longPreferencesKey("updated_at")
