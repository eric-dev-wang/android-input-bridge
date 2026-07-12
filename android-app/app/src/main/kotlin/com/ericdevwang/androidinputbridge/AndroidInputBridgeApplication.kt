package com.ericdevwang.androidinputbridge

import android.app.Application
import com.ericdevwang.androidinputbridge.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AndroidInputBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@AndroidInputBridgeApplication)
            modules(appModule)
        }
    }
}
