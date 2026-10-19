package org.mr0btain.ticketman.util

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.mr0btain.ticketman.logging.AppLogger
import java.io.File

actual class AppDir {
    actual val TAG = "AppDir"
    actual val appId = "TicketMan"

    actual val path = appDataFolder(currentPlatform, appId, System::getenv)
        .also { if (it == null) AppLogger.e(TAG, "No app data folder for $currentPlatform, its HOME or APPDATA isn't set") }
        ?.let { Path(it) }
        ?.also { value ->
            // Make sure it exists
            if (!SystemFileSystem.exists(value)) {
                File(value.toString()).mkdirs()
            }
        }

    actual val currentPlatform: Platform
        get() = platformFromOsName(System.getProperty("os.name"))
}

/** Which desktop system an `os.name` is. Anything unknown is treated like Linux */
internal fun platformFromOsName(osName: String): Platform {
    val system = osName.lowercase()
    return when {
        system.startsWith("windows") -> Platform.Windows
        system.contains("mac") || system.contains("darwin") -> Platform.MacOS
        else -> Platform.Linux
    }
}

/**
 * Where each desktop system keeps app data, from its environment [env]: `$XDG_DATA_HOME/<appId>` (or
 * `~/.local/share/<appId>`) on Linux, `~/Library/Application Support/<appId>` on macOS, `%APPDATA%/<appId>` on
 * Windows. Null when the variable it needs isn't set, or on a system that isn't a desktop
 */
internal fun appDataFolder(platform: Platform, appId: String, env: (String) -> String?): String? {
    fun variable(name: String) = env(name)?.takeIf { it.isNotBlank() }?.trimEnd('/', '\\')

    return when (platform) {
        // XDG_DATA_HOME is already the data folder (usually ~/.local/share), HOME needs it added
        Platform.Linux -> variable("XDG_DATA_HOME")?.let { "$it/$appId" } ?: variable("HOME")?.let { "$it/.local/share/$appId" }
        Platform.MacOS -> variable("HOME")?.let { "$it/Library/Application Support/$appId" }
        Platform.Windows -> variable("APPDATA")?.let { "$it\\$appId" }
        else -> null
    }
}
