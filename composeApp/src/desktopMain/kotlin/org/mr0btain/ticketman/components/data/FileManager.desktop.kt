package org.mr0btain.ticketman.components.data

import io.github.vinceglb.filekit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.components.UserSettings

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class FileManager actual constructor() {
    private val projectDir = FileKit.filesDir

    // To save a file
    actual fun getSettings(scope: CoroutineScope): UserSettings {
        val file = PlatformFile(projectDir, "settings.sts")

        return if (file.exists()) {
            val setting = file.file.readBytes().decodeToString()
            Json.decodeFromString<UserSettings>(setting)
        } else UserSettings()
    }

    actual fun saveSettings(settings: UserSettings, scope: CoroutineScope) {
        val file = PlatformFile(projectDir, "settings.sts")

        scope.launch {
            // Check if directory exists if not make it and save the file
            if (projectDir.exists()) {
                file.write(Json.encodeToString(settings).encodeToByteArray())
            } else {
                // Make dir
                projectDir.createDirectories()

                // Write file
                file.write(Json.encodeToString(settings).encodeToByteArray())
            }
        }
    }
}