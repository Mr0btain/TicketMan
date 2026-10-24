package org.mr0btain.ticketman.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual class AppDir {
    actual val TAG = "AppDir"
    actual val appId: String = "TicketMan"

    // Application Support inside the app's sandbox, made the first time it's asked for. Backed up with the device
    @OptIn(ExperimentalForeignApi::class)
    actual val path: Path? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )?.let { url: NSURL -> url.path }?.let { Path(it) }

    actual val currentPlatform: Platform = Platform.IOS
}
