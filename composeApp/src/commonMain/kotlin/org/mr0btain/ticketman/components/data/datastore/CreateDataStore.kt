package org.mr0btain.ticketman.components.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import okio.Path.Companion.toPath


expect fun createDataStore(): DataStore<Preferences>

private lateinit var dataStore: DataStore<Preferences>
@OptIn(InternalCoroutinesApi::class)
private val lock = SynchronizedObject()

// App directory and directory for file to save items in
private val appDir = FileKit.filesDir.path

val dataStoreFileName = "$appDir/config/app.preferences_pb"

@OptIn(InternalCoroutinesApi::class)
fun getDataStore(producePath: () -> String): DataStore<Preferences> =
    synchronized(lock) {
        if (::dataStore.isInitialized) {
            dataStore
        } else {
            PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() })
                .also { dataStore = it }
        }
    }

