package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.util.*
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

actual fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
    return Image.makeFromEncoded(base64.decodeBase64Bytes()).toComposeImageBitmap()
}

actual fun ImageBitmap.encodeToBase64(): String {
    val bufferedImage = this.toAwtImage()
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "png", outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.getEncoder().encodeToString(byteArray)
}