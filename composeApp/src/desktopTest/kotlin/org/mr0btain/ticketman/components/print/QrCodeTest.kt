package org.mr0btain.ticketman.components.print

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Scans the codes we make with ZXing, if it can read them so can a phone
 */
class QrCodeTest {
    private fun scan(code: QrCode): String {
        val scale = 6
        val quiet = 4
        val width = (code.size + quiet * 2) * scale

        val pixels = IntArray(width * width) { index ->
            val x = index % width / scale - quiet
            val y = index / width / scale - quiet
            val dark = x in 0 until code.size && y in 0 until code.size && code[x, y]
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }

        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, width, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun ticketNumbersScan() {
        listOf("26-00000042", "27-00000001", "99-12345678").forEach { number ->
            val code = QrCode.encode(number)
            assertEquals(21, code.size, "Ticket numbers should fit in version 1")
            assertEquals(number, scan(code))
        }
    }

    @Test
    fun longerTextUsesBiggerVersionsAndStillScans() {
        // Covers versions with several error correction blocks and the version info area (7 and up)
        listOf(20, 40, 80, 120, 150, 185).forEach { length ->
            val text = "https://shop.example/t/" + "x".repeat(length)
            assertEquals(text, scan(QrCode.encode(text)), "Length $length")
        }
    }

    @Test
    fun nonAsciiTextScans() {
        assertEquals("Café 26-00000042", scan(QrCode.encode("Café 26-00000042")))
    }

    @Test
    fun tooLongIsRefused() {
        assertFailsWith<IllegalArgumentException> { QrCode.encode("x".repeat(400)) }
    }
}
