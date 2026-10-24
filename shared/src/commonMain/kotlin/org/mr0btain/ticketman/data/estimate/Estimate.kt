package org.mr0btain.ticketman.data.estimate

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.InvoiceMath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class EstimateStatus(val label: String) {
    /** Sent, waiting for the customer */
    PENDING("Waiting for approval"),

    /** Its lines are on the invoice */
    APPROVED("Approved"),
    DECLINED("Declined"),

    /** Replaced by a newer one, or called off by staff */
    WITHDRAWN("Withdrawn")
}

/** How the customer said yes or no */
@Serializable
enum class DecisionMethod(val label: String) {
    /** On the status page, signing with their typed name */
    STATUS_PAGE("Status page"),
    PHONE("Phone"),
    IN_PERSON("In person"),
    EMAIL("Email or text")
}

/**
 * The customer's answer to an estimate
 * @param amountCents The estimate's total at the time, kept so what was agreed to never changes
 * @param recordedBy Staff who wrote it down, blank when the customer answered on the status page
 * @param signedName The name the customer typed on the status page, blank otherwise
 */
@Serializable
data class EstimateDecision(
    val approved: Boolean,
    val method: DecisionMethod,
    val amountCents: Long,
    val decidedAt: Long,
    val recordedBy: String = "",
    val signedName: String = "",
    val note: String = ""
)

/**
 * Work the shop proposes on a ticket, for the customer to approve before it's done. Approving puts its lines on the
 * ticket's invoice. A ticket can have several over time, at most one [EstimateStatus.PENDING]
 * @param taxRateBasisPoints The shop's tax rate when it was made, like an invoice's
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Estimate(
    val identifier: Uuid = Uuid.generateV7(),
    val ticketIdentifier: Uuid,
    val lines: List<Charge> = emptyList(),
    val taxRateBasisPoints: Int = 0,
    val note: String = "",
    val status: EstimateStatus = EstimateStatus.PENDING,
    val createdAt: Long = 0,
    val createdBy: String = "",
    val decision: EstimateDecision? = null
) {
    val totals: InvoiceMath.Totals
        get() = InvoiceMath.totals(lines, 0, taxRateBasisPoints)

    val totalCents: Long
        get() = totals.totalCents
}
