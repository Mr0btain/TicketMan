package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.ResultRow
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.storedvalue.CustomerCreditTable
import org.mr0btain.ticketman.components.database.tables.storedvalue.GiftCardTable
import org.mr0btain.ticketman.data.customer.CreditEntry
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.giftcard.GiftCardCode
import org.mr0btain.ticketman.data.shared.PagedRequest
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Store credit and gift cards, the balances the shop holds for customers. Like stock, a balance only changes in a
 * single SQL update that can't take it below zero, so two spends at once can't both go through. Called inside the
 * invoice service's transactions, where a false answer is turned into a throw that undoes everything
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class StoredValueDatabase {
    private val TAG = "StoredValueDatabase"
    private val CREDIT_TAG = "$TAG:Credit"
    private val CREDIT_HISTORY_TAG = "$TAG:CreditHistory"
    private val CARD_TAG = "$TAG:GiftCard"
    private val CARD_LIST_TAG = "$TAG:GiftCards"

    // ------------------------------------------ Store credit ------------------------------------------

    fun creditOf(customerIdentifier: Uuid): Long? =
        dbQuery(CREDIT_TAG) {
            CustomerTable.select(CustomerTable.creditCents).where { CustomerTable.id eq customerIdentifier }.singleOrNull()?.get(CustomerTable.creditCents)
        }

    /**
     * Adds [amountCents] to the customer's credit, or takes it off when negative, and writes it down
     * @return False when the customer isn't there or doesn't have enough to take
     */
    fun changeCredit(customerIdentifier: Uuid, amountCents: Long, reason: String, invoiceIdentifier: Uuid?, by: String): Boolean =
        dbWrite(CREDIT_TAG) {
            val updated = CustomerTable.update(where = {
                val customer = CustomerTable.id eq customerIdentifier
                if (amountCents < 0) customer and (CustomerTable.creditCents greaterEq -amountCents) else customer
            }) { row ->
                row[CustomerTable.creditCents] = CustomerTable.creditCents + amountCents
            }
            if (updated == 0) return@dbWrite false

            val balance = CustomerTable.select(CustomerTable.creditCents).where { CustomerTable.id eq customerIdentifier }.single()[CustomerTable.creditCents]
            CustomerCreditTable.insert { row ->
                row[CustomerCreditTable.id] = Uuid.generateV7()
                row[customer] = customerIdentifier
                row[CustomerCreditTable.amountCents] = amountCents
                row[balanceAfterCents] = balance
                row[CustomerCreditTable.reason] = reason
                row[invoice] = invoiceIdentifier
                row[at] = Clock.System.now().toEpochMilliseconds()
                row[CustomerCreditTable.by] = by
            }
            true
        }

    /** Newest first. Null when the read failed */
    fun creditHistory(customerIdentifier: Uuid, limit: Int): List<CreditEntry>? =
        dbQuery(CREDIT_HISTORY_TAG) {
            CustomerCreditTable
                .selectAll()
                .where { CustomerCreditTable.customer eq customerIdentifier }
                .orderBy(CustomerCreditTable.id to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    CreditEntry(
                        identifier = row[CustomerCreditTable.id].value,
                        amountCents = row[CustomerCreditTable.amountCents],
                        balanceAfterCents = row[CustomerCreditTable.balanceAfterCents],
                        reason = row[CustomerCreditTable.reason],
                        invoiceIdentifier = row[CustomerCreditTable.invoice],
                        at = row[CustomerCreditTable.at],
                        by = row[CustomerCreditTable.by]
                    )
                }
        }

    // ------------------------------------------ Gift cards ------------------------------------------

    /** The card with this code, however it was typed */
    fun giftCard(code: String): GiftCard? =
        dbQuery(CARD_TAG) {
            GiftCardTable.selectAll().where { GiftCardTable.code eq GiftCardCode.normalize(code) }.singleOrNull()?.toGiftCard()
        }

    /**
     * Puts [amountCents] on the card with [code], making it if there's none
     * @return The card as it is now, null when it failed
     */
    fun loadGiftCard(code: String, amountCents: Long, invoiceIdentifier: Uuid?, customerIdentifier: Uuid?): GiftCard? =
        dbQuery(CARD_TAG) {
            val normalized = GiftCardCode.normalize(code)
            val topped = GiftCardTable.update(where = { GiftCardTable.code eq normalized }) { row ->
                row[balanceCents] = balanceCents + amountCents
                row[issuedCents] = issuedCents + amountCents
            }

            if (topped == 0) {
                GiftCardTable.insert { row ->
                    row[GiftCardTable.id] = Uuid.generateV7()
                    row[GiftCardTable.code] = normalized
                    row[balanceCents] = amountCents
                    row[issuedCents] = amountCents
                    row[createdAt] = Clock.System.now().toEpochMilliseconds()
                    row[invoice] = invoiceIdentifier
                    row[customer] = customerIdentifier
                }
            }

            GiftCardTable.selectAll().where { GiftCardTable.code eq normalized }.single().toGiftCard()
        }

    /**
     * Takes [amountCents] off the card, or puts it back when negative (a spend undone)
     * @return False when there's no such card or not enough on it
     */
    fun spendGiftCard(code: String, amountCents: Long): Boolean =
        dbWrite(CARD_TAG) {
            GiftCardTable.update(where = {
                val card = GiftCardTable.code eq GiftCardCode.normalize(code)
                if (amountCents > 0) card and (GiftCardTable.balanceCents greaterEq amountCents) else card
            }) { row ->
                row[balanceCents] = balanceCents - amountCents
            } > 0
        }

    /**
     * Takes back what an invoice loaded onto a card, when it's voided. Unlike a spend it comes off what was issued too
     * @return False when it's been spent since
     */
    fun unloadGiftCard(code: String, amountCents: Long): Boolean =
        dbWrite(CARD_TAG) {
            GiftCardTable.update(where = { (GiftCardTable.code eq GiftCardCode.normalize(code)) and (GiftCardTable.balanceCents greaterEq amountCents) }) { row ->
                row[balanceCents] = balanceCents - amountCents
                row[issuedCents] = issuedCents - amountCents
            } > 0
        }

    /** Newest first */
    fun giftCards(request: PagedRequest): List<GiftCard>? =
        dbQuery(CARD_LIST_TAG) {
            GiftCardTable
                .selectAll()
                .orderBy(GiftCardTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toGiftCard() }
        }

    private fun ResultRow.toGiftCard(): GiftCard =
        GiftCard(
            identifier = this[GiftCardTable.id].value,
            code = this[GiftCardTable.code],
            balanceCents = this[GiftCardTable.balanceCents],
            issuedCents = this[GiftCardTable.issuedCents],
            createdAt = this[GiftCardTable.createdAt],
            invoiceIdentifier = this[GiftCardTable.invoice],
            customerIdentifier = this[GiftCardTable.customer]
        )
}
