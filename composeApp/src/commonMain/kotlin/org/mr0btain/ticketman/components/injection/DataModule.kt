package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.local.KSafeKeyValueStore
import org.mr0btain.ticketman.components.data.local.KeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
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

    single<KeyValueStore> {
        KSafeKeyValueStore(get())
    }

    single<LocalStorage> {
        LocalStorage(get())
    }

    single<SnackbarHostState> {
        SnackbarHostState()
    }
}