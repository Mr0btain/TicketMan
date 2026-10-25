package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.print.QrCode
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * [text] as a QR code for a phone to scan, like a payment link. Always dark on white with a quiet zone, whatever the
 * theme, since scanners want that. Draws nothing when [text] is too long to encode, see [qrFits]
 */
@Composable
fun TmQrCode(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    contentDescription: String = "QR code"
) {
    val code = remember(text) { runCatching { QrCode.encode(text) }.getOrNull() } ?: return

    Box(
        modifier = modifier
            .size(size)
            .clip(TmTheme.shapes.control)
            .background(QR_LIGHT)
            .padding(size / (code.size + QUIET_ZONE * 2) * QUIET_ZONE)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val module = this.size.minDimension / code.size
            for (y in 0 until code.size) {
                for (x in 0 until code.size) {
                    if (code[x, y]) drawRect(QR_DARK, Offset(x * module, y * module), Size(module + 0.5f, module + 0.5f))
                }
            }
        }
    }
}

/** Whether [text] is short enough for [TmQrCode] */
fun qrFits(text: String): Boolean = runCatching { QrCode.encode(text) }.isSuccess

private const val QUIET_ZONE = 2
private val QR_DARK = Color(0xFF000000)
private val QR_LIGHT = Color(0xFFFFFFFF)
