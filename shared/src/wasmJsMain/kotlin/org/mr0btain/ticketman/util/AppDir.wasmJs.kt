package org.mr0btain.ticketman.util

import kotlinx.io.files.Path

actual class AppDir {
    actual val TAG = "AppDir"
    actual val appId: String = "TicketMan"
    // A browser has no folder to keep files in. The web client only talks to the server
    actual val path: Path? = null
    actual val currentPlatform: Platform = Platform.WasmJs
}