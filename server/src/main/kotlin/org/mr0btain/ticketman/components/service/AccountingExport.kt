package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.service.InvoiceDocuments.Companion.csvCell
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.AccountingSettings
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.components.utils.formatQuantity
import java.io.Writer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Invoices and payments as CSV for accounting software: QuickBooks Online's and Xero's invoice imports, one row per
 * line of each invoice paid in the days, and a payments list for matching against the bank. Names come from the
 * shop's [AccountingSettings]. The books work the tax out again from the rates, so a total can be a cent off ours
 * when the discount is shared between rates
 * @param zone The shop's time zone, which the dates and day boundaries follow
 */
@OptIn(ExperimentalUuidApi::class)
class AccountingExport(
    private val invoices: InvoiceDatabase,
    private val tickets: TicketDatabase,
    private val customers: CustomerDatabase,
    private val shop: ShopDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    /** Invoices paid from [from] to [to], both included, oldest first. Null when the read failed */
    fun paidInRange(from: LocalDate, to: LocalDate, location: Uuid? = null): List<Invoice>? =
        invoices.getPaidBetween(start(from), start(to.plusDays(1)), location)

    /** Every payment and refund received from [from] to [to], both included, oldest first, with its invoice */
    fun paymentsInRange(from: LocalDate, to: LocalDate, location: Uuid? = null): List<Pair<Invoice, Payment>>? {
        val start = start(from)
        val end = start(to.plusDays(1))
        return invoices.withPaymentsBetween(start, end, location)
            ?.flatMap { invoice -> invoice.payments.filter { it.receivedAt in start until end }.map { invoice to it } }
            ?.sortedBy { it.second.receivedAt }
    }

    /**
     * QuickBooks Online's invoice import: InvoiceNo, Customer, InvoiceDate, DueDate, Memo, Item(Product/Service),
     * ItemDescription, ItemQuantity, ItemRate, ItemAmount, Taxable, TaxRate. A discount is a line of its own
     */
    fun writeQuickBooks(list: List<Invoice>, out: Writer) {
        val settings = shop.get().accounting
        val names = Names()

        out.row(QUICKBOOKS_HEADER)
        list.forEach { invoice ->
            val customer = names.customer(invoice)
            val (date, due) = dates(invoice, settings)
            val memo = names.memo(invoice)

            lines(invoice).forEach { (charge, rate) ->
                out.row(
                    listOf(
                        invoice.label,
                        customer?.name ?: settings.walkInCustomer,
                        date,
                        due,
                        memo,
                        if (charge == null) DISCOUNT else settings.item(charge.type),
                        charge?.description ?: DISCOUNT,
                        charge?.let { formatQuantity(it.quantity) } ?: "1",
                        money(charge?.unitPriceCents ?: -invoice.discountCents),
                        money(charge?.totalCents ?: -invoice.discountCents),
                        if (rate != null) "Y" else "N",
                        rate?.let { "${formatQuantity(it / 100.0)}%" }.orEmpty()
                    )
                )
            }
        }
    }

    /**
     * Xero's sales invoice import: ContactName, EmailAddress, InvoiceNumber, Reference, InvoiceDate, DueDate,
     * Description, Quantity, UnitAmount, AccountCode, TaxType, Currency left to the organisation's. A discount is a
     * line of its own
     */
    fun writeXero(list: List<Invoice>, out: Writer) {
        val settings = shop.get().accounting
        val names = Names()

        out.row(XERO_HEADER)
        list.forEach { invoice ->
            val customer = names.customer(invoice)
            val (date, due) = dates(invoice, settings)
            val reference = names.memo(invoice)

            lines(invoice).forEach { (charge, rate) ->
                out.row(
                    listOf(
                        customer?.name ?: settings.walkInCustomer,
                        customer?.email.orEmpty(),
                        invoice.label,
                        reference,
                        date,
                        due,
                        charge?.description ?: DISCOUNT,
                        charge?.let { formatQuantity(it.quantity) } ?: "1",
                        money(charge?.unitPriceCents ?: -invoice.discountCents),
                        settings.account(charge?.type ?: ChargeType.OTHER),
                        when {
                            charge != null -> settings.taxType(charge)
                            rate != null -> settings.taxType
                            else -> settings.exemptTaxType
                        }
                    )
                )
            }
        }
    }

    /** Date, Invoice, Customer, Method, Amount, Reference, Note, Received by. Refunds are negative */
    fun writePayments(list: List<Pair<Invoice, Payment>>, out: Writer) {
        val settings = shop.get().accounting
        val names = Names()

        out.row(PAYMENTS_HEADER)
        list.forEach { (invoice, payment) ->
            out.row(
                listOf(
                    date(payment.receivedAt, settings),
                    invoice.label,
                    names.customer(invoice)?.name ?: settings.walkInCustomer,
                    formatEnumName(payment.method.name),
                    money(payment.amountCents),
                    payment.reference,
                    payment.note,
                    payment.receivedBy
                )
            )
        }
    }

    /**
     * Each line with the tax rate it's charged at (null when it isn't taxed), then a discount line when there's
     * one. The discount comes off the taxed lines first, so it's taxed at the main rate when anything was
     */
    private fun lines(invoice: Invoice): List<Pair<Charge?, Int?>> {
        val rates = invoice.taxRates.associate { it.identifier to it.basisPoints }
        val charged = invoice.charges.map { charge ->
            charge to if (charge.taxable && charge.type != ChargeType.GIFT_CARD) {
                charge.taxClass?.let { rates[it] } ?: invoice.taxRateBasisPoints
            } else null
        }
        val discount = if (invoice.discountCents > 0) {
            listOf(null to if (charged.any { it.second != null }) invoice.taxRateBasisPoints else null)
        } else emptyList()

        return charged + discount
    }

    private fun dates(invoice: Invoice, settings: AccountingSettings): Pair<String, String> {
        val paid = invoice.paidAt ?: invoice.createdAt
        return date(paid, settings) to date(paid + settings.dueDays * DAY_MILLIS, settings)
    }

    private fun date(millis: Long, settings: AccountingSettings): String {
        val day = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()
        return settings.dateFormat.format(day.year, day.monthValue, day.dayOfMonth)
    }

    private fun start(day: LocalDate): Long = day.atStartOfDay(zone()).toInstant().toEpochMilli()

    private fun money(cents: Long) = Money.format(cents, symbol = "")

    private fun Writer.row(cells: List<String>) = write(cells.joinToString(",", postfix = "\r\n") { csvCell(it) })

    /** Customers and ticket numbers looked up once each */
    private inner class Names {
        private val customerCache = mutableMapOf<Uuid, Customer?>()
        private val ticketLabels = mutableMapOf<Uuid, String>()

        fun customer(invoice: Invoice): Customer? {
            val identifier = invoice.customerIdentifier ?: invoice.ticketIdentifier?.let { tickets.getCustomer(it)?.identifier } ?: return null
            return customerCache.getOrPut(identifier) { customers.getById(identifier) }
        }

        /** Its ticket's number, or that it was a counter sale */
        fun memo(invoice: Invoice): String =
            invoice.ticketIdentifier?.let { identifier ->
                "Ticket " + ticketLabels.getOrPut(identifier) { tickets.getById(identifier)?.label ?: invoice.ticketLabel }
            } ?: "Counter sale"
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val DISCOUNT = "Discount"

        val QUICKBOOKS_HEADER = listOf(
            "InvoiceNo", "Customer", "InvoiceDate", "DueDate", "Memo", "Item(Product/Service)", "ItemDescription",
            "ItemQuantity", "ItemRate", "ItemAmount", "Taxable", "TaxRate"
        )

        val XERO_HEADER = listOf(
            "*ContactName", "EmailAddress", "*InvoiceNumber", "Reference", "*InvoiceDate", "*DueDate", "*Description",
            "*Quantity", "*UnitAmount", "*AccountCode", "*TaxType"
        )

        val PAYMENTS_HEADER = listOf("Date", "Invoice", "Customer", "Method", "Amount", "Reference", "Note", "Received by")
    }
}
