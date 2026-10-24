package org.mr0btain.ticketman.components.print

import org.mr0btain.ticketman.components.utils.formatDate
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.StatusLink
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.BadgeCode
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.response.DailyReport

/** Under [QrCode]'s 213, so a long public address falls back to the ticket number rather than failing */
private const val QR_MAX_BYTES = 200

/** What can be printed from the counter */
enum class PrintTemplate(val label: String) {
    CLAIM_SLIP("Claim slip"),
    RECEIPT("Receipt"),
    DEVICE_TAG("Device tag")
}

/**
 * Builds the counter's printouts. They only describe what goes on the page, the platform printers lay them out
 */
object PrintTemplates {
    /**
     * Given to the customer at check in: the ticket number and QR, what was left, when it's promised, and what
     * they signed. Once the shop has a public address the QR is the ticket's status page link, which the counter
     * search also takes, otherwise it's the ticket number
     */
    fun claimSlip(shop: ShopProfile, ticket: Ticket, customer: Customer?, invoice: Invoice?, signature: TicketSignature?): PrintDocument {
        val link = StatusLink.url(shop.publicUrl, ticket)?.takeIf { it.encodeToByteArray().size <= QR_MAX_BYTES }
        val lookup = "${shop.publicUrl.trim().trimEnd('/')}${ApiRoutes.STATUS}"

        val blocks = shopHeader(shop) + listOfNotNull(
            PrintBlock.Divider,
            PrintBlock.Heading("Claim Slip"),
            PrintBlock.Heading(ticket.label, large = true),
            PrintBlock.Qr(link ?: ticket.label),
            link?.let { PrintBlock.Text("Scan to check on your repair, or go to $lookup", small = true, centered = true) },
            PrintBlock.Gap
        ) + customerFields(customer) + deviceFields(ticket) + listOfNotNull(
            PrintBlock.Field("Checked in", formatDateTime(ticket.identifier.timestampMillis())),
            ticket.promisedAt?.let { PrintBlock.Field("Promised", formatDate(it)) },
            ticket.estimateCents?.let { PrintBlock.Field("Estimate", Money.format(it, shop.currencySymbol)) },
            invoice?.paidCents?.takeIf { it > 0 }?.let { PrintBlock.Field("Deposit paid", Money.format(it, shop.currencySymbol)) }
        ) + signatureBlocks(signature) + listOf(
            PrintBlock.Divider,
            PrintBlock.Text("Bring this slip or your ticket number when you pick up.", small = true, centered = true)
        )

        return PrintDocument("Claim slip ${ticket.label}", blocks)
    }

    /**
     * Given to the customer at pickup: every charge, the totals and how it was paid
     */
    fun receipt(shop: ShopProfile, ticket: Ticket, customer: Customer?, invoice: Invoice): PrintDocument {
        val details = listOfNotNull(
            PrintBlock.Field("Invoice", invoice.label),
            PrintBlock.Field("Ticket", ticket.label),
            PrintBlock.Field("Date", formatDateTime(invoice.paidAt ?: invoice.createdAt)),
            customer?.let { PrintBlock.Field("Customer", it.name) },
            PrintBlock.Field("Device", ticket.device.toString().trim())
        )

        return PrintDocument("Receipt ${invoice.label}", shopHeader(shop) + PrintBlock.Divider + PrintBlock.Heading("Receipt") + details + money(shop, invoice))
    }

    /**
     * The invoice on its own, to email or file: like the [receipt], with the customer's contact details and what's
     * still owed. Made on the server as a PDF (`GET /api/invoice/{id}/pdf`)
     * @param ticket Null once the ticket is deleted, the invoice's [Invoice.ticketLabel] stands in
     */
    fun invoice(shop: ShopProfile, ticket: Ticket?, customer: Customer?, invoice: Invoice): PrintDocument {
        val address = customer?.address?.let { listOf(it.street, listOf(it.city, it.state, it.zip).filter(String::isNotBlank).joinToString(" ")) }
            ?.filter { it.isNotBlank() }?.joinToString("\n").orEmpty()

        val details = listOfNotNull(
            PrintBlock.Field("Invoice", invoice.label),
            PrintBlock.Field("Date", formatDate(invoice.createdAt)),
            PrintBlock.Field(
                "Status",
                when (invoice.status) {
                    InvoiceStatus.PAID -> "Paid ${invoice.paidAt?.let { formatDate(it) }.orEmpty()}".trim()
                    InvoiceStatus.VOID -> "Void"
                    InvoiceStatus.OPEN -> "Due"
                }
            ),
            PrintBlock.Field("Ticket", ticket?.label ?: invoice.ticketLabel.ifBlank { "-" }),
            PrintBlock.Field("Device", ticket?.device?.toString()?.trim().orEmpty().ifBlank { "-" }),
            PrintBlock.Gap,
            customer?.let { PrintBlock.Field("Bill to", it.name) },
            address.takeIf { it.isNotBlank() }?.let { PrintBlock.Field("", it) },
            customer?.phone?.takeIf { it.isNotBlank() }?.let { PrintBlock.Field("Phone", it) },
            customer?.email?.takeIf { it.isNotBlank() }?.let { PrintBlock.Field("Email", it) }
        )

        return PrintDocument("Invoice ${invoice.label}", shopHeader(shop) + PrintBlock.Divider + PrintBlock.Heading("Invoice") + details + money(shop, invoice))
    }

