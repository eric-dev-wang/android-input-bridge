package com.ericdevwang.inputbridge.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ericdevwang.inputbridge.core.datastore.inputBridgeDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val datastoreModule = module {
    single<DataStore<Preferences>> {
        androidContext().inputBridgeDataStore
    }
}
