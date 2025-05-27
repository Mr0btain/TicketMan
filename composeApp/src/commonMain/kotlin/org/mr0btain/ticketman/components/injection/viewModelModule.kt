package org.mr0btain.ticketman.components.injection

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModel
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.PreferenceStore
import org.mr0btain.ticketman.components.data.PreferenceStoreImpl
import org.mr0btain.ticketman.components.data.createDataStore
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel

val viewModelModule = module {
    // Main view model
    viewModelOf(::MainViewModel)

    viewModel {
        SettingsViewModel(
            preferenceStore = get(),
            dispatcher = Dispatchers.Default
        )
    }
}


