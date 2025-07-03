package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.components.settings.UserSettings
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.getPlatform
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

            dataHandler.customerDataHandler.populateListFromDatabase()
            dataHandler.ticketDataHandler.populateListFromDatabase()
            dataHandler.userDataHandler.populateListFromDatabase()
            dataHandler.inventoryDataHandler.populateListFromDatabase()

            if (!dataHandler.syncData()) AppLogger.i(INIT_TAG, SimpleError.FAIL)
        }
    }

    val dataHandler = DataHandler(appDir, viewModelScope)

    // Platform the app is running on
    val platform = getPlatform().name

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

    fun saveServerAddress(address: String) {
        // update value
        _serverAddress.value = address
        saveSettings()
    }

    fun updateServerAddress(url: String) {
        saveServerAddress(url)
        dataHandler.updateServerUrl(url)
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

    /**
     * Check to see if an address is valid. Will have to set up a ping or something to actually check. for now just check text
     */
    fun checkWebAddress(text: String): Boolean {
        var isValid = false

        // basic placeholder check
        if (text.startsWith("http://") || text.startsWith("https://") && text.endsWith(".com")) isValid = true

        // Basic ip check to see if there is enough digits
        val ipcheck = text.filter { it.isDigit() }
        if (ipcheck.length >= 6) isValid = true

        return isValid
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