package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.components.data.components.UserSettings
import org.mr0btain.ticketman.components.data.components.UserSettingsDataStatus

class SettingsViewModel(
    private val fileManager: FileManager
): ViewModel() {

    // For updating and getting settings
    private val _userSettingsDataState : MutableStateFlow<UserSettingsDataStatus> = MutableStateFlow(UserSettingsDataStatus.LOADING)
    val userSettingsState = _userSettingsDataState.asStateFlow()

    // TODO: Setup error saving and such
    fun getUserSettings() {
        val settings = fileManager.getSettings(viewModelScope)

        _isDarkMode.value = settings.darkMode
        _serverAddress.value = settings.serverAddress
        _scrollBarOnTop.value = settings.horizontalScrollTop
        _scrollBarOnRight.value = settings.verticalScrollRight
    }

    fun updateUserSettings() {
        fileManager.saveSettings(
            UserSettings(
                darkMode = _isDarkMode.value,
                serverAddress = _serverAddress.value,
                verticalScrollRight = _scrollBarOnRight.value,
                horizontalScrollTop = _scrollBarOnTop.value
            ),
            viewModelScope
        )
    }

    // First basic group

    // Second data group
    // Server address
    private val _serverAddress: MutableStateFlow<String> = MutableStateFlow("")
    var serverAddress = _serverAddress.asStateFlow()

    // Nothing
    private val _numberPreference: MutableStateFlow<String> = MutableStateFlow("0")
    var numberPreference = _numberPreference.asStateFlow()


    // Third UI group

    // Dark mode

    private val _isDarkMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isDarkMode = _isDarkMode.asStateFlow()

    // Should we have scroll bars on the right or left
    private val _scrollBarOnRight: MutableStateFlow<Boolean> = MutableStateFlow(true)
    var scrollBarOnRight = _scrollBarOnRight.asStateFlow()

    // Should we have scroll bars on the Top or Bottom
    private val _scrollBarOnTop: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var scrollBarOnTop = _scrollBarOnTop.asStateFlow()



    private val _isSwitchOn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isSwitchOn = _isSwitchOn.asStateFlow()

    // Functions to change settings

    fun toggleDarkMode(){
        // Update the value
        _isDarkMode.value = !_isDarkMode.value

        // Save it
        updateUserSettings()
    }

    fun toggleScrollBarOnRight(){
        // Update value
        _scrollBarOnRight.value = !_scrollBarOnRight.value

        // Save it
        updateUserSettings()
    }

    fun toggleScrollBarOnTop(){
        // Update value
        _scrollBarOnTop.value = !_scrollBarOnTop.value

        // Save it
        updateUserSettings()
    }


    fun toggleSwitch(){
        // Update value
        _isSwitchOn.value = !_isSwitchOn.value

        // Save it
        updateUserSettings()
    }

    fun saveServerAddress(finalText: String) {
        // update value
        _serverAddress.value = finalText

        // Save it
        updateUserSettings()
    }

    // just checking, if it is not empty - but you can check anything
    fun checkTextInput(text: String) = text.isNotEmpty()

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

    // saving the number / show error if something goes wrong
    fun saveNumber(text: String) {
        // Update value
        _numberPreference.value = text

        // Save it
        updateUserSettings()
    }

    companion object {
        const val TAG = "SettingsViewModel"
    }

}