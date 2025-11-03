package package org.mr0btain.ticketman.utils

actual fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
    return try {
        val imageBytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