    // The charges, totals, payments and the shop's footer, the same on a receipt and an invoice
    private fun money(shop: ShopProfile, invoice: Invoice): List<PrintBlock> {
        val charges = invoice.charges.map { charge ->
            val quantity = if (charge.quantity == 1.0) "" else "${formatQuantity(charge.quantity)} x "
            AmountRow("$quantity${charge.description}", Money.format(charge.totalCents, shop.currencySymbol))
        }

        val totals = listOfNotNull(
            AmountRow("Subtotal", Money.format(invoice.subtotalCents, shop.currencySymbol)),
            invoice.discountCents.takeIf { it > 0 }?.let { AmountRow("Discount", "-${Money.format(it, shop.currencySymbol)}") },
            AmountRow("Tax (${invoice.taxRateBasisPoints / 100.0}%)", Money.format(invoice.taxCents, shop.currencySymbol)),
            AmountRow("Total", Money.format(invoice.totalCents, shop.currencySymbol), bold = true)
        )

        val payments = invoice.payments.map { payment ->
            AmountRow("Paid ${formatEnumName(payment.method.name).lowercase()}", Money.format(payment.amountCents, shop.currencySymbol))
        } + listOfNotNull(
            invoice.balanceCents.takeIf { it < 0 }?.let { AmountRow("Change", Money.format(-it, shop.currencySymbol), bold = true) },
            invoice.balanceCents.takeIf { it > 0 }?.let { AmountRow("Still owed", Money.format(it, shop.currencySymbol), bold = true) }
        )

        return listOfNotNull(
            PrintBlock.Divider,
            PrintBlock.Amounts(charges.ifEmpty { listOf(AmountRow("No charge", Money.format(0, shop.currencySymbol))) }),
            PrintBlock.Divider,
            PrintBlock.Amounts(totals),
            payments.takeIf { it.isNotEmpty() }?.let { PrintBlock.Amounts(it) }
        ) + listOfNotNull(
            shop.invoiceFooter.takeIf { it.isNotBlank() }?.let { PrintBlock.Text(it, small = true, centered = true) }
        ).let { footer -> if (footer.isEmpty()) footer else listOf(PrintBlock.Divider) + footer }
    }

    /**
     * Stuck to the device so it can be matched to its ticket in the back
     */
    fun deviceTag(ticket: Ticket, customer: Customer?): PrintDocument =
        PrintDocument(
            "Device tag ${ticket.label}",
            listOfNotNull(
                PrintBlock.Heading(ticket.label, large = true),
                PrintBlock.Qr(ticket.label),
                customer?.let { PrintBlock.Text(it.name, bold = true, centered = true) },
                PrintBlock.Text(ticket.device.toString().trim(), centered = true),
                ticket.promisedAt?.let { PrintBlock.Text("Due ${formatDate(it)}", small = true, centered = true) }
            )
        )

