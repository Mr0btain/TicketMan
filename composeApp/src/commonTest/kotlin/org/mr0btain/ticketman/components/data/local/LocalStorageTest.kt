package org.mr0btain.ticketman.components.data.local

import kotlinx.coroutines.test.runTest
import org.mr0btain.ticketman.data.station.StationCredentials
import kotlin.test.*
import kotlin.uuid.Uuid

class LocalStorageTest {
    private val store = InMemoryKeyValueStore()
    private val localStorage = LocalStorage(store)

    @Test
    fun savedPasswordKeepsItsSpaces() {
        localStorage.password = " pass word "

        assertEquals(" pass word ", LocalStorage(store).password)
    }

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
        localStorage.refreshToken = "new"

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

    @Test
    fun settingsDefaultWhenMissing() {
        assertEquals(LocalStorage.DEFAULT_REMEMBER_LOGIN, localStorage.rememberLogin)
        assertEquals(LocalStorage.DEFAULT_TAB, localStorage.defaultTab)
        assertEquals(LocalStorage.DEFAULT_PAGE_SIZE, localStorage.pageSize)
        assertEquals(LocalStorage.DEFAULT_SEARCH_LIMIT, localStorage.searchLimit)
        assertEquals(LocalStorage.DEFAULT_CACHE_SIZE, localStorage.cacheSize)
        assertEquals(UpdateInterval.SECONDS_30, localStorage.updateInterval)
    }

    @Test
    fun settingsPersistAcrossInstances() {
        localStorage.rememberLogin = false
        localStorage.defaultTab = 2
        localStorage.pageSize = 50
        localStorage.searchLimit = 10
        localStorage.cacheSize = 75
        localStorage.updateInterval = UpdateInterval.MINUTES_5

        val reloaded = LocalStorage(store)

        assertFalse(reloaded.rememberLogin)
        assertEquals(2, reloaded.defaultTab)
        assertEquals(50, reloaded.pageSize)
        assertEquals(10, reloaded.searchLimit)
        assertEquals(75, reloaded.cacheSize)
        assertEquals(UpdateInterval.MINUTES_5, reloaded.updateInterval)
    }

    @Test
    fun invalidStoredSettingsFallBackToDefaults() {
        store.putString("remember_login", "maybe")
        store.putString("page_size", "lots")
        store.putString("search_limit", "")
        store.putString("update_interval", "SOMETIMES")

        val reloaded = LocalStorage(store)

        assertEquals(LocalStorage.DEFAULT_REMEMBER_LOGIN, reloaded.rememberLogin)
        assertEquals(LocalStorage.DEFAULT_PAGE_SIZE, reloaded.pageSize)
        assertEquals(LocalStorage.DEFAULT_SEARCH_LIMIT, reloaded.searchLimit)
        assertEquals(UpdateInterval.DEFAULT, reloaded.updateInterval)
    }

    @Test
    fun clearCredentialsOnlyRemovesCredentialKeys() = runTest {
        localStorage.username = "user"
        localStorage.password = "pass"
        localStorage.accessToken = "access"

        localStorage.clearCredentials()

        assertEquals("", localStorage.username)
        assertEquals("", localStorage.password)
        assertEquals(mapOf("access_token" to "access"), store.values)
    }

    @Test
    fun deviceIdIsMadeOnceAndKept() {
        val id = localStorage.deviceId

        assertTrue(id.isNotBlank())
        assertEquals(id, LocalStorage(store).deviceId)
    }

    @Test
    fun blankDeviceNameFallsBackToThePlatform() {
        localStorage.deviceName = "Front Counter"
        assertEquals("Front Counter", LocalStorage(store).deviceName)

        localStorage.deviceName = "  "
        assertTrue(localStorage.deviceName.isNotBlank())
    }

    @Test
    fun stationCredentialsRoundTripAndClear() {
        assertNull(localStorage.stationCredentials)

        val credentials = StationCredentials(Uuid.random(), "secret")
        localStorage.stationCredentials = credentials
        assertEquals(credentials, LocalStorage(store).stationCredentials)

        localStorage.stationCredentials = null
        assertNull(localStorage.stationCredentials)
    }
}
