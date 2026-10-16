package org.mr0btain.ticketman.components.data.local

// TODO("Finish implementation")
class LocalStorage() {
    private var refreshToken: String? = null
    private var username: String = "kenneth_mitchell"
    private var password: String = "Mitchell"
    private var accessToken: String? = null

    fun getAccessToken(): String = accessToken ?: ""
    fun setAccessToken(token: String) {
        accessToken = token
    }
    fun setRefreshToken(token: String) {
        refreshToken = token
    }
    fun getRefreshToken(): String = refreshToken ?: ""

    // Admin: User(
    // identifier=01a135ff-c386-733b-9289-229e12f7398c,
    // username=kenneth_mitchell,
    // password=Mitchell,
    // name=kenneth:mitchell,
    // email=james.smith52@gmail.com,
    // phone=769-856-8578,
    // accessLevel=ADMIN)

    fun getUsername(): String = username
    fun getPassword(): String = password
}