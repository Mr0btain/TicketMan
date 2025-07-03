package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import org.koin.dsl.module
import org.mr0btain.ticketman.components.database.ClientDatabase
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.util.AppDir

val dataModule = module {
    //example
    /*
    single<PreferenceStore> {
        PreferenceStoreImpl(createDataStore())
    }
     */

    single<AppDir> {
        initAppDir()
    }

    /*
    single<ClientDatabase> {
        configureDatabase(get())
    }
     */

    single<SnackbarHostState> {
        SnackbarHostState()
    }
}