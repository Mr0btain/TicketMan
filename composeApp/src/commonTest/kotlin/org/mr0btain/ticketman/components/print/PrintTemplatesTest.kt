package org.mr0btain.ticketman.components.print

import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import kotlin.test.*
import kotlin.uuid.Uuid

class PrintTemplatesTest {
    private val shop = ShopProfile(name = "Fix It Shop", street = "1 Main St", city = "Springfield", phone = "555-0100", invoiceFooter = "Thanks <3")
    private val customer = Customer(name = "jane:doe", phone = "555-0199")
    private val ticket = Ticket(
        number = TicketNumber(2026, 42),
        device = Device("Apple", "iPhone 14", "SN1"),
        problem = "Cracked screen",
        accessories = "Case",
        estimateCents = 12_000,
        promisedAt = 1_800_000_000_000
    )
    private val signature = TicketSignature(
        strokes = listOf(listOf(SignaturePoint(0.1f, 0.5f), SignaturePoint(0.9f, 0.5f))),
        aspectRatio = 3f,
        signedName = "Jane Doe",
        terms = "Not responsible for data loss.",
        signedAt = 1_790_000_000_000,
        takenBy = "tech"
    )

    @Test
    fun claimSlipHasTheNumberQrAndSignature() {
        val slip = PrintTemplates.claimSlip(shop, ticket, customer, null, signature)

        assertTrue(PrintBlock.Heading("26-00000042", large = true) in slip.blocks)
        assertTrue(PrintBlock.Qr("26-00000042") in slip.blocks)
        assertTrue(PrintBlock.Field("Customer", "Jane Doe") in slip.blocks)
        assertTrue(PrintBlock.Field("Left with it", "Case") in slip.blocks)
        assertTrue(slip.blocks.any { it is PrintBlock.Text && it.text == "Not responsible for data loss." })
        assertTrue(slip.blocks.any { it is PrintBlock.Signature })
    }

    @Test
    fun receiptListsChargesTotalsPaymentsAndChange() {
        val invoice = Invoice(
            ticketIdentifier = ticket.identifier,
            number = InvoiceNumber(2026, 7),
            charges = listOf(
                Charge(type = ChargeType.PART, description = "Screen", quantity = 1.0, unitPriceCents = 8_000),
                Charge(type = ChargeType.LABOR, description = "Install", quantity = 1.5, unitPriceCents = 4_000)
            ),
            payments = listOf(Payment(method = PaymentMethod.CASH, amountCents = 15_000)),
            subtotalCents = 14_000,
            taxRateBasisPoints = 0,
            totalCents = 14_000
        )

        val receipt = PrintTemplates.receipt(shop, ticket, customer, invoice)
        val rows = receipt.blocks.filterIsInstance<PrintBlock.Amounts>().flatMap { it.rows }

        assertTrue(AmountRow("Screen", "$80.00") in rows)
        assertTrue(AmountRow("1.5 x Install", "$60.00") in rows)
        assertTrue(AmountRow("Total", "$140.00", bold = true) in rows)
        assertTrue(AmountRow("Paid cash", "$150.00") in rows)
        assertTrue(AmountRow("Change", "$10.00", bold = true) in rows)
        assertTrue(receipt.blocks.any { it is PrintBlock.Text && it.text == "Thanks <3" })
    }

    @Test
    fun htmlIsSizedForThePaperAndEscaped() {
        val slip = PrintTemplates.claimSlip(shop, ticket, customer, null, signature)

        val receipt = HtmlPrintRenderer.render(slip, PaperSize.RECEIPT_80MM)
        val letter = HtmlPrintRenderer.render(PrintTemplates.receipt(shop, ticket, customer, Invoice(ticketIdentifier = Uuid.random())), PaperSize.LETTER)

        assertTrue("size: 80mm auto" in receipt)
        assertTrue("size: letter" in letter)
        assertTrue("<svg class=\"qr\"" in receipt)
        assertTrue("<polyline" in receipt)
        assertTrue("Thanks &lt;3" in letter)
        assertFalse("Thanks <3" in letter)
    }
}
