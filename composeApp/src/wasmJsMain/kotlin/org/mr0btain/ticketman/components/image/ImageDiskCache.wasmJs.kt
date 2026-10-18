package org.mr0btain.ticketman.components.image

import coil3.PlatformContext
import coil3.disk.DiskCache

// No disk here, the browser's own http cache keeps photos thanks to their immutable Cache-Control
actual fun imageDiskCache(context: PlatformContext, maxSizeBytes: Long): DiskCache? = null
