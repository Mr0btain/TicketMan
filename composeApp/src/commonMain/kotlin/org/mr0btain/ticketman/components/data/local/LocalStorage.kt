package org.mr0btain.ticketman.components.data.local

import org.mr0btain.ticketman.getPlatform
import kotlin.uuid.Uuid

class LocalStorage(private val store: KeyValueStore) {
    private var _username: String = ""
    private var _password: String = ""
    private var _accessToken: String = ""
    private var _refreshToken: String = ""
    private var _darkMode: Boolean = false
    private var _serverUrl: String = ""
    private var _rememberLogin: Boolean = DEFAULT_REMEMBER_LOGIN
    private var _defaultTab: Int = DEFAULT_TAB
    private var _pageSize: Int = DEFAULT_PAGE_SIZE
    private var _searchLimit: Int = DEFAULT_SEARCH_LIMIT
    private var _cacheSize: Int = DEFAULT_CACHE_SIZE
    private var _deviceId: String = ""
    private var _deviceName: String = ""

    var username: String
        get() = _username
        set(value) {
            _username = value
            store.putString(USERNAME_KEY, value.trim())
        }

    var password: String
        get() = _password
        set(value) {
            _password = value
            store.putString(PASSWORD_KEY, value.trim())
        }

    var accessToken: String
        get() = _accessToken
        set(value) {
            _accessToken = value
            store.putString(ACCESS_TOKEN_KEY, value.trim())
        }

    var refreshToken: String
        get() = _refreshToken
        set(value) {
            _refreshToken = value
            store.putString(REFRESH_TOKEN_KEY, value.trim())
        }

    var darkMode: Boolean
        get() = _darkMode
        set(value) {
            _darkMode = value
            store.putString(DARK_MODE_KEY, value.toString())
        }

    var serverUrl: String
        get() = _serverUrl
        set(value) {
            _serverUrl = value
            store.putString(SERVER_URL_KEY, value.trim())
        }

    /** Whether the username and password should be saved after a successful login */
    var rememberLogin: Boolean
        get() = _rememberLogin
        set(value) {
            _rememberLogin = value
            store.putString(REMEMBER_LOGIN_KEY, value.toString())
        }

    /** Main screen tab index to start on */
    var defaultTab: Int
        get() = _defaultTab
        set(value) {
            _defaultTab = value
            store.putString(DEFAULT_TAB_KEY, value.toString())
        }

    /** Number of items each pager pulls per page */
    var pageSize: Int
        get() = _pageSize
        set(value) {
            _pageSize = value
            store.putString(PAGE_SIZE_KEY, value.toString())
        }

    /** Max number of results returned by a search */
    var searchLimit: Int
        get() = _searchLimit
        set(value) {
            _searchLimit = value
            store.putString(SEARCH_LIMIT_KEY, value.toString())
        }

    /** Max number of items held in each entity cache */
    var cacheSize: Int
        get() = _cacheSize
        set(value) {
            _cacheSize = value
            store.putString(CACHE_SIZE_KEY, value.toString())
        }

    /** Made the first time it's needed and kept, lets the server tell this device's session apart from the user's others */
    val deviceId: String
        get() {
            if (_deviceId.isBlank()) {
                _deviceId = Uuid.random().toString()
                store.putString(DEVICE_ID_KEY, _deviceId)
            }

            return _deviceId
        }

    /** What admins see for this device in the session list */
    var deviceName: String
        get() = _deviceName
        set(value) {
            _deviceName = value.trim().ifBlank { defaultDeviceName() }
            store.putString(DEVICE_NAME_KEY, _deviceName)
        }

    // Initialize by pulling stored values
    init {
        _accessToken = store.getString(ACCESS_TOKEN_KEY, "")
        _refreshToken = store.getString(REFRESH_TOKEN_KEY, "")
        _username = store.getString(USERNAME_KEY, "")
        _password = store.getString(PASSWORD_KEY, "")
        _darkMode = getBoolean(DARK_MODE_KEY, false)
        _serverUrl = store.getString(SERVER_URL_KEY, "")
        _rememberLogin = getBoolean(REMEMBER_LOGIN_KEY, DEFAULT_REMEMBER_LOGIN)
        _defaultTab = getInt(DEFAULT_TAB_KEY, DEFAULT_TAB)
        _pageSize = getInt(PAGE_SIZE_KEY, DEFAULT_PAGE_SIZE)
        _searchLimit = getInt(SEARCH_LIMIT_KEY, DEFAULT_SEARCH_LIMIT)
        _cacheSize = getInt(CACHE_SIZE_KEY, DEFAULT_CACHE_SIZE)

        _deviceId = store.getString(DEVICE_ID_KEY, "")
        _deviceName = store.getString(DEVICE_NAME_KEY, "").ifBlank { defaultDeviceName() }
    }

    private fun defaultDeviceName(): String = getPlatform().name

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        store.getString(key, "").toBooleanStrictOrNull() ?: defaultValue

    private fun getInt(key: String, defaultValue: Int): Int =
        store.getString(key, "").toIntOrNull() ?: defaultValue

    /**
     * Removes the stored tokens. Uses delete over KSafe's clearAll since that also wipes the encryption keys
     */
    suspend fun clearSession() {
        // Clear stored
        store.delete(ACCESS_TOKEN_KEY)
        store.delete(REFRESH_TOKEN_KEY)

        // Clear local values
        _accessToken = ""
        _refreshToken = ""
    }

    /**
     * Removes the stored username and password
     */
    suspend fun clearCredentials() {
        store.delete(USERNAME_KEY)
        store.delete(PASSWORD_KEY)

        _username = ""
        _password = ""
    }

    companion object {
        const val DEFAULT_REMEMBER_LOGIN = true
        const val DEFAULT_TAB = 0
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_SEARCH_LIMIT = 20
        const val DEFAULT_CACHE_SIZE = 20

        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val USERNAME_KEY = "username"
        private const val PASSWORD_KEY = "password"
        private const val DARK_MODE_KEY = "dark_mode"
        private const val SERVER_URL_KEY = "server_url"
        private const val REMEMBER_LOGIN_KEY = "remember_login"
        private const val DEFAULT_TAB_KEY = "default_tab"
        private const val PAGE_SIZE_KEY = "page_size"
        private const val SEARCH_LIMIT_KEY = "search_limit"
        private const val CACHE_SIZE_KEY = "cache_size"
        private const val DEVICE_ID_KEY = "device_id"
        private const val DEVICE_NAME_KEY = "device_name"
    }
}
