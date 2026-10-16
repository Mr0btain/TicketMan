package org.mr0btain.ticketman.components.injection

import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.KeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import kotlin.test.*

class DataModuleTest {
    @Test
    fun localStorageIsASingleUsingTheProvidedStore() {
        val store = InMemoryKeyValueStore()
        // Swaps out the KSafe backed store so the platform module is never needed
        val koin = koinApplication {
            modules(dataModule, module { single<KeyValueStore> { store } })
        }.koin

        val localStorage = koin.get<LocalStorage>()
        localStorage.accessToken = "access"

        assertSame(localStorage, koin.get<LocalStorage>())
        assertEquals("access", store.values.values.single())
    }
}
