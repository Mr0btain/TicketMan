package org.mr0btain.ticketman.components.injection

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    // Main view model
    viewModel {
        initMainViewModel(
            appDir = get(),
            snackBarHost = get()
        )
    }
}


