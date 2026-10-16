package org.mr0btain.ticketman.components.data.local

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocalStorageTest {
    private val store = InMemoryKeyValueStore()
    private val localStorage = LocalStorage(store)

    @Test
    fun tokensDefaultToEmpty() {
        assertEquals("", localStorage.accessToken)
        assertEquals("", localStorage.refreshToken)
    }

    @Test
    fun tokensRoundTrip() {
        localStorage.accessToken = "access"
        localStorage.refreshToken = "refresh"

        assertEquals("access", localStorage.accessToken)
        assertEquals("refresh", localStorage.refreshToken)
    }

    @Test
    fun setOverwritesPreviousToken() {
        localStorage.accessToken = "old"
        localStorage.accessToken = "new"

        localStorage.refreshToken = "old"
        localStorage.accessToken = "new"

        assertEquals("new", localStorage.accessToken)
        assertEquals("new", localStorage.refreshToken)
    }

    @Test
    fun accessAndRefreshUseSeparateKeys() {
        localStorage.accessToken = "access"

        assertEquals("", localStorage.refreshToken)
        assertEquals(1, store.values.size)
    }

    @Test
    fun tokensPersistAcrossInstancesSharingAStore() {
        localStorage.refreshToken = "refresh"

        // Simulates the app restarting with the same backing store
        assertEquals("refresh", LocalStorage(store).refreshToken)
    }

    @Test
    fun clearSessionRemovesTokens() = runTest {
        localStorage.accessToken = "access"
        localStorage.refreshToken = "refresh"

        localStorage.clearSession()

        assertEquals("", localStorage.accessToken)
        assertEquals("", localStorage.refreshToken)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun clearSessionOnlyRemovesTokenKeys() = runTest {
        store.putString("other", "value")
        localStorage.accessToken = "access"
        localStorage.refreshToken = "refresh"

        localStorage.clearSession()

        assertEquals(mapOf("other" to "value"), store.values)
    }
}
