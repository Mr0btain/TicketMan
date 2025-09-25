package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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
    }

    /**
     * Gets normal user settings
     */
    // If we want settings or access tokens pull them specifically.
    fun getSettings(): UserSettings? {
        return try {
            var result: UserSettings? = null

            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = SettingsTable
                    .selectAll()
                    .where { SettingsTable.identifier eq IDENTIFIER }
                    .map { item ->
                        UserSettings(
                            darkMode = item[SettingsTable.isDarkMode],
                            serverUrl = item[SettingsTable.serverUrl],
                            autoSync = item[SettingsTable.autoSync],
                            scrollBarsOnBottom = item[SettingsTable.scrollBarsOnBottom],
                            scrollBarsOnRight = item[SettingsTable.scrollBarsOnRight]
                        )
                    }.firstOrNull()
            }

            AppLogger.m(GET_SETTINGS_TAG, SimpleResult.SUCCESS)
            result
        } catch (ex: Exception) {
            AppLogger.e(GET_SETTINGS_TAG, SimpleResult.FAIL, ex)
            null
        }
    }

    /**
     * Saves normal user settings to database
     */
    fun saveSettings(data: UserSettings): Boolean {
        return try {
            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                SettingsTable.update(where = { SettingsTable.identifier eq IDENTIFIER }) { item ->
                    item[SettingsTable.isDarkMode] = data.darkMode
                    item[SettingsTable.serverUrl] = data.serverUrl
                    item[SettingsTable.autoSync] = data.autoSync
                    item[SettingsTable.scrollBarsOnBottom] = data.scrollBarsOnBottom
                    item[SettingsTable.scrollBarsOnRight] = data.scrollBarsOnRight
                }
            }

            AppLogger.m(SAVE_SETTINGS_TAG, SimpleResult.SUCCESS)
            true
        } catch (ex: Exception) {
            AppLogger.e(SAVE_SETTINGS_TAG, SimpleResult.FAIL, ex)
            false
        }
    }

    /**
     * Gets user password from database
     */
    fun getUsername(): String? {
        return try {
            var result: String? = null

            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = SettingsTable
                    .select(SettingsTable.userName)
                    .where { SettingsTable.identifier eq IDENTIFIER }
                    .firstOrNull()
                    ?.get(SettingsTable.userName)
            }

            AppLogger.m(GET_USERNAME_TAG, SimpleResult.SUCCESS)
            result
        } catch (ex: Exception) {
            AppLogger.e(GET_USERNAME_TAG, SimpleResult.FAIL, ex)
            null
        }
    }

    /**
     * Sets user password in database
     */
    fun setUsername(value: String): Boolean {
        return try {
            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                SettingsTable
                    .update(where = { SettingsTable.identifier eq IDENTIFIER }) { item ->
                        item[SettingsTable.userName] = value
                    }
            }

            AppLogger.m(SET_USERNAME_TAG, SimpleResult.SUCCESS)
            true
        } catch (ex: Exception) {
            AppLogger.e(SET_USERNAME_TAG, SimpleResult.FAIL, ex)
            false
        }
    }

    /**
     * Gets user password from database
     */
    fun getPassword(): String? {
        return try {
            var result: String? = null

            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = SettingsTable
                    .select(SettingsTable.password)
                    .where { SettingsTable.identifier eq IDENTIFIER }
                    .firstOrNull()
                    ?.get(SettingsTable.password)
            }

            AppLogger.m(GET_PASSWORD_TAG, SimpleResult.SUCCESS)
            result
        } catch (ex: Exception) {
            AppLogger.e(GET_PASSWORD_TAG, SimpleResult.FAIL, ex)
            null
        }
    }

    /**
     * Sets user password in database
     */
    fun setPassword(value: String): Boolean {
        return try {
            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                SettingsTable
                    .update(where = { SettingsTable.identifier eq IDENTIFIER }) { item ->
                        item[SettingsTable.password] = value
                    }
            }

            AppLogger.m(SET_PASSWORD_TAG, SimpleResult.SUCCESS)
            true
        } catch (ex: Exception) {
            AppLogger.e(SET_PASSWORD_TAG, SimpleResult.FAIL, ex)
            false
        }
    }

    /**
     * Gets access token for authentication with server from database
     */
    fun getAccessToken(): String? {
        return try {
            var result: String? = null

            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = SettingsTable
                    .select(SettingsTable.accessToken)
                    .where { SettingsTable.identifier eq IDENTIFIER }
                    .firstOrNull()
                    ?.get(SettingsTable.accessToken)
            }

            AppLogger.m(GET_ACCESS_TOKEN_TAG, SimpleResult.SUCCESS)
            result
        } catch (ex: Exception) {
            AppLogger.e(GET_ACCESS_TOKEN_TAG, SimpleResult.FAIL, ex)
            null
        }
    }

    /**
     * Sets access token in database
     */
    fun setAccessToken(value: String): Boolean {
        return try {
            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                SettingsTable
                    .update(where = { SettingsTable.identifier eq IDENTIFIER }) { item ->
                        item[SettingsTable.accessToken] = value
                    }
            }

            AppLogger.m(SET_ACCESS_TOKEN_TAG, SimpleResult.SUCCESS)
            true
        } catch (ex: Exception) {
            AppLogger.e(SET_ACCESS_TOKEN_TAG, SimpleResult.FAIL, ex)
            false
        }
    }

    /**
     * Gets refresh token for authentication with server from database
     */
    fun getRefreshToken(): String? {
        return try {
            var result: String? = null

            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = SettingsTable
                    .select(SettingsTable.refreshToken)
                    .where { SettingsTable.identifier eq IDENTIFIER }
                    .firstOrNull()
                    ?.get(SettingsTable.refreshToken)
            }

            AppLogger.m(GET_REFRESH_TOKEN_TAG, SimpleResult.SUCCESS)
            result
        } catch (ex: Exception) {
            AppLogger.e(GET_REFRESH_TOKEN_TAG, SimpleResult.FAIL, ex)
            null
        }
    }

    /**
     * Sets refresh token in database
     */
    fun setRefreshToken(value: String): Boolean {
        return try {
            transaction {
                if (shouldLog) addLogger(StdOutSqlLogger)

                SettingsTable
                    .update(where = { SettingsTable.identifier eq IDENTIFIER }) { item ->
                        item[SettingsTable.refreshToken] = value
                    }
            }

            AppLogger.m(SET_REFRESH_TOKEN_TAG, SimpleResult.SUCCESS)
            true
        } catch (ex: Exception) {
            AppLogger.e(SET_REFRESH_TOKEN_TAG, SimpleResult.FAIL, ex)
            false
        }
    }
}