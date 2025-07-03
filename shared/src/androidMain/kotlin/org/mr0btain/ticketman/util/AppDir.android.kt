package org.mr0btain.ticketman.util

import android.app.Application
import android.content.Context
import kotlinx.io.files.Path

actual class AppDir(
    context: Context
) {
    actual val appId: String = "TicketMan"

    actual val path: Path? = Path(context.dataDir.path)
    actual val currentPlatform: Platform = Platform.Android
}