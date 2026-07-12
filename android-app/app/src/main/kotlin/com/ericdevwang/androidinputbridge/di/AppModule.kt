package com.ericdevwang.androidinputbridge.di

import com.ericdevwang.androidinputbridge.persistence.TextPersistenceCoordinator
import com.ericdevwang.androidinputbridge.repository.DefaultTextRepository
import com.ericdevwang.androidinputbridge.repository.TextRepository
import com.ericdevwang.androidinputbridge.ui.main.MainScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single<TextRepository> { DefaultTextRepository(androidContext()) }
    single { TextPersistenceCoordinator(get(), get()) }
    viewModel { MainScreenViewModel(repository = get(), persistenceCoordinator = get()) }
}
