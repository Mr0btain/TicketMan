package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.settings.UserSettings
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.DataHandler
import org.mr0btain.ticketman.util.AppDir

// TODO: Setup log in button and log in status

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app
 */

class MainViewModel(
    appDir: AppDir,
    val snackBarHost: SnackbarHostState
) : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
        private const val INIT_TAG = "$TAG:Init"
        private const val INIT_SYNC_TAG = "$TAG:Init_Sync"
    }

    // TODO: Add a way to check if we have network connection, and once we do have it run a check and sync
    fun init() {
        viewModelScope.launch {
            // Pull saved settings
            dataHandler.getSettings()?.let { result ->
                _darkMode.value = result.darkMode
                _serverAddress.value = result.serverUrl
                _scrollBarsOnRight.value = result.scrollBarsOnRight
                _scrollBarsOnBottom.value = result.scrollBarsOnBottom
                _autoSyncDatabase.value = result.autoSync
            } ?: AppLogger.i(INIT_TAG, SimpleError.FAIL)

            // Populate data lists from local
            dataHandler.populate()

            if (dataHandler.loginCheck()) {
                if (dataHandler.syncData())
                    AppLogger.m(INIT_SYNC_TAG, SimpleError.SUCCESS)
                else
                    AppLogger.i(INIT_SYNC_TAG, SimpleError.FAIL)
            } else AppLogger.i(INIT_SYNC_TAG, "No Connection or Not Logged in")
        }
    }

    /**
     * Main data handler where most of the functions and data is held
     */
    val dataHandler = DataHandler(appDir, viewModelScope)

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
    val selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    // To update the main tab index
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
    }

    private val _showLogin: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()

    /**
     * Update whether we should show the login popup or not
     */
    fun updateShowLogin(value: Boolean) {
        _showLogin.value = value
    }

    /** Settings section */
    // ----------------------------------------------------------------------------------------------------------------------

    private val _darkMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val darkMode = _darkMode.asStateFlow()

    private val _serverAddress: MutableStateFlow<String> = MutableStateFlow("")
    val serverAddress = _serverAddress.asStateFlow()

    private val _scrollBarsOnBottom: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnBottom = _scrollBarsOnBottom.asStateFlow()

    private val _scrollBarsOnRight: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnRight = _scrollBarsOnRight.asStateFlow()

    private val _autoSyncDatabase: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val autoSyncDatabase = _autoSyncDatabase.asStateFlow()

    fun toggleAutoSync() {
        val value = !_autoSyncDatabase.value
        _autoSyncDatabase.value = value
        saveSettings()
    }

    fun toggleDarkMode() {
        val value = !_darkMode.value
        _darkMode.value = value
        saveSettings()
    }

    fun updateServerUrl(url: String) {
        _serverAddress.value = url
        viewModelScope.launch {
            dataHandler.updateServerUrl(url)
            saveSettings()
        }
    }

    fun toggleScrollBarsOnBottom() {
        val value = !_scrollBarsOnBottom.value
        _scrollBarsOnBottom.value = value
        saveSettings()
    }

    fun toggleScrollBarsOnRight() {
        val value = !_scrollBarsOnRight.value
        _scrollBarsOnRight.value = value
        saveSettings()
    }

    private fun saveSettings() {
        dataHandler.saveSettings(
            UserSettings(
                darkMode = _darkMode.value,
                serverUrl = _serverAddress.value,
                autoSync = _autoSyncDatabase.value,
                scrollBarsOnRight = _scrollBarsOnRight.value,
                scrollBarsOnBottom = _scrollBarsOnBottom.value
            )
        )
    }
}