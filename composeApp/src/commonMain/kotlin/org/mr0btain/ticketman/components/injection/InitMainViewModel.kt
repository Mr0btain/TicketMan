package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.ui.MainViewModel

/**
 * Simple function to initialise view model for things we cant do in init
 */
fun initMainViewModel(
    snackBarHost: SnackbarHostState,
    localStorage: LocalStorage
): MainViewModel {
    return MainViewModel(snackBarHost, localStorage).also { viewModel -> viewModel.init() }
}