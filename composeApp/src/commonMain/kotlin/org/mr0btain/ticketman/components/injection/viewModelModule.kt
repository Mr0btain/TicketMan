package org.mr0btain.ticketman.components.injection

import org.mr0btain.ticketman.components.MainViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel

val viewModelModule = module {
    // Main view model
    viewModelOf(::MainViewModel)

    viewModelOf(::SettingsViewModel)
}