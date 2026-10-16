package org.mr0btain.ticketman.components.data.local

class LocalStorage(private val store: KeyValueStore) {
    private var _username: String = ""
    private var _password: String = ""
    private var _accessToken: String = ""
    private var _refreshToken: String = ""

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

    // Initialize by pulling stored values
    init {
        _accessToken = store.getString(ACCESS_TOKEN_KEY, "")
        _refreshToken = store.getString(REFRESH_TOKEN_KEY, "")
        _username = store.getString(USERNAME_KEY, "")
        _password = store.getString(PASSWORD_KEY, "")
    }

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

    private companion object {
        const val ACCESS_TOKEN_KEY = "access_token"
        const val REFRESH_TOKEN_KEY = "refresh_token"
        const val USERNAME_KEY = "username"
        const val PASSWORD_KEY = "password"
    }
}
