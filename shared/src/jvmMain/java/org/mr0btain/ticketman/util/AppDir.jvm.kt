package org.mr0btain.ticketman.util

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.mr0btain.ticketman.logging.AppLogger
import java.io.File

actual class AppDir {
    actual val TAG = "AppDir"
    actual val appId = "TicketMan"

    actual val path = when (currentPlatform) {
            Platform.Linux -> {
                AppLogger.m(TAG, "Linux OS")
                val env = System.getenv("XDG_DATA_HOME") ?: System.getenv("HOME") ?: ""

                if (env.isBlank()) {
                    null
                } else Path("$env/.local/share/$appId")
            }

        // TODO: Test this
            Platform.MacOS -> {
                val env = System.getenv("HOME") ?: ""

                AppLogger.m(TAG, "Mac OS")

                if (env.isBlank()) {
                    println("Env is blank!")
                    null
                } else Path("$env/Library/Application Support/$appId")
            }

        // TODO: test this
            Platform.Windows -> {
                AppLogger.m(TAG, "Windows OS")
                val env = System.getenv("APPDATA") ?: ""

                if (env.isBlank()) {
                    println(" Env is blank!")
                    null
                } else  Path("$env/$appId")
            }

            else -> null
        }?.also { value ->
            // Make sure it exists
            if (!SystemFileSystem.exists(value)) {
                File(value.toString()).mkdirs()
            }
        }

    actual val currentPlatform: Platform
        get() {
            val system = System.getProperty("os.name").lowercase()
            return if (system.contains("win")) {
                Platform.Windows
            } else if (
                system.contains("nix") ||
                system.contains("nux") ||
                system.contains("aix")
            ) {
                Platform.Linux
            } else if (system.contains("mac")) {
                Platform.MacOS
            } else {
                Platform.Linux
            }
        }
}