package org.mr0btain.ticketman.components.injection

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel

val viewModelModule = module {
    // Main view model
    //viewModelOf(::MainViewModel)
    //viewModelOf(::SettingsViewModel)

    viewModel {
        SettingsViewModel(
            dataStore = get()
        )
    }

    viewModel { MainViewModel(
        fileManager = get(),
        localDatabase = get(),
        dataStore = get(),
        databaseClient = get(),
        snackBarHost = get()
    ) }
}


