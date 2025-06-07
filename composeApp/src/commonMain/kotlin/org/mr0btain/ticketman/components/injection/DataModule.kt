package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.components.data.database.SqliteDb

val dataModule = module {
    //example
    /*
    single<PreferenceStore> {
        PreferenceStoreImpl(createDataStore())
    }
     */

    single<FileManager> {
        FileManager()
    }

    single<SqliteDb> {
        SqliteDb()
    }

    single<SnackbarHostState> {
        SnackbarHostState()
    }
}