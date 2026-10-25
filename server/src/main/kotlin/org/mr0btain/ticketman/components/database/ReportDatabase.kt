package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.mr0btain.ticketman.components.database.tables.invoice.InvoicePaymentTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceTable
import org.mr0btain.ticketman.components.database.tables.report.CommissionRateTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketStatusHistoryTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketWorkTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.InvoiceNumber
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.invoice.TaxLine
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.report.DaySales
import org.mr0btain.ticketman.data.report.PeriodReport
import org.mr0btain.ticketman.data.report.RefundLine
import org.mr0btain.ticketman.data.report.RefundSummary
import org.mr0btain.ticketman.data.report.ReportRules
import org.mr0btain.ticketman.data.report.SalesSummary
import org.mr0btain.ticketman.data.report.TechSummary
import org.mr0btain.ticketman.data.report.Turnaround
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The reports: read only sums over invoices, payments, the work log and status history, and the commission rates
 * they use
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class ReportDatabase(private val invoices: InvoiceDatabase) {
    private val TAG = "ReportDatabase"
    private val PERIOD_TAG = "$TAG:Period"
    private val COMMISSIONS_TAG = "$TAG:Commissions"

    /**
     * How the shop did from [from] to [to], both included, with days in [zone]. Null when a read failed
     */
    fun period(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PeriodReport? {
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val paid = invoices.getPaidBetween(start, end) ?: return null
        val costs = invoices.costs(paid.map { it.identifier }) ?: return null

        return dbQuery(PERIOD_TAG) {
            // ---------------------------------- Sales ----------------------------------
            val lines = paid.flatMap { it.charges }
            val parts = lines.filter { it.type == ChargeType.PART }
            val sales = SalesSummary(
                invoices = paid.size,
                totalCents = paid.sumOf { it.totalCents },
                taxCents = paid.sumOf { it.taxCents },
                discountCents = paid.sumOf { it.discountCents },
                laborCents = lines.filter { it.type == ChargeType.LABOR }.sumOf { it.totalCents },
                partsCents = parts.sumOf { it.totalCents },
                otherCents = lines.filter { it.type == ChargeType.OTHER }.sumOf { it.totalCents },
                giftCardCents = lines.filter { it.type == ChargeType.GIFT_CARD }.sumOf { it.totalCents },
                partsCostCents = parts.sumOf { line -> costs[line.identifier]?.let { (it * line.quantity).roundToLong() } ?: 0 },
                partsWithoutCost = parts.count { (costs[it.identifier] ?: 0L) == 0L }
            )

            val days = paid
                .groupBy { Instant.ofEpochMilli(it.paidAt ?: 0).atZone(zone).toLocalDate() }
                .toSortedMap()
                .map { (day, list) -> DaySales(day.toString(), list.size, list.sumOf { it.totalCents }) }

            // Each rate's tax as each invoice worked it out, added up by name and rate
            val taxes = paid
                .flatMap { it.totals.taxLines }
                .groupBy { it.name to it.basisPoints }
                .map { (key, list) -> TaxLine(key.first, key.second, list.sumOf { it.taxableCents }, list.sumOf { it.taxCents }) }
                .filter { it.taxCents != 0L || it.taxableCents != 0L }

            // ---------------------------------- Money taken ----------------------------------
            val payments = InvoicePaymentTable
                .selectAll()
                .where { (InvoicePaymentTable.receivedAt greaterEq start) and (InvoicePaymentTable.receivedAt less end) }
                .toList()

            val takenByMethod = payments
                .groupBy({ PaymentMethod.valueOf(it[InvoicePaymentTable.method]) }, { it[InvoicePaymentTable.amountCents] })
                .mapValues { (_, amounts) -> amounts.sum() }

            val refundRows = payments.filter { it[InvoicePaymentTable.amountCents] < 0 }
            val labels = refundRows.map { it[InvoicePaymentTable.invoice].value }.distinct().let { ids ->
                if (ids.isEmpty()) emptyMap() else InvoiceTable
                    .select(InvoiceTable.id, InvoiceTable.numberYear, InvoiceTable.numberSequence)
                    .where { InvoiceTable.id inList ids }
                    .associate { it[InvoiceTable.id].value to InvoiceNumber(it[InvoiceTable.numberYear], it[InvoiceTable.numberSequence]).toString() }
            }
            val refunds = RefundSummary(
                count = refundRows.size,
                cents = -refundRows.sumOf { it[InvoicePaymentTable.amountCents] },
                refunds = refundRows
                    .sortedByDescending { it[InvoicePaymentTable.receivedAt] }
                    .take(ReportRules.REFUND_LIST_LIMIT)
                    .map { row ->
                        RefundLine(
                            invoiceLabel = labels[row[InvoicePaymentTable.invoice].value].orEmpty(),
                            amountCents = -row[InvoicePaymentTable.amountCents],
                            reason = row[InvoicePaymentTable.note],
                            method = PaymentMethod.valueOf(row[InvoicePaymentTable.method]),
                            receivedAt = row[InvoicePaymentTable.receivedAt],
                            receivedBy = row[InvoicePaymentTable.receivedBy]
                        )
                    }
            )

            // ---------------------------------- Techs ----------------------------------
            // A work entry's invoice line has the entry's own id, so the line says what was billed for it
            val billed = paid.flatMap { invoice -> invoice.charges.map { it to invoice.ticketIdentifier } }.associateBy { it.first.identifier }
            val work = if (billed.isEmpty()) emptyList() else billed.keys.chunked(CHUNK).flatMap { chunk ->
                TicketWorkTable
                    .select(TicketWorkTable.id, TicketWorkTable.user, TicketWorkTable.userName)
                    .where { TicketWorkTable.id inList chunk }
                    .map { row -> Triple(row[TicketWorkTable.id].value, row[TicketWorkTable.user]?.let { runCatching { Uuid.parse(it) }.getOrNull() }, row[TicketWorkTable.userName]) }
            }

            val rates = commissionMap()
            val names = UserTable.select(UserTable.id, UserTable.name, UserTable.username)
                .associate { it[UserTable.id].value to it[UserTable.name].ifBlank { it[UserTable.username] } }

            val techs = work
                .groupBy { (_, user, name) -> user ?: name }
                .map { (_, entries) ->
                    val user = entries.first().second
                    val charged = entries.mapNotNull { (identifier, _, _) -> billed[identifier] }
                    val labor = charged.filter { it.first.type == ChargeType.LABOR }.map { it.first }
                    TechSummary(
                        userIdentifier = user,
                        name = user?.let { names[it] } ?: entries.first().third,
                        tickets = charged.mapNotNull { it.second }.distinct().size,
                        laborHours = labor.sumOf { it.quantity },
                        laborCents = labor.sumOf { it.totalCents },
                        partsCents = charged.filter { it.first.type == ChargeType.PART }.sumOf { it.first.totalCents },
                        commissionBasisPoints = user?.let { rates[it] } ?: 0
                    )
                }
                .sortedByDescending { it.laborCents }

            // ---------------------------------- Turnaround ----------------------------------
            val pickedUp = TicketTable
                .select(TicketTable.id, TicketTable.pickedUpAt)
                .where { (TicketTable.pickedUpAt greaterEq start) and (TicketTable.pickedUpAt less end) }
                .associate { it[TicketTable.id].value to it[TicketTable.pickedUpAt]!! }

            val firstReady = if (pickedUp.isEmpty()) emptyMap() else pickedUp.keys.chunked(CHUNK).flatMap { chunk ->
                TicketStatusHistoryTable
                    .select(TicketStatusHistoryTable.ticket, TicketStatusHistoryTable.time)
                    .where { (TicketStatusHistoryTable.ticket inList chunk) and (TicketStatusHistoryTable.status eq TicketStatusType.DONE.name) }
                    .map { it[TicketStatusHistoryTable.ticket].value to it[TicketStatusHistoryTable.time] }
            }.groupBy({ it.first }, { it.second }).mapValues { (_, times) -> times.min() }

            val toPickup = pickedUp.map { (identifier, at) -> hours(at - identifier.timestampMillis()) }
            val toReady = firstReady.map { (identifier, at) -> hours(at - identifier.timestampMillis()) }

            PeriodReport(
                from = from.toString(),
                to = to.toString(),
                sales = sales,
                days = days,
                taxes = taxes,
                takenByMethod = takenByMethod,
                refunds = refunds,
                techs = techs,
                turnaround = Turnaround(
                    pickedUp = pickedUp.size,
                    readyCount = toReady.size,
                    averageHoursToReady = toReady.averageOrNull(),
                    medianHoursToReady = toReady.medianOrNull(),
                    averageHoursToPickup = toPickup.averageOrNull(),
                    medianHoursToPickup = toPickup.medianOrNull()
                )
            )
        }
    }

    /** Every user's commission rate that's more than nothing, for users that still exist. Null when the read failed */
    fun commissions(): List<CommissionRate>? =
        dbQuery(COMMISSIONS_TAG) {
            val users = UserTable.select(UserTable.id).map { it[UserTable.id].value }.toSet()
            commissionMap().filterKeys { it in users }.map { (user, points) -> CommissionRate(user, points) }
        }

    /** Replaces every rate with [rates]; a user left out has none */
    fun setCommissions(rates: List<CommissionRate>): Boolean =
        dbWrite(COMMISSIONS_TAG) {
            CommissionRateTable.deleteAll()
            CommissionRateTable.batchInsert(rates.filter { it.basisPoints > 0 }) { rate ->
                this[CommissionRateTable.user] = rate.userIdentifier
                this[CommissionRateTable.basisPoints] = rate.basisPoints
            }
            true
        }

    // Inside a transaction
    private fun commissionMap(): Map<Uuid, Int> =
        CommissionRateTable.selectAll().associate { it[CommissionRateTable.user] to it[CommissionRateTable.basisPoints] }

    private fun hours(millis: Long): Double = millis.coerceAtLeast(0) / HOUR_MILLIS

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private companion object {
        const val CHUNK = 500
        const val HOUR_MILLIS = 3_600_000.0
    }
}
