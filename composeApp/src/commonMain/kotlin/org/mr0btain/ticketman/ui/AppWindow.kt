package org.mr0btain.ticketman.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _pinned = MutableStateFlow<Boolean?>(null)

    /**
     * Whether the app is pinned to the screen so it can't be left (Android's lock task mode), set by the entry
     * point. Null where the platform can't pin
     */
    val pinned: StateFlow<Boolean?> = _pinned.asStateFlow()

    fun setPinned(value: Boolean?) {
        _pinned.value = value
    }

    private val _pinRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Asks the entry point to pin the app again, after someone turned down or undid pinning */
    val pinRequests: SharedFlow<Unit> = _pinRequests.asSharedFlow()

    fun requestPin() {
        _pinRequests.tryEmit(Unit)
    }
}
