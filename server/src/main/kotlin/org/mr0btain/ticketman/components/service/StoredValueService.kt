package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.StoredValueDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.server.request.BalanceAdjustRequest
import kotlin.uuid.Uuid

/**
 * Store credit and gift card balances changed by hand, admin only. Spending them is the invoice service's
 */
class StoredValueService(
    private val values: StoredValueDatabase,
    private val customers: CustomerDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase
) {
    /** Gives or takes store credit, written down with the reason. A balance can't go below zero */
    fun adjustCredit(customerIdentifier: Uuid, request: BalanceAdjustRequest, by: String, actor: Uuid?): AccountResult<Customer> {
        val customer = customers.getById(customerIdentifier) ?: return AccountResult.NotFound
        problem(request)?.let { return AccountResult.Invalid(it) }

        val changed = transaction {
            values.changeCredit(customerIdentifier, request.amountCents, request.reason.trim(), null, by).also { done ->
                if (done) auditLog.log(actor, AuditAction.UPDATE, "customer", customerIdentifier.toString(), "Store credit ${signed(request.amountCents)} for ${customer.name}: ${request.reason.trim()}")
            }
        }
        if (!changed) return AccountResult.Conflict("${customer.name} only has ${money(customer.creditCents)} of store credit")

        return AccountResult.Done(customers.getById(customerIdentifier)!!)
    }

    /** Corrects a gift card's balance, written to the audit log with the reason */
    fun adjustGiftCard(code: String, request: BalanceAdjustRequest, actor: Uuid?): AccountResult<GiftCard> {
        val card = values.giftCard(code) ?: return AccountResult.NotFound
        problem(request)?.let { return AccountResult.Invalid(it) }

        val changed = transaction {
            // Spending a negative amount puts it on, the same single update
            values.spendGiftCard(card.code, -request.amountCents).also { done ->
                if (done) auditLog.log(actor, AuditAction.UPDATE, "gift card", card.identifier.toString(), "Gift card ${card.code} ${signed(request.amountCents)}: ${request.reason.trim()}")
            }
        }
        if (!changed) return AccountResult.Conflict("Gift card ${card.code} only has ${money(card.balanceCents)} on it")

        return AccountResult.Done(values.giftCard(card.code)!!)
    }

    private fun problem(request: BalanceAdjustRequest): String? = when {
        request.amountCents == 0L -> "Change it by something other than zero"
        request.reason.isBlank() -> "Give a reason"
        else -> null
    }

    private fun money(cents: Long): String = Money.format(cents, shop.get().currencySymbol)

    private fun signed(cents: Long): String = if (cents > 0) "+${money(cents)}" else money(cents)
}
