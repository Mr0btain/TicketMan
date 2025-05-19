package components

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/**
    Main View model for the project.
    Used to store data that needs to persist for the lifetime of the app and be used anywhere
 */

class MainViewModel(): ViewModel() {
    // Settings Values
    val darkMode = mutableStateOf(false)
}