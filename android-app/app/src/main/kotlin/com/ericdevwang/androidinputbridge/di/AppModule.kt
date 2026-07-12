package com.ericdevwang.androidinputbridge.di

import com.ericdevwang.androidinputbridge.http.HttpTextRepository
import com.ericdevwang.androidinputbridge.repository.DataStoreTextDataSource
import com.ericdevwang.androidinputbridge.repository.DefaultTextRepository
import com.ericdevwang.androidinputbridge.repository.TextDataSource
import com.ericdevwang.androidinputbridge.repository.TextRepository
import com.ericdevwang.androidinputbridge.server.InputHttpServer
import com.ericdevwang.androidinputbridge.ui.main.MainScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single<TextDataSource> { DataStoreTextDataSource(androidContext()) }
    single<TextRepository> { DefaultTextRepository(dataSource = get(), scope = get()) }
    single { HttpTextRepository(repository = get()) }
    single { InputHttpServer(repository = get()) }
    viewModel { MainScreenViewModel(repository = get()) }
}
