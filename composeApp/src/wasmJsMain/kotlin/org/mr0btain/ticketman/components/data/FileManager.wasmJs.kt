package org.mr0btain.ticketman.components.data

import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.components.UserSettings

actual class FileManager actual constructor() {
    actual fun getSettings(): UserSettings {
        TODO("Not yet implemented")
    }

    actual fun saveSettings(
        settings: UserSettings,
        scope: CoroutineScope
    ) {
    }
}