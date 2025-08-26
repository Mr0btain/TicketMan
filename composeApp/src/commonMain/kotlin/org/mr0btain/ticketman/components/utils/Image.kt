package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.graphics.ImageBitmap


expect fun decodeBase64ToImageBitmap(base64: String): ImageBitmap?

expect fun ImageBitmap.encodeToBase64(): String