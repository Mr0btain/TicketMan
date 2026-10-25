package org.mr0btain.ticketman.components.database.tables.storedvalue

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable

/**
 * Every change to a customer's store credit, which itself is `CustomerTable.creditCents`. Goes with the customer
 */
object CustomerCreditTable : UuidTable("customer_credit") {
    val customer = reference("customer_id", CustomerTable, onDelete = ReferenceOption.CASCADE)
    val amountCents = long("amount_cents")
    val balanceAfterCents = long("balance_after_cents")
    val reason = text("reason")
    val invoice = uuid("invoice_id").nullable()
    val at = long("at")
    val by = text("by_user")
}

/** Gift cards, found by [code] */
object GiftCardTable : UuidTable("gift_cards") {
    val code = text("code").uniqueIndex()
    val balanceCents = long("balance_cents")
    val issuedCents = long("issued_cents")
    val createdAt = long("created_at")
    val invoice = uuid("invoice_id").nullable()
    val customer = uuid("customer_id").nullable()
}
