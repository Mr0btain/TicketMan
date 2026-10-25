package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.StoredValueDatabase
import org.mr0btain.ticketman.data.giftcard.GiftCardCode
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.server.request.PaymentRequest
import kotlin.uuid.Uuid

/**
 * Payments made with store credit or a gift card: checked before a transaction starts, so the answer says what's
 * short, then spent inside it, where a balance spent meanwhile throws [ShortOfValue] and undoes the lot
 * @param money Formats an amount in the shop's currency, for messages
 */
class StoredValuePayments(
    private val values: StoredValueDatabase,
    private val money: (Long) -> String
) {
    /** What's wrong with taking these payments for an invoice whose customer is [customerIdentifier], or null */
    fun problem(payments: List<PaymentRequest>, customerIdentifier: Uuid?): String? {
        val credit = payments.filter { it.method == PaymentMethod.STORE_CREDIT }.sumOf { it.amountCents }
        if (credit > 0) {
            if (customerIdentifier == null) return "Store credit needs a customer, add one first"
            val has = values.creditOf(customerIdentifier) ?: 0
            if (has < credit) return "Only ${money(has)} of store credit left"
        }

        val cards = payments.filter { it.method == PaymentMethod.GIFT_CARD }
        cards.forEach { GiftCardCode.problem(it.reference)?.let { problem -> return problem } }
        cards.groupBy { GiftCardCode.normalize(it.reference) }.forEach { (code, spends) ->
            val card = values.giftCard(code) ?: return "There's no gift card $code"
            val spending = spends.sumOf { it.amountCents }
            if (card.balanceCents < spending) return "Only ${money(card.balanceCents)} left on gift card $code"
        }

        return null
    }

    /** The payment as saved: a gift card's code cleaned up */
    fun normalized(payment: Payment): Payment =
        if (payment.method == PaymentMethod.GIFT_CARD) payment.copy(reference = GiftCardCode.normalize(payment.reference)) else payment

    /**
     * Spends what [payment] takes from a balance, nothing for money. Inside a transaction
     * @param label The invoice, for the credit history
     */
    fun take(payment: Payment, customerIdentifier: Uuid?, invoiceIdentifier: Uuid, label: String, by: String) {
        val taken = when (payment.method) {
            PaymentMethod.STORE_CREDIT -> customerIdentifier != null &&
                values.changeCredit(customerIdentifier, -payment.amountCents, "Spent on $label", invoiceIdentifier, by)
            PaymentMethod.GIFT_CARD -> values.spendGiftCard(payment.reference, payment.amountCents)
            else -> true
        }
        if (!taken) throw ShortOfValue(if (payment.method == PaymentMethod.STORE_CREDIT) "Not enough store credit left" else "Not enough left on gift card ${payment.reference}")
    }

    /** Puts back what [payment] took, for a payment taken off an open invoice. Inside a transaction */
    fun giveBack(payment: Payment, customerIdentifier: Uuid?, invoiceIdentifier: Uuid, label: String, by: String) {
        when (payment.method) {
            PaymentMethod.STORE_CREDIT -> customerIdentifier?.let {
                values.changeCredit(it, payment.amountCents, "Payment taken off $label", invoiceIdentifier, by)
            }
            PaymentMethod.GIFT_CARD -> values.spendGiftCard(payment.reference, -payment.amountCents)
            else -> {}
        }
    }

    /** A balance was spent between the check and the transaction */
    class ShortOfValue(override val message: String) : Exception(message)
}
