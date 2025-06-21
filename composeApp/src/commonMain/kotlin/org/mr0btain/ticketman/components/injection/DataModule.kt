package org.mr0btain.ticketman.components.injection

import androidx.compose.material3.SnackbarHostState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.database.LocalAppDatabase
import org.mr0btain.ticketman.components.data.datastore.createDataStore
import org.mr0btain.ticketman.components.network.DatabaseClient
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

    single<LocalAppDatabase> {
        LocalAppDatabase(FileKit.filesDir.path)
    }

    single<SnackbarHostState> {
        SnackbarHostState()
    }

    single<DataStore<Preferences>> {
        createDataStore()
    }

    single<DatabaseClient> {
        DatabaseClient(createHttpClient(getHttpEngine()))
    }
}