package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.components.data.settings.UserSettings
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger

@Suppress("PrivatePropertyName")
class SettingsDatabase(
    private val shouldLog: Boolean = true
) {
    private val TAG = "SettingsDatabase"
    private val IDENTIFIER = "0" // Use same identifier everytime

    private val INITIALIZE_TAG = "$TAG:Initialize"
    private val GET_SETTINGS_TAG = "$TAG:GetSettings"
    private val SAVE_SETTINGS_TAG = "$TAG:SaveSettings"
    private val GET_USERNAME_TAG = "$TAG:GetUsername"
    private val SET_USERNAME_TAG = "$TAG:SetUsername"
    private val GET_PASSWORD_TAG = "$TAG:GetPassword"
    private val SET_PASSWORD_TAG = "$TAG:SetPassword"
    private val GET_ACCESS_TOKEN_TAG = "$TAG:GetAccessToken"
    private val SET_ACCESS_TOKEN_TAG = "$TAG:SetAccessToken"
    private val GET_REFRESH_TOKEN_TAG = "$TAG:GetRefreshToken"
    private val SET_REFRESH_TOKEN_TAG = "$TAG:SetRefreshToken"

    /**
     * For initializing the database with default values if none exist
     */
    fun initialize() {
        /*
        var initialized = false

        try {
            val default = UserSettings()

            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                val current = SettingsTable.selectAll().where { SettingsTable.identifier eq IDENTIFIER }.firstOrNull()

                if (current == null) {
                    SettingsTable.insert { item ->
                        item[SettingsTable.identifier] = IDENTIFIER
                        item[SettingsTable.isDarkMode] = default.darkMode
                        item[SettingsTable.serverUrl] = default.serverUrl
                        item[SettingsTable.autoSync] = default.autoSync
                        item[SettingsTable.scrollBarsOnBottom] = default.scrollBarsOnBottom
                        item[SettingsTable.scrollBarsOnRight] = default.scrollBarsOnRight
                        item[SettingsTable.userName] = "0"
                        item[SettingsTable.password] = "0"
                        item[SettingsTable.accessToken] = "0"
                        item[SettingsTable.refreshToken] = "0"
                    }

                    initialized = true
                }
            }
        } catch (ex: Exception) {
            AppLogger.e(INITIALIZE_TAG, SimpleResult.FAIL, ex)
        }

        if (initialized) {
            AppLogger.m(INITIALIZE_TAG, "Settings Initialized")
        } else AppLogger.m(INITIALIZE_TAG, "Settings already exists")
         */
    }

    /**
     * Gets normal user settings
     */
    // If we want settings or access tokens pull them specifically.
    fun getSettings(): UserSettings? {
        return UserSettings()
    }

    /**
     * Saves normal user settings to database
     */
    fun saveSettings(data: UserSettings): Boolean {
        return false
    }

    /**
     * Gets user password from database
     */
    fun getUsername(): String? {
       return null
    }

    /**
     * Sets user password in database
     */
    fun setUsername(value: String): Boolean {
        return false
    }

    /**
     * Gets user password from database
     */
    fun getPassword(): String? {
        return null
    }

    /**
     * Sets user password in database
     */
    fun setPassword(value: String): Boolean {
        return false
    }

    /**
     * Gets access token for authentication with server from database
     */
    fun getAccessToken(): String? {
        return ""
    }

    /**
     * Sets access token in database
     */
    fun setAccessToken(value: String): Boolean {
        return false
    }

    /**
     * Gets refresh token for authentication with server from database
     */
    fun getRefreshToken(): String? {
        return ""
    }

    /**
     * Sets refresh token in database
     */
    fun setRefreshToken(value: String): Boolean {
        return false
    }
}