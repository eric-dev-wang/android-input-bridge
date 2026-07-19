package com.ericdevwang.inputbridge

import android.app.Application
import com.ericdevwang.inputbridge.core.data.di.dataModule
import com.ericdevwang.inputbridge.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class InputBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@InputBridgeApplication)
            modules(dataModule, appModule)
        }
    }
}
