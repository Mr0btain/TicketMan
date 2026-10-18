package org.mr0btain.ticketman.components.image

import coil3.PlatformContext
import coil3.disk.DiskCache
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.cacheDir
import okio.Path.Companion.toPath

// FileKit is set up with the app id in main, so this is the OS's cache folder for TicketMan
actual fun imageDiskCache(context: PlatformContext, maxSizeBytes: Long): DiskCache? =
    DiskCache.Builder()
        .directory(FileKit.cacheDir.absolutePath().toPath().resolve("images"))
        .maxSizeBytes(maxSizeBytes)
        .build()
