package org.mr0btain.ticketman.components.data

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.components.UserSettings
import kotlin.io.readBytes

actual class FileManager actual constructor() {
    private val projectDir = FileKit.filesDir

    // To save a file
    // TODO: I don't think this will work i think were going to return faster than we can read the file
    actual fun getSettings(scope: CoroutineScope): UserSettings {
        val file = PlatformFile(projectDir, "settings.sts")
        var settingFile = UserSettings()

        // Check if the file exists then try and read it
        if (file.exists()) {
            scope.launch {
                settingFile = Json.decodeFromString<UserSettings>(file.readBytes().decodeToString())
            }
        }

        return settingFile
    }

    actual fun saveSettings(settings: UserSettings, scope: CoroutineScope) {
        val file = PlatformFile(projectDir, "settings.sts")

        scope.launch {
            if (projectDir.exists()) {
                file.write(Json.encodeToString(settings).encodeToByteArray())
            } else {
                projectDir.createDirectories()

                file.write(Json.encodeToString(settings).encodeToByteArray())
            }
        }
    }
}