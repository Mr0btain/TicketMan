package org.mr0btain.ticketman.components.injection

import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.PreferenceStore
import org.mr0btain.ticketman.components.data.PreferenceStoreImpl
import org.mr0btain.ticketman.components.data.createDataStore

val dataModule = module {
    //data store
    single<PreferenceStore> {
        PreferenceStoreImpl(createDataStore())
    }
}