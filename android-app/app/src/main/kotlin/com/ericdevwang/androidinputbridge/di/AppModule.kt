package com.ericdevwang.androidinputbridge.di

import com.ericdevwang.androidinputbridge.repository.DefaultTextRepository
import com.ericdevwang.androidinputbridge.repository.TextRepository
import com.ericdevwang.androidinputbridge.ui.main.MainScreenViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<TextRepository> { DefaultTextRepository(androidContext()) }
    viewModel { MainScreenViewModel(get()) }
}
