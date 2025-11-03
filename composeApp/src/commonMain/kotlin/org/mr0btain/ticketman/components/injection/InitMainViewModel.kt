package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

/**
 * Simple function to initialise view model for things we cant do in init
 */
fun initMainViewModel(
    snackBarHost: SnackbarHostState
): MainViewModel {
    return MainViewModel(snackBarHost).also { viewModel -> viewModel.init() }
}