package org.mr0btain.ticketman.components.image

import coil3.PlatformContext
import coil3.disk.DiskCache
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

// Caches isn't backed up, and the system may clear it when space runs low, which is fine for a cache
@OptIn(ExperimentalForeignApi::class)
actual fun imageDiskCache(context: PlatformContext, maxSizeBytes: Long): DiskCache? {
    val caches = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )?.path ?: return null

    return DiskCache.Builder()
        .directory("$caches/images".toPath())
        .maxSizeBytes(maxSizeBytes)
        .build()
}
