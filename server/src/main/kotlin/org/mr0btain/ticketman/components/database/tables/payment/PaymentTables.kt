package org.mr0btain.ticketman.components.database.tables.payment

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.payment.PaymentSettings

/**
 * A single row with how the shop takes cards, the processor's keys included
 */
object PaymentSettingsTable : IntIdTable("payment_settings") {
    val settings = json<PaymentSettings>("settings", Json { ignoreUnknownKeys = true })
}

/**
 * Every go at taking a card through the processor. [invoice] isn't a foreign key, so a charge stays on record
 * after its invoice is deleted
 */
object CardChargeTable : UuidTable("card_charges") {
    val kind = text("kind")
    val provider = text("provider")
    val status = text("status")
    val amountCents = long("amount_cents")
    val currency = text("currency")
    val invoice = uuid("invoice_id").nullable().index()
    val invoiceLabel = text("invoice_label").default("")
    val reader = text("reader_id").default("")
    val readerName = text("reader_name").default("")
    val url = text("url").default("")
    val providerReference = text("provider_reference").default("").index()
    val paymentReference = text("payment_reference").default("")
    val payment = uuid("payment_id").nullable()
    val error = text("error").default("")
    val createdAt = long("created_at")
    val finishedAt = long("finished_at").nullable()
    val checkedAt = long("checked_at").default(0)
    val createdBy = text("created_by").default("")

    // Added with tips and surcharges: the parts of the amount that don't go on the invoice
    val tipCents = long("tip_cents").default(0)
    val surchargeCents = long("surcharge_cents").default(0)

    init {
        index("card_charges_status", false, status, createdAt)
    }
}
