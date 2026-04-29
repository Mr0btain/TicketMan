package org.mr0btain.ticketman.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object CustomMaterialColors {
    private var _InProgress by mutableStateOf(inProgressLight)

    val inProgress: Color
        get() = _InProgress

    private var _OnInProgress by mutableStateOf(onInProgressLight)

    val onInProgress: Color
        get() = _OnInProgress

    private var _Success by mutableStateOf(onSuccessContainerDark)

    val success: Color
        get() = _Success

    private var _OnSuccess by mutableStateOf(onOnSuccessContainerDark)

    val onSuccess: Color
        get() = _OnSuccess

    private var _DarkMode by mutableStateOf(false)

    val darkMode: Boolean
        get() = _DarkMode

    fun isDarkMode(value: Boolean) {
        if (value) {
            _InProgress = inProgressDark
            _OnInProgress = onInProgressDark
        } else {
            _InProgress = inProgressLight
            _OnInProgress = onInProgressLight
        }

        _DarkMode = value
    }
}