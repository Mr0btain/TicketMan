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

    private val _textPreference: MutableStateFlow<String> = MutableStateFlow("")
    var textPreference = _textPreference.asStateFlow()

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
        _textPreference.value = finalText
        // place to store text
    }

    // just checking, if it is not empty - but you can check anything
    fun checkTextInput(text: String) = text.isNotEmpty()

    // to get separator for the locale
    private val separatorChar = DecimalFormatSymbols.getInstance().decimalSeparator

    // filtering only numbers and decimal separator
    fun filterNumbers(text: String): String = text.filter {
        it.isDigit()
    }

    // someone can still put more decimal points into the textfield
    // we should always try to convert text to number
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