package com.ericdevwang.inputbridge.di

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.ericdevwang.inputbridge.repository.DataStoreTextDataSource
import com.ericdevwang.inputbridge.repository.DefaultTextRepository
import com.ericdevwang.inputbridge.repository.TextDataSource
import com.ericdevwang.inputbridge.repository.TextRepository
import com.ericdevwang.inputbridge.server.InputWebSocketServer
import com.ericdevwang.inputbridge.ui.main.MainScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val APP_VERSION_QUALIFIER = "appVersion"

val appModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single<TextDataSource> { DataStoreTextDataSource(androidContext()) }
    single<TextRepository> { DefaultTextRepository(dataSource = get(), scope = get()) }
    single<String>(named(APP_VERSION_QUALIFIER)) {
        (androidContext() as Application).readVersionName()
    }
    single {
        InputWebSocketServer(
            repository = get(),
            appVersion = get(named(APP_VERSION_QUALIFIER)),
        )
    }
    viewModel { MainScreenViewModel(repository = get()) }
}

private fun Application.readVersionName(): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName
        ?: error("Application version name is unavailable for $packageName")
}
