package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.components.data.datastore.createDataStore
import org.mr0btain.ticketman.components.database.ClientDatabase
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.network.ServerDatabaseClient
import org.mr0btain.ticketman.components.network.createHttpClient
import org.mr0btain.ticketman.components.network.getHttpEngine

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

    single<ClientDatabase> {
        configureDatabase()
    }

    single<SnackbarHostState> {
        SnackbarHostState()
    }

    single<DataStore<Preferences>> {
        createDataStore()
    }

    single<ServerDatabaseClient> {
        ServerDatabaseClient(createHttpClient(getHttpEngine()))
    }
}