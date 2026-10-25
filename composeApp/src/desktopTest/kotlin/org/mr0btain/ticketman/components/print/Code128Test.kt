package org.mr0btain.ticketman.components.print

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.Code128Reader
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.*

/**
 * Our Code 128 encoder, read back by ZXing like a scanner would, on its own and printed on a label
 */
class Code128Test {
    // Bars at [module] pixels each, with the quiet zones, on white
    private fun draw(widths: List<Int>, module: Int = 3): BufferedImage {
        val total = (Code128.modules(widths) + Code128.QUIET_ZONE * 2) * module
        val image = BufferedImage(total, 60, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        g.color = Color.BLACK

        var x = Code128.QUIET_ZONE * module
        widths.forEachIndexed { index, width ->
            if (index % 2 == 0) g.fillRect(x, 0, width * module, image.height)
            x += width * module
        }
        g.dispose()
        return image
    }

    private fun scan(image: BufferedImage): String {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        return Code128Reader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
    }

    @Test
    fun everySymbolIsElevenModulesWithEvenBars() {
        assertEquals(107, Code128.PATTERNS.size)
        assertEquals(107, Code128.PATTERNS.toSet().size)
        Code128.PATTERNS.dropLast(1).forEachIndexed { value, pattern ->
            assertEquals(11, pattern.sum(), "symbol $value")
            assertEquals(0, pattern.filterIndexed { index, _ -> index % 2 == 0 }.sum() % 2, "symbol $value bars")
        }
        assertEquals(13, Code128.PATTERNS.last().sum())
    }

    @Test
    fun aScannerReadsWhatItEncodes() {
        listOf("SCR-14", "0194253401", "885909950805", "abc 123!", "A", "12345", "GC-7KQ2-M9XD", "~{}|").forEach { text ->
            assertEquals(text, scan(draw(Code128.encode(text)!!)), text)
        }
    }

    @Test
    fun digitsArePackedInPairs() {
        // Code set C: start, six pairs, check, stop
        assertEquals(11 * 8 + 13, Code128.modules(Code128.encode("885909950805")!!))
        assertEquals(11 * 14 + 13, Code128.modules(Code128.encode("88590995080A")!!))
    }

    @Test
    fun whatCantBeABarcodeIsNull() {
        assertNull(Code128.encode(""))
        assertNull(Code128.encode("café"))
        assertNull(Code128.encode("x".repeat(49)))
    }

    @Test
    fun anItemLabelScansOnALabelPrinter() {
        val item = Inventory(name = "iPhone 14 Screen", priceCents = 12_000, sku = "SCR-14", barcode = "0194253401")
        val layout = Java2DPrintLayout(PrintTemplates.itemLabel(ShopProfile(), item), PaperSize.LABEL)
        assertEquals(1, layout.pageCount)

        val scale = 8.0
        val image = BufferedImage((layout.pageWidth * scale).toInt(), (layout.pageHeight * scale).toInt(), BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        g.scale(scale, scale)
        g.translate(layout.pageMargin, layout.pageMargin)
        layout.drawPage(g, 0)
        g.dispose()

        assertEquals("0194253401", scan(image))

        // No barcode or SKU, no barcode
        assertTrue(PrintTemplates.itemLabel(ShopProfile(), item.copy(sku = "", barcode = "")).blocks.none { it is PrintBlock.Barcode })
        assertEquals("SCR-14", (PrintTemplates.itemLabel(ShopProfile(), item.copy(barcode = "")).blocks.last() as PrintBlock.Barcode).text)
    }

    @Test
    fun theHtmlBarcodeHasEveryBar() {
        val svg = HtmlPrintRenderer.barcodeSvg("SCR-14", "12mm")!!
        val bars = Code128.encode("SCR-14")!!.filterIndexed { index, _ -> index % 2 == 0 }.size
        assertEquals(bars, Regex("M\\d+,0h").findAll(svg).count())
        assertNull(HtmlPrintRenderer.barcodeSvg("café", "12mm"))
    }
}
