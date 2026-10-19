package org.mr0btain.ticketman.components.print

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

/**
 * Draws the printouts into images the way they'd go to a printer, and reads the QR code back off the page
 */
class Java2DPrintLayoutTest {
    private val shop = ShopProfile(name = "Fix It Shop", street = "1 Main St", city = "Springfield", phone = "555-0100")
    private val ticket = Ticket(
        number = TicketNumber(2026, 42),
        device = Device("Apple", "iPhone 14", "SN1"),
        problem = "Cracked screen and the battery drains fast when it's cold out",
        accessories = "Case"
    )
    private val signature = TicketSignature(
        strokes = listOf(listOf(SignaturePoint(0.1f, 0.6f), SignaturePoint(0.3f, 0.3f), SignaturePoint(0.6f, 0.7f), SignaturePoint(0.9f, 0.4f))),
        aspectRatio = 3f,
        signedName = "Jane Doe",
        terms = "Not responsible for data loss. Devices left over 90 days may be recycled.",
        signedAt = 1_790_000_000_000,
        takenBy = "tech"
    )

    // Renders one page at [scale] times the point size, white background
    private fun render(layout: Java2DPrintLayout, paper: PaperSize, page: Int = 0, scale: Double = 4.0): BufferedImage {
        val format = PageFormat().apply {
            this.paper = Paper().apply {
                val width = if (paper == PaperSize.RECEIPT_80MM) 80 / 25.4 * 72 else 612.0
                val height = if (paper == PaperSize.RECEIPT_80MM) layout.contentHeight + 23 else 792.0
                setSize(width, height)
                val margin = if (paper == PaperSize.RECEIPT_80MM) 4 / 25.4 * 72 else 36.0
                setImageableArea(margin, margin, width - margin * 2, height - margin * 2)
            }
        }

        val image = BufferedImage((format.width * scale).toInt(), (format.height * scale).toInt(), BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.scale(scale, scale)

        assertEquals(Printable.PAGE_EXISTS, layout.print(graphics, format, page))
        graphics.dispose()

        return image
    }

    private fun scanQr(image: BufferedImage): String {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
    }

    @Test
    fun receiptSizedClaimSlipIsOneLongPageWithAScannableQr() {
        val slip = PrintTemplates.claimSlip(shop, ticket, Customer(name = "Jane Doe", phone = "555-0199"), null, signature)
        val layout = Java2DPrintLayout(slip, PaperSize.RECEIPT_80MM)

        assertEquals(1, layout.pageCount)
        assertTrue(layout.contentHeight > 300, "Slip is only ${layout.contentHeight}pt long")

        val image = render(layout, PaperSize.RECEIPT_80MM)

        // Kept for looking at by eye
        File(System.getProperty("java.io.tmpdir"), "ticketman-claim-slip-80mm.png").also { ImageIO.write(image, "png", it) }

        assertEquals("26-00000042", scanQr(image))
    }

    @Test
    fun letterClaimSlipFitsOnOnePage() {
        val slip = PrintTemplates.claimSlip(shop, ticket, Customer(name = "Jane Doe"), null, signature)
        val layout = Java2DPrintLayout(slip, PaperSize.LETTER)

        assertEquals(1, layout.pageCount)

        val image = render(layout, PaperSize.LETTER, scale = 2.0)
        assertEquals("26-00000042", scanQr(image))

        File(System.getProperty("java.io.tmpdir"), "ticketman-claim-slip-letter.png").also { ImageIO.write(image, "png", it) }
    }

    @Test
    fun longLetterDocumentsSpillOntoMorePages() {
        val long = PrintDocument("Long", List(120) { PrintBlock.Field("Line $it", "Something worth printing") })
        val layout = Java2DPrintLayout(long, PaperSize.LETTER)

        assertTrue(layout.pageCount > 1)
        render(layout, PaperSize.LETTER, page = layout.pageCount - 1, scale = 1.0)
        assertEquals(Printable.NO_SUCH_PAGE, layout.print(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics(), PageFormat(), layout.pageCount))
    }

    @Test
    fun deviceTagRendersOnAReceipt() {
        val layout = Java2DPrintLayout(PrintTemplates.deviceTag(ticket, Customer(name = "Jane Doe")), PaperSize.RECEIPT_80MM)
        assertEquals("26-00000042", scanQr(render(layout, PaperSize.RECEIPT_80MM)))
    }
}
