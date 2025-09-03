package org.mr0btain.ticketman.components.injection

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

val viewModelModule = module {
    // Main view model
    viewModel { initMainViewModel(get()) }
}