    /**
     * The end of day summary for closing up
     */
    fun dailyReport(shop: ShopProfile, report: DailyReport): PrintDocument {
        val taken = PaymentMethod.entries
            .filter { (report.takenByMethod[it] ?: 0) > 0 }
            .map { AmountRow(formatEnumName(it.name), Money.format(report.takenByMethod[it] ?: 0, shop.currencySymbol)) }

        val blocks = shopHeader(shop) + listOf(
            PrintBlock.Divider,
            PrintBlock.Heading("End of Day"),
            PrintBlock.Text(report.date, centered = true),
            PrintBlock.Divider,
            PrintBlock.Text("Money taken", bold = true),
            PrintBlock.Amounts(taken.ifEmpty { listOf(AmountRow("Nothing taken", Money.format(0, shop.currencySymbol))) }),
            PrintBlock.Amounts(listOf(AmountRow("Total taken (${report.paymentCount} payments)", Money.format(report.takenCents, shop.currencySymbol), bold = true))),
            PrintBlock.Gap,
            PrintBlock.Text("Sales", bold = true),
            PrintBlock.Amounts(
                listOf(
                    AmountRow("Invoices paid", report.invoicesPaid.toString()),
                    AmountRow("Sales", Money.format(report.salesCents, shop.currencySymbol)),
                    AmountRow("Tax in sales", Money.format(report.taxCents, shop.currencySymbol))
                )
            ),
            PrintBlock.Gap,
            PrintBlock.Text("Tickets", bold = true),
            PrintBlock.Amounts(
                listOf(
                    AmountRow("Checked in", report.ticketsCheckedIn.toString()),
                    AmountRow("Picked up", report.ticketsPickedUp.toString()),
                    AmountRow("Waiting for pickup now", report.waitingForPickupNow.toString()),
                    AmountRow("Overdue now", report.overdueNow.toString())
                )
            )
        )

        return PrintDocument("End of day ${report.date}", blocks)
    }

    private fun shopHeader(shop: ShopProfile): List<PrintBlock> {
        val cityLine = listOf(shop.city, shop.state, shop.zip).filter { it.isNotBlank() }.joinToString(" ")

        return listOfNotNull(
            PrintBlock.Heading(shop.name.ifBlank { "Repair Shop" }),
            shop.street.takeIf { it.isNotBlank() }?.let { PrintBlock.Text(it, small = true, centered = true) },
            cityLine.takeIf { it.isNotBlank() }?.let { PrintBlock.Text(it, small = true, centered = true) },
            listOf(shop.phone, shop.email, shop.website).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                ?.let { PrintBlock.Text(it.joinToString("  "), small = true, centered = true) }
        )
    }

    private fun customerFields(customer: Customer?): List<PrintBlock> =
        if (customer == null) emptyList()
        else listOfNotNull(
            PrintBlock.Field("Customer", customer.name),
            customer.phone.takeIf { it.isNotBlank() }?.let { PrintBlock.Field("Phone", it) }
        )

    private fun deviceFields(ticket: Ticket): List<PrintBlock> =
        listOfNotNull(
            PrintBlock.Field("Device", ticket.device.toString().trim().ifBlank { "-" }),
            ticket.device.serial.takeIf { it.isNotBlank() }?.let { PrintBlock.Field("Serial", it) },
            PrintBlock.Field("Problem", ticket.problem.ifBlank { "-" }),
            ticket.condition.takeIf { it.isNotBlank() }?.let { PrintBlock.Field("Condition", it) },
            ticket.accessories.takeIf { it.isNotBlank() }?.let { PrintBlock.Field("Left with it", it) }
        )

    private fun signatureBlocks(signature: TicketSignature?): List<PrintBlock> {
        if (signature == null) return emptyList()

        return listOfNotNull(
            PrintBlock.Divider,
            signature.terms.takeIf { it.isNotBlank() }?.let { PrintBlock.Text(it, small = true) },
            PrintBlock.Signature(signature.strokes, signature.aspectRatio, "${signature.signedName}, ${formatDateTime(signature.signedAt)}")
        )
    }

    /**
     * A staff member's ID card: the shop, their photo, name and username, and the QR with their badge code, for
     * clocking in once that's built. Prints on either paper; a receipt makes a strip about card width
     * @param photo Their profile photo, null to leave it off
     */
    fun idCard(shop: ShopProfile, user: User, badgeCode: String, photo: PrintBlock.Photo?): PrintDocument {
        val name = user.name.trim().ifBlank { user.username }

        return PrintDocument(
            "ID card $name",
            listOfNotNull(
                PrintBlock.Heading(shop.name.ifBlank { "Staff" }),
                PrintBlock.Divider,
                photo,
                PrintBlock.Heading(name, large = true),
                PrintBlock.Text(user.username, centered = true),
                PrintBlock.Gap,
                PrintBlock.Qr(BadgeCode.qrText(badgeCode)),
                PrintBlock.Text(BadgeCode.readable(badgeCode), small = true, centered = true)
            )
        )
    }
}
