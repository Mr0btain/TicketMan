package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.payment.CardChargeTable
import org.mr0btain.ticketman.components.database.tables.payment.PaymentSettingsTable
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeKind
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.data.shared.PagedRequest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * How the shop takes cards, and every charge sent to the processor. A charge's status only moves on from
 * `PENDING` once, in a single update that checks it's still waiting, so a webhook and a check at the same time
 * can't both put its payment on the invoice
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class PaymentDatabase {
    private val TAG = "PaymentDatabase"
    private val SETTINGS_TAG = "$TAG:Settings"
    private val ADD_TAG = "$TAG:Add"
    private val GET_TAG = "$TAG:Get"
    private val UPDATE_TAG = "$TAG:Update"
    private val LIST_TAG = "$TAG:List"

    private val ROW_ID = 1

    /** The saved settings with their keys, or the defaults */
    fun settings(): PaymentSettings =
        dbQuery(SETTINGS_TAG) {
            PaymentSettingsTable.selectAll().where { PaymentSettingsTable.id eq ROW_ID }.firstOrNull()?.get(PaymentSettingsTable.settings)
        } ?: PaymentSettings()

    fun saveSettings(settings: PaymentSettings): Boolean =
        dbWrite(SETTINGS_TAG) {
            val updated = PaymentSettingsTable.update(where = { PaymentSettingsTable.id eq ROW_ID }) { row ->
                row[PaymentSettingsTable.settings] = settings
            }

            if (updated == 0) {
                PaymentSettingsTable.insert { row ->
                    row[PaymentSettingsTable.id] = ROW_ID
                    row[PaymentSettingsTable.settings] = settings
                }
            }

            true
        }

    fun add(charge: CardCharge): Boolean =
        dbWrite(ADD_TAG) {
            CardChargeTable.insert { row ->
                row[CardChargeTable.id] = charge.identifier
                row[kind] = charge.kind.name
                row[provider] = charge.provider.name
                row[status] = charge.status.name
                row[amountCents] = charge.amountCents
                row[currency] = charge.currency
                row[invoice] = charge.invoiceIdentifier
                row[invoiceLabel] = charge.invoiceLabel
                row[reader] = charge.readerIdentifier
                row[readerName] = charge.readerName
                row[url] = charge.url
                row[providerReference] = charge.providerReference
                row[paymentReference] = charge.paymentReference
                row[payment] = charge.paymentIdentifier
                row[error] = charge.error
                row[createdAt] = charge.createdAt
                row[finishedAt] = charge.finishedAt
                row[createdBy] = charge.createdBy
                row[tipCents] = charge.tipCents
                row[surchargeCents] = charge.surchargeCents
            }

            true
        }

    fun get(identifier: Uuid): CardCharge? =
        dbQuery(GET_TAG) {
            CardChargeTable.selectAll().where { CardChargeTable.id eq identifier }.firstOrNull()?.toCharge()
        }

    fun markChecked(identifier: Uuid, at: Long): Boolean =
        dbWrite(UPDATE_TAG) {
            CardChargeTable.update(where = { CardChargeTable.id eq identifier }) { row -> row[checkedAt] = at } > 0
        }

    /** What the processor called what was started, once it answered */
    fun setStarted(identifier: Uuid, reference: String, url: String): Boolean =
        dbWrite(UPDATE_TAG) {
            CardChargeTable.update(where = { CardChargeTable.id eq identifier }) { row ->
                row[providerReference] = reference
                row[CardChargeTable.url] = url
            } > 0
        }

    /**
     * Moves a waiting charge to [status], only while it's still waiting
     * @return Whether this call moved it
     */
    fun finish(identifier: Uuid, status: CardChargeStatus, at: Long, error: String = "", paymentReference: String = "", paymentIdentifier: Uuid? = null): Boolean =
        dbWrite(UPDATE_TAG) {
            CardChargeTable.update(where = { (CardChargeTable.id eq identifier) and (CardChargeTable.status eq CardChargeStatus.PENDING.name) }) { row ->
                row[CardChargeTable.status] = status.name
                row[finishedAt] = at
                row[CardChargeTable.error] = error
                row[CardChargeTable.paymentReference] = paymentReference
                row[payment] = paymentIdentifier
            } > 0
        }

    /** Leaves a paid charge without an invoice, when the one it was for is gone */
    fun detach(identifier: Uuid): Boolean =
        dbWrite(UPDATE_TAG) {
            CardChargeTable.update(where = { CardChargeTable.id eq identifier }) { row -> row[invoice] = null } > 0
        }

    /**
     * Puts a paid charge with no invoice onto one, only while nothing has it yet
     * @return Whether this call claimed it
     */
    fun claim(identifier: Uuid, invoiceIdentifier: Uuid, label: String, paymentIdentifier: Uuid): Boolean =
        dbWrite(UPDATE_TAG) {
            CardChargeTable.update(where = {
                (CardChargeTable.id eq identifier) and (CardChargeTable.status eq CardChargeStatus.SUCCEEDED.name) and CardChargeTable.payment.isNull()
            }) { row ->
                row[invoice] = invoiceIdentifier
                row[invoiceLabel] = label
                row[payment] = paymentIdentifier
            } > 0
        }

    /** A paid charge given back by an admin, only while nothing has it */
    fun markRefunded(identifier: Uuid, at: Long): Boolean =
        dbWrite(UPDATE_TAG) {
            CardChargeTable.update(where = {
                (CardChargeTable.id eq identifier) and (CardChargeTable.status eq CardChargeStatus.SUCCEEDED.name) and CardChargeTable.payment.isNull()
            }) { row ->
                row[status] = CardChargeStatus.REFUNDED.name
                row[finishedAt] = at
            } > 0
        }

    /** A paid charge whose payment was taken off its invoice and the money given back */
    fun markReturned(identifier: Uuid, at: Long): Boolean =
        dbWrite(UPDATE_TAG) {
            CardChargeTable.update(where = { (CardChargeTable.id eq identifier) and (CardChargeTable.status eq CardChargeStatus.SUCCEEDED.name) }) { row ->
                row[status] = CardChargeStatus.REFUNDED.name
                row[finishedAt] = at
            } > 0
        }

    /** Waiting charges with when each was last checked, oldest first */
    fun pending(limit: Int): List<Pair<CardCharge, Long>> =
        dbQuery(LIST_TAG) {
            CardChargeTable.selectAll()
                .where { CardChargeTable.status eq CardChargeStatus.PENDING.name }
                .orderBy(CardChargeTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .map { it.toCharge() to it[CardChargeTable.checkedAt] }
        }.orEmpty()

    /** The waiting charges the processor knows as [reference], for a webhook */
    fun pendingByReference(provider: PaymentProvider, reference: String): List<CardCharge> =
        dbQuery(LIST_TAG) {
            CardChargeTable.selectAll()
                .where {
                    (CardChargeTable.providerReference eq reference) and (CardChargeTable.provider eq provider.name) and
                        (CardChargeTable.status eq CardChargeStatus.PENDING.name)
                }
                .map { it.toCharge() }
        }.orEmpty()

    /** An invoice's charges, newest first */
    fun forInvoice(invoiceIdentifier: Uuid): List<CardCharge>? =
        dbQuery(LIST_TAG) {
            CardChargeTable.selectAll()
                .where { CardChargeTable.invoice eq invoiceIdentifier }
                .orderBy(CardChargeTable.createdAt to SortOrder.DESC, CardChargeTable.id to SortOrder.DESC)
                .map { it.toCharge() }
        }

    /** A page of every charge, newest first */
    fun page(request: PagedRequest): List<CardCharge>? =
        dbQuery(LIST_TAG) {
            CardChargeTable.selectAll()
                .orderBy(CardChargeTable.createdAt to SortOrder.DESC, CardChargeTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toCharge() }
        }

    private fun ResultRow.toCharge(): CardCharge =
        CardCharge(
            identifier = this[CardChargeTable.id].value,
            kind = CardChargeKind.valueOf(this[CardChargeTable.kind]),
            provider = PaymentProvider.valueOf(this[CardChargeTable.provider]),
            status = CardChargeStatus.valueOf(this[CardChargeTable.status]),
            amountCents = this[CardChargeTable.amountCents],
            currency = this[CardChargeTable.currency],
            invoiceIdentifier = this[CardChargeTable.invoice],
            invoiceLabel = this[CardChargeTable.invoiceLabel],
            readerIdentifier = this[CardChargeTable.reader],
            readerName = this[CardChargeTable.readerName],
            url = this[CardChargeTable.url],
            providerReference = this[CardChargeTable.providerReference],
            paymentReference = this[CardChargeTable.paymentReference],
            paymentIdentifier = this[CardChargeTable.payment],
            error = this[CardChargeTable.error],
            createdAt = this[CardChargeTable.createdAt],
            finishedAt = this[CardChargeTable.finishedAt],
            createdBy = this[CardChargeTable.createdBy],
            tipCents = this[CardChargeTable.tipCents],
            surchargeCents = this[CardChargeTable.surchargeCents]
        )
}
