package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.EstimateDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.notify.NotificationService
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.estimate.DecisionMethod
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.estimate.EstimateDecision
import org.mr0btain.ticketman.data.estimate.EstimateStatus
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.selectable
import org.mr0btain.ticketman.server.request.EstimateDecisionRequest
import org.mr0btain.ticketman.server.request.EstimateRequest
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Estimates: staff send one, the customer approves or declines it on the status page or tells staff, who write it
 * down. Approving puts its lines on the invoice. Each step can move the ticket to a status the shop picked
 * ([org.mr0btain.ticketman.data.shop.ShopProfile.estimateSentStatus] and the others); those moves don't send the
 * status changed message, since the estimate's own message says it
 * @param now Epoch millis, swapped in tests
 */
@OptIn(ExperimentalUuidApi::class)
class EstimateService(
    private val estimates: EstimateDatabase,
    private val tickets: TicketDatabase,
    private val invoices: InvoiceService,
    private val shop: ShopDatabase,
    private val notifications: NotificationService,
    private val auditLog: AuditLogDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    fun forTicket(ticketIdentifier: Uuid): AccountResult<List<Estimate>> {
        tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        return estimates.forTicket(ticketIdentifier)?.let { AccountResult.Done(it) } ?: AccountResult.Invalid("Couldn't read the estimates")
    }

    /** The one waiting for the customer, for the status page */
    fun pending(ticketIdentifier: Uuid): Estimate? = estimates.pending(ticketIdentifier)

    /** The newest one the customer answered, for the status page */
    fun lastAnswered(ticketIdentifier: Uuid): Estimate? =
        estimates.forTicket(ticketIdentifier)?.firstOrNull { it.decision != null }

    /** Sends a new estimate, withdrawing one still waiting */
    fun create(ticketIdentifier: Uuid, request: EstimateRequest, staffName: String, actor: Uuid?): AccountResult<Estimate> = synchronized(LOCK) {
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was picked up")
        invoices.linesProblem(request.lines)?.let { return AccountResult.Invalid(it) }
        if (request.note.length > MAX_NOTE) return AccountResult.Invalid("The note can be up to $MAX_NOTE characters")

        val profile = shop.get()
        request.lines.firstOrNull { line -> line.taxable && line.taxClass != null && profile.taxRates.none { it.identifier == line.taxClass } }?.let { line ->
            return AccountResult.Invalid("\"${line.description}\" has a tax class the shop doesn't have any more, pick another")
        }
        val estimate = Estimate(
            ticketIdentifier = ticketIdentifier,
            lines = request.lines.map { it.copy(description = it.description.trim()) },
            taxRateBasisPoints = profile.taxBasisPoints,
            taxRates = profile.taxRates,
            note = request.note.trim(),
            createdAt = now(),
            createdBy = staffName
        )

        estimates.pending(ticketIdentifier)?.let { estimates.close(it.identifier, EstimateStatus.WITHDRAWN, null) }
        if (!estimates.add(estimate)) return AccountResult.Invalid("Couldn't save the estimate")

        auditLog.log(actor, AuditAction.CREATE, "ticket", ticketIdentifier.toString(), "Estimate of ${money(estimate.totalCents)} sent for ${ticket.label}")
        val moved = moveTicket(ticket, profile.estimateSentStatus, staffName)
        if (request.notify) notifications.estimateReady(moved)

        return AccountResult.Done(estimate)
    }

    /** What the customer told staff */
    fun decide(identifier: Uuid, request: EstimateDecisionRequest, staffName: String, actor: Uuid?): AccountResult<Estimate> {
        if (request.method == DecisionMethod.STATUS_PAGE) return AccountResult.Invalid("Only the customer answers on the status page")
        if (request.note.length > MAX_NOTE) return AccountResult.Invalid("The note can be up to $MAX_NOTE characters")

        return answer(identifier, request.approved, request.method, request.note.trim(), recordedBy = staffName, signedName = "", actor = actor)
    }

    /**
     * The customer answering on their status page, signing with their typed name
     * @return Null when there's nothing waiting to answer
     */
    fun decideOnPage(ticket: Ticket, approved: Boolean, signedName: String): AccountResult<Estimate> {
        val estimate = estimates.pending(ticket.identifier) ?: return AccountResult.NotFound
        return answer(estimate.identifier, approved, DecisionMethod.STATUS_PAGE, "", recordedBy = "", signedName = signedName.trim(), actor = null)
    }

    private fun answer(
        identifier: Uuid,
        approved: Boolean,
        method: DecisionMethod,
        note: String,
        recordedBy: String,
        signedName: String,
        actor: Uuid?
    ): AccountResult<Estimate> = synchronized(LOCK) {
        val estimate = estimates.get(identifier) ?: return AccountResult.NotFound
        if (estimate.status != EstimateStatus.PENDING) return AccountResult.Conflict("That estimate was already ${estimate.status.label.lowercase()}")
        val ticket = tickets.getById(estimate.ticketIdentifier) ?: return AccountResult.NotFound

        val decision = EstimateDecision(
            approved = approved,
            method = method,
            amountCents = estimate.totalCents,
            decidedAt = now(),
            recordedBy = recordedBy,
            signedName = signedName,
            note = note
        )
        val status = if (approved) EstimateStatus.APPROVED else EstimateStatus.DECLINED
        val who = recordedBy.ifBlank { signedName.ifBlank { "the customer" } }
        val what = "Estimate of ${money(estimate.totalCents)} on ${ticket.label} ${status.label.lowercase()} (${method.label.lowercase()}, $who)"

        if (approved) {
            when (val added = invoices.addApprovedLines(ticket.identifier, estimate.lines, "Approved estimate", actor) { estimates.close(identifier, status, decision) }) {
                is AccountResult.Done -> tickets.setEstimate(ticket.identifier, estimate.totalCents)
                is AccountResult.Invalid -> return added
                is AccountResult.Conflict -> return added
                AccountResult.NotFound -> return AccountResult.NotFound
            }
        } else if (!estimates.close(identifier, status, decision)) {
            return AccountResult.Conflict("That estimate was already answered")
        }

        auditLog.log(actor, AuditAction.UPDATE, "ticket", ticket.identifier.toString(), what, actorName = if (actor == null) who else null)

        val profile = shop.get()
        moveTicket(ticket, if (approved) profile.estimateApprovedStatus else profile.estimateDeclinedStatus, who)

        return AccountResult.Done(estimate.copy(status = status, decision = decision))
    }

    /** Calls off a waiting estimate */
    fun withdraw(identifier: Uuid, actor: Uuid?): AccountResult<Estimate> = synchronized(LOCK) {
        val estimate = estimates.get(identifier) ?: return AccountResult.NotFound
        if (!estimates.close(identifier, EstimateStatus.WITHDRAWN, null)) return AccountResult.Conflict("Only a waiting estimate can be withdrawn")

        auditLog.log(actor, AuditAction.UPDATE, "ticket", estimate.ticketIdentifier.toString(), "Estimate of ${money(estimate.totalCents)} withdrawn")
        return AccountResult.Done(estimate.copy(status = EstimateStatus.WITHDRAWN))
    }

    /**
     * Moves the ticket to [choice] when the shop set one and it's still a status staff could pick. Recorded in its
     * history like any change
     * @return The ticket as it is now
     */
    private fun moveTicket(ticket: Ticket, choice: StatusChoice?, changedBy: String): Ticket {
        // Read again, so a change made since (like the approved price) isn't saved over
        val current = tickets.getById(ticket.identifier) ?: return ticket
        if (choice == null || choice == current.status.choice || current.status.status == TicketStatusType.PICKED_UP) return current
        if (choice !in shop.get().statuses.selectable()) return current

        val moved = current.copy(status = choice.toStatus(now()))
        if (!tickets.update(moved)) return current

        tickets.recordStatusChange(current.identifier, moved.status, changedBy)
        return moved
    }

    private fun money(cents: Long): String = Money.format(cents, shop.get().currencySymbol)

    companion object {
        const val MAX_NOTE = 2000

        // Sending and answering one at a time, so two answers can't both count
        private val LOCK = Any()
    }
}
