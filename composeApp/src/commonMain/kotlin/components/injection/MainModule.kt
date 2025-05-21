package components.injection

import components.MainViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import screens.settings.components.SettingsViewModel

val viewModelModule = module {
    // Main view model
    viewModelOf(::MainViewModel)

    // Settings view model
    viewModelOf(::SettingsViewModel)
}