package org.mr0btain.ticketman.components.service

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawerDefaultFonts
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.print.Java2DPrintLayout
import org.mr0btain.ticketman.components.print.PrintDocument
import org.mr0btain.ticketman.components.print.PrintTemplates
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.logging.AppLogger
import java.io.OutputStream
import java.io.Writer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Invoices leaving the server as files: one as a PDF to email or file, and a range as CSV for the books
 * @param zone The shop's time zone, which the export's dates and day boundaries follow
 */
@Suppress("PrivatePropertyName")
class InvoiceDocuments(
    private val invoices: InvoiceDatabase,
    private val tickets: TicketDatabase,
    private val customers: CustomerDatabase,
    private val shop: ShopDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private val TAG = "InvoiceDocuments"
    private val PDF_TAG = "$TAG:Pdf"

    /**
     * The invoice laid out like the printed one on a letter page, with what's needed to send it
     * @return Null when the invoice isn't there. One kept after its ticket was deleted shows the ticket's number
     */
    fun pdf(identifier: Uuid): InvoicePdf? {
        val invoice = invoices.getById(identifier) ?: return null
        val ticket = invoice.ticketIdentifier?.let { tickets.getById(it) }
        val customerIdentifier = invoice.customerIdentifier ?: ticket?.let { tickets.getCustomer(it.identifier) }?.identifier
        val customer = customerIdentifier?.let { customers.getById(it) }

        // With the address and contacts of the location it was made at
        val document = PrintTemplates.invoice(shop.at(invoice.locationIdentifier), ticket, customer, invoice)
        return InvoicePdf(fileName = "${invoice.label}.pdf", document = document)
    }

    /** Writes [document] to [out] as a PDF, one page per printed page */
    fun writePdf(document: PrintDocument, out: OutputStream) {
        val layout = Java2DPrintLayout(document, PaperSize.LETTER)
        val width = layout.pageWidth.toFloat()
        val height = layout.pageHeight.toFloat()

        PDDocument().use { pdf ->
            pdf.documentInformation.title = document.title

            repeat(layout.pageCount) { index ->
                val page = PDPage(PDRectangle(width, height))
                pdf.addPage(page)

                // Text the standard PDF fonts can show stays text, anything else is drawn as shapes
                val graphics = PdfBoxGraphics2D(pdf, width, height)
                graphics.setFontTextDrawer(PdfBoxGraphics2DFontTextDrawerDefaultFonts())
                graphics.translate(layout.pageMargin, layout.pageMargin)
                layout.drawPage(graphics, index)
                graphics.dispose()

                PDPageContentStream(pdf, page).use { it.drawForm(graphics.xFormObject) }
            }

            pdf.save(out)
        }

        AppLogger.d(PDF_TAG, "${document.title}, ${layout.pageCount} pages")
    }

    /**
     * Invoices made from [from] to [to], both days included, in the shop's time zone
     * @return Null when the read failed
     */
    fun inRange(from: LocalDate, to: LocalDate, location: Uuid? = null): List<Invoice>? {
        val start = from.atStartOfDay(zone()).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()

        return invoices.getCreatedBetween(start, end, location)
    }

    /**
     * Writes [list] as CSV, one row per invoice, amounts as plain numbers so a spreadsheet adds them up. Quoted the
     * way spreadsheets read it (RFC 4180), and a cell starting with = + - @ gets a leading ' so it isn't run as a
     * formula
     */
    fun writeCsv(list: List<Invoice>, out: Writer) {
        val zone = zone()
        val ticketLabels = mutableMapOf<Uuid, String>()
        val customerNames = mutableMapOf<Uuid, String>()

        fun date(millis: Long?) = millis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }.orEmpty()
        fun money(cents: Long) = Money.format(cents, symbol = "")

        out.write(CSV_HEADER.joinToString(",", postfix = "\r\n") { csvCell(it) })

        list.forEach { invoice ->
            // A sale has no ticket, its column says so
            val ticket = invoice.ticketIdentifier?.let { identifier ->
                ticketLabels.getOrPut(identifier) { tickets.getById(identifier)?.label ?: invoice.ticketLabel }
            } ?: SALE_LABEL
            val customerIdentifier = invoice.customerIdentifier ?: invoice.ticketIdentifier?.let { tickets.getCustomer(it)?.identifier }
            val customer = customerIdentifier?.let { id -> customerNames.getOrPut(id) { customers.getById(id)?.name.orEmpty() } }.orEmpty()

            val row = listOf(
                invoice.label,
                date(invoice.createdAt),
                invoice.status.name.lowercase(),
                date(invoice.paidAt),
                ticket,
                customer,
                money(invoice.subtotalCents),
                money(invoice.discountCents),
                money(invoice.taxCents),
                money(invoice.totalCents),
                money(invoice.paidCents),
                money(invoice.balanceCents),
                money(invoice.refundedCents)
            )
            out.write(row.joinToString(",", postfix = "\r\n") { csvCell(it) })
        }
    }

    companion object {
        /** In the Ticket column for a sale without one */
        const val SALE_LABEL = "Sale"

        val CSV_HEADER = listOf(
            "Invoice", "Date", "Status", "Paid on", "Ticket", "Customer", "Subtotal", "Discount", "Tax", "Total", "Paid", "Balance", "Refunded"
        )

        /** One CSV cell, quoted when it has to be */
        fun csvCell(value: String): String {
            // Amounts are written by us and can be negative, everything else typed by someone could be a formula
            val safe = if (value.firstOrNull() in FORMULA_STARTS && value.toDoubleOrNull() == null) "'$value" else value
            return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${safe.replace("\"", "\"\"")}\"" else safe
        }

        private val FORMULA_STARTS = setOf('=', '+', '-', '@')
    }
}

/** An invoice ready to be written as a PDF */
class InvoicePdf(
    val fileName: String,
    val document: PrintDocument
)
