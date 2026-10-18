package org.mr0btain.ticketman.components.image

// Sent as is: this platform's compressor ignores EXIF orientation, which would turn phone photos sideways. The
// server scales and rotates it anyway
actual suspend fun shrinkForUpload(bytes: ByteArray): ByteArray = bytes
