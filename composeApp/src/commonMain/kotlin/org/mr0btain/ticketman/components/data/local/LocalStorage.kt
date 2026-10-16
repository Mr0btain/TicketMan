package org.mr0btain.ticketman.components.data.local

// TODO("Finish implementation")
class LocalStorage(private val store: KeyValueStore) {
    private var username: String = "kenneth_sanchez"
    private var password: String = "Sanchez"

    fun getAccessToken(): String = store.getString(ACCESS_TOKEN_KEY, "")
    fun setAccessToken(token: String) {
        store.putString(ACCESS_TOKEN_KEY, token)
    }
    fun setRefreshToken(token: String) {
        store.putString(REFRESH_TOKEN_KEY, token)
    }
    fun getRefreshToken(): String = store.getString(REFRESH_TOKEN_KEY, "")

    /**
     * Removes the stored tokens. Uses delete over KSafe's clearAll since that also wipes the encryption keys
     */
    suspend fun clearSession() {
        store.delete(ACCESS_TOKEN_KEY)
        store.delete(REFRESH_TOKEN_KEY)
    }

    fun getUsername(): String = username
    fun getPassword(): String = password

    private companion object {
        const val ACCESS_TOKEN_KEY = "access_token"
        const val REFRESH_TOKEN_KEY = "refresh_token"
    }
}
