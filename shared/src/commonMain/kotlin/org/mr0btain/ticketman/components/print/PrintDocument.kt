package org.mr0btain.ticketman.components.print

import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.ticket.SignaturePoint

/**
 * Something to print, described as blocks from top to bottom. Each platform lays these out for the paper it's
 * printing on, so one template works for an 80mm receipt and a letter page. In shared because the server lays the
 * same documents out as PDFs (Java2DPrintLayout, in jvmMain)
 */
data class PrintDocument(
    val title: String,
    val blocks: List<PrintBlock>
)

sealed interface PrintBlock {
    /** Centered heading. [large] is for the ticket number, big enough to read across the counter */
    data class Heading(val text: String, val large: Boolean = false) : PrintBlock

    data class Text(
        val text: String,
        val bold: Boolean = false,
        val small: Boolean = false,
        val centered: Boolean = false
    ) : PrintBlock

    /** Label on the left, value beside it */
    data class Field(val label: String, val value: String) : PrintBlock

    /** Rows with text on the left and an amount on the right, like invoice lines and totals */
    data class Amounts(val rows: List<AmountRow>) : PrintBlock

    data object Divider : PrintBlock

    data object Gap : PrintBlock

    /** Centered QR code */
    data class Qr(val text: String) : PrintBlock

    /**
     * Centered photo, like the face on an ID card, about the size of a [Qr]
     * @param jpeg The picture, as the server's `display` variant
     * @param aspectRatio Width over height
     */
    class Photo(val jpeg: ByteArray, val aspectRatio: Float) : PrintBlock

    data class Signature(
        val strokes: List<List<SignaturePoint>>,
        val aspectRatio: Float,
        val caption: String
    ) : PrintBlock
}

data class AmountRow(
    val label: String,
    val amount: String,
    val bold: Boolean = false
)
