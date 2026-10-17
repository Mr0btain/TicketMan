package org.mr0btain.ticketman.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Window wide settings the app asks each platform's entry point to apply, like going fullscreen for kiosk mode
 */
object AppWindow {
    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    fun setFullscreen(value: Boolean) {
        _fullscreen.value = value
    }
}
