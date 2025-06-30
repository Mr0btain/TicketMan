package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import androidx.compose.material3.SnackbarHostState
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.components.database.ClientDatabase
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

/**
 * Simple function to initialise view model for things we cant do in init
 */
fun initMainViewModel(
    fileManager: FileManager,
    clientDatabase: ClientDatabase,
    snackBarHost: SnackbarHostState
): MainViewModel {
    return MainViewModel(fileManager, clientDatabase, snackBarHost).also { viewModel -> viewModel.init() }
}