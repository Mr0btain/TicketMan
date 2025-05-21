package screens.settings.components

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DecimalFormatSymbols

class SettingsViewModel: ViewModel() {
    private val _isDarkMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isDarkMode = _isDarkMode.asStateFlow()

    private val _isSwitchOn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isSwitchOn = _isSwitchOn.asStateFlow()

    private val _serverAddress: MutableStateFlow<String> = MutableStateFlow("")
    var serverAddress = _serverAddress.asStateFlow()

    private val _numberPreference: MutableStateFlow<String> = MutableStateFlow("0")
    var numberPreference = _numberPreference.asStateFlow()

    // Functions to change settings

    fun toggleDarkMode(){
        _isDarkMode.value = !_isDarkMode.value
        // here is place for permanent storage handling
    }


    fun toggleSwitch(){
        _isSwitchOn.value = !_isSwitchOn.value
        // here is place for permanent storage handling - switch
    }

    fun saveText(finalText: String) {
        _serverAddress.value = finalText
        // place to store text
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
        _numberPreference.value = text
        //val value = text.toDoubleOrNull() ?: 0 // default value / handle the error in some way - show toast or something
    }

    companion object {
        const val TAG = "SettingsViewModel"
    }

}