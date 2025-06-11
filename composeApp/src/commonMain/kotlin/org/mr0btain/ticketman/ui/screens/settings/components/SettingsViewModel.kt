package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStore: DataStore<Preferences>
): ViewModel() {

    private companion object {
        const val TAG = "SettingsViewModel"

        val DARK_MODE = booleanPreferencesKey("appInDarkMode")
        val SERVER_ADDRESS = stringPreferencesKey("serverAddress")
        val SCROLL_BARS_ON_BOTTOM = booleanPreferencesKey("scrollBarsOnBottom")
        val SCROLL_BARS_ON_RIGHT = booleanPreferencesKey("scrollBarsOnRight")
    }

    private val _darkMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var darkMode = _darkMode.asStateFlow()

    private val _serverAddress: MutableStateFlow<String> = MutableStateFlow("")
    var serverAddress = _serverAddress.asStateFlow()

    private val _scrollBarsOnBottom: MutableStateFlow<Boolean> = MutableStateFlow(true)
    var scrollBarsOnBottom = _scrollBarsOnBottom.asStateFlow()

    private val _scrollBarsOnRight: MutableStateFlow<Boolean> = MutableStateFlow(true)
    var scrollBarsOnRight = _scrollBarsOnRight.asStateFlow()

    // Pulling it all at once this way saves us from a real hard hitch in the ui when we change these
    fun getSettingsFromPreferences() {
        viewModelScope.launch {
            _darkMode.value = dataStore.data.map { preferences -> preferences[DARK_MODE] ?: false }.first()
            _serverAddress.value = dataStore.data.map { preferences -> preferences[SERVER_ADDRESS] ?: "" }.first()
            _scrollBarsOnBottom.value = dataStore.data.map { preferences -> preferences[SCROLL_BARS_ON_BOTTOM] ?: true }.first()
            _scrollBarsOnRight.value = dataStore.data.map { preferences -> preferences[SCROLL_BARS_ON_RIGHT] ?: true }.first()
        }
    }

    fun toggleDarkMode(){
        // Update the value
        val value = !_darkMode.value

        _darkMode.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[DARK_MODE] = value }
        }
    }

    fun saveServerAddress(address: String) {
        // update value
        _serverAddress.value = address

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[SERVER_ADDRESS] = address }
        }
    }

    fun toggleScrollBarsOnBottom(){
        // Update the value
        val value = !_scrollBarsOnBottom.value

        _scrollBarsOnBottom.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[SCROLL_BARS_ON_BOTTOM] = value }
        }
    }

    fun toggleScrollBarsOnRight(){
        // Update the value
        val value = !_scrollBarsOnRight.value

        _scrollBarsOnRight.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[SCROLL_BARS_ON_RIGHT] = value }
        }
    }

    /**
     * Check to see if an address is valid. Will have to setup a ping or something to actually check. for now just check text
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

    // filtering only number
    fun filterNumbers(text: String): String = text.filter {
        it.isDigit()
    }

    // Check if text is a number
    fun checkNumber(text: String): Boolean {
        val value = text.toDoubleOrNull() ?: return false
        return value >= 0
    }
}