package org.mr0btain.ticketman.components.injection

import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.FileManager

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
}