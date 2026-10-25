package org.mr0btain.ticketman.components.image

// Sent as is until FileKit's iOS compressor is checked to keep photos upright (iPhone photos carry their
// orientation in EXIF). The server scales and rotates it anyway
actual suspend fun shrinkForUpload(bytes: ByteArray): ByteArray = bytes
