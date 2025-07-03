package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import org.mr0btain.ticketman.components.database.ClientDatabase
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.util.AppDir

/**
 * Simple function to initialise view model for things we cant do in init
 */
fun initMainViewModel(
    appDir: AppDir,
    snackBarHost: SnackbarHostState
): MainViewModel {
    return MainViewModel(appDir, snackBarHost).also { viewModel -> viewModel.init() }
}