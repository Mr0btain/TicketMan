package org.mr0btain.ticketman.util

import kotlinx.io.files.Path

actual class AppDir {
    actual val TAG = "AppDir"
    actual val appId: String = "TicketMan"
    // TODO: Browser never uses this, browser only connects to database
    actual val path: Path? = Path("")
    actual val currentPlatform: Platform = Platform.WasmJs
}