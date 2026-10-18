package org.mr0btain.ticketman.components.image

import coil3.PlatformContext
import coil3.disk.DiskCache
import okio.Path.Companion.toPath

// The system may clear this when space runs low, which is fine for a cache
actual fun imageDiskCache(context: PlatformContext, maxSizeBytes: Long): DiskCache? =
    DiskCache.Builder()
        .directory(context.cacheDir.resolve("images").absolutePath.toPath())
        .maxSizeBytes(maxSizeBytes)
        .build()
