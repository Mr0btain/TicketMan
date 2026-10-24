package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.estimate.DecisionMethod
import org.mr0btain.ticketman.data.invoice.Charge

/**
 * A new estimate for a ticket, withdrawing one still waiting
 * @param notify Tells the customer it's ready, when the shop has that message on
 */
@Serializable
data class EstimateRequest(
    val lines: List<Charge>,
    val note: String = "",
    val notify: Boolean = true
)

/** What the customer said, written down by staff */
@Serializable
data class EstimateDecisionRequest(
    val approved: Boolean,
    val method: DecisionMethod,
    val note: String = ""
)
