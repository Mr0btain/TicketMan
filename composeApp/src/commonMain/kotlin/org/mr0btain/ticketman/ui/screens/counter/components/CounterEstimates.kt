package org.mr0btain.ticketman.ui.screens.counter.components

import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.estimate.DecisionMethod
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.notify.NotificationEvent
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.request.EstimateDecisionRequest
import org.mr0btain.ticketman.server.request.EstimateRequest
import org.mr0btain.ticketman.server.request.TicketMessageRequest
import kotlin.uuid.Uuid

/**
 * Talking to the open ticket's customer from its card: estimates and messages
 * @param reload Reloads the open ticket after a change
 */
class CounterEstimates(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport,
    private val reload: () -> Unit
) {
    /** Sends an estimate, withdrawing one still waiting */
    fun send(ticketIdentifier: Uuid, lines: List<Charge>, note: String, notify: Boolean, onDone: () -> Unit = {}) {
        if (!support.canEdit()) return

        support.scope.launch {
            when (val result = remoteHandler.estimateRepo.send(ticketIdentifier, EstimateRequest(lines, note, notify))) {
                is TmResult.Success -> {
                    support.notify("Estimate sent")
                    onDone()
                    reload()
                    support.ticketsChanged()
                }

                is TmResult.Error -> support.notify("Estimate not sent: ${result.error.message}")
            }
        }
    }

    /** Writes down what the customer said, by phone or at the counter */
    fun decide(estimate: Estimate, approved: Boolean, method: DecisionMethod, note: String) {
        if (!support.canEdit()) return

        support.scope.launch {
            when (val result = remoteHandler.estimateRepo.decide(estimate.identifier, EstimateDecisionRequest(approved, method, note))) {
                is TmResult.Success -> {
                    support.notify(if (approved) "Approved, its lines are on the invoice" else "Declined")
                    reload()
                    support.ticketsChanged()
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    fun withdraw(estimate: Estimate) {
        if (!support.canEdit()) return

        support.scope.launch {
            when (val result = remoteHandler.estimateRepo.withdraw(estimate.identifier)) {
                is TmResult.Success -> {
                    support.notify("Estimate withdrawn")
                    reload()
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /** Sends the customer a message now, by every channel they have a contact for */
    fun message(ticketIdentifier: Uuid, event: NotificationEvent = NotificationEvent.STATUS_LINK) {
        if (!support.canEdit()) return

        support.scope.launch {
            when (val result = remoteHandler.notifyRepo.sendForTicket(ticketIdentifier, TicketMessageRequest(event))) {
                is TmResult.Success -> {
                    support.notify("Sending by ${result.data.joinToString(" and ") { it.channel.label.lowercase() }}")
                    reload()
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }
}
