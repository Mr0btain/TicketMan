package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.estimate.EstimateTable
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.estimate.EstimateDecision
import org.mr0btain.ticketman.data.estimate.EstimateStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Estimates on tickets. Changes are made by `EstimateService`, inside its transactions
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class EstimateDatabase {
    private val TAG = "EstimateDatabase"
    private val GET_TAG = "$TAG:Get"
    private val ADD_TAG = "$TAG:Add"
    private val STATUS_TAG = "$TAG:Status"

    fun get(identifier: Uuid): Estimate? =
        dbQuery(GET_TAG) {
            EstimateTable.selectAll().where { EstimateTable.id eq identifier }.firstOrNull()?.toEstimate()
        }

    /** The ticket's estimates, newest first. Null when the read failed */
    fun forTicket(ticketIdentifier: Uuid): List<Estimate>? =
        dbQuery(GET_TAG) {
            EstimateTable.selectAll()
                .where { EstimateTable.ticket eq ticketIdentifier }
                .orderBy(EstimateTable.createdAt to SortOrder.DESC, EstimateTable.id to SortOrder.DESC)
                .map { it.toEstimate() }
        }

    /** The one waiting for the customer, if there is one */
    fun pending(ticketIdentifier: Uuid): Estimate? =
        forTicket(ticketIdentifier)?.firstOrNull { it.status == EstimateStatus.PENDING }

    fun add(estimate: Estimate): Boolean =
        dbWrite(ADD_TAG) {
            EstimateTable.insert { row ->
                row[EstimateTable.id] = estimate.identifier
                row[ticket] = estimate.ticketIdentifier
                row[lines] = estimate.lines
                row[taxRateBasisPoints] = estimate.taxRateBasisPoints
                row[note] = estimate.note
                row[status] = estimate.status.name
                row[createdAt] = estimate.createdAt
                row[createdBy] = estimate.createdBy
                row[decision] = estimate.decision
            }

            true
        }

    /**
     * Moves a waiting estimate on. Only from [EstimateStatus.PENDING], so two answers can't both count
     * @return Whether it was still waiting
     */
    fun close(identifier: Uuid, status: EstimateStatus, decision: EstimateDecision?): Boolean =
        dbWrite(STATUS_TAG) {
            EstimateTable.update(where = { (EstimateTable.id eq identifier) and (EstimateTable.status eq EstimateStatus.PENDING.name) }) { row ->
                row[EstimateTable.status] = status.name
                row[EstimateTable.decision] = decision
            } > 0
        }

    private fun ResultRow.toEstimate(): Estimate =
        Estimate(
            identifier = this[EstimateTable.id].value,
            ticketIdentifier = this[EstimateTable.ticket].value,
            lines = this[EstimateTable.lines],
            taxRateBasisPoints = this[EstimateTable.taxRateBasisPoints],
            note = this[EstimateTable.note],
            status = EstimateStatus.valueOf(this[EstimateTable.status]),
            createdAt = this[EstimateTable.createdAt],
            createdBy = this[EstimateTable.createdBy],
            decision = this[EstimateTable.decision]
        )
}
