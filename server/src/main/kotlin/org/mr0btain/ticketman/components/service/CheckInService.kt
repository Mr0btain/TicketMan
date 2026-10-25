package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.StoredValueDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.CustomStatusRules
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Checking devices in at the counter
 */
class CheckInService(
    private val tickets: TicketDatabase,
    private val customers: CustomerDatabase,
    private val users: UserDatabase,
    private val invoices: InvoiceDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase,
    values: StoredValueDatabase = StoredValueDatabase()
) {
    private val storedValue = StoredValuePayments(values) { Money.format(it, shop.get().currencySymbol) }

    /**
     * Makes the ticket, links the customer and tech, takes any deposit and saves the signature. Everything is
     * checked first, then saved together
     */
    fun checkIn(received: CheckInRequest, takenBy: String, actor: Uuid?): AccountResult<CheckInResponse> {
        val shopProfile = shop.get()

        // A custom status is saved with the built-in one it acts as, which is then checked like any other
        val status = when (val resolved = CustomStatusRules.resolve(received.ticket.status, shopProfile.statuses)) {
            is CustomStatusRules.Resolved.Ok -> resolved.status
            is CustomStatusRules.Resolved.Problem -> return AccountResult.Invalid(resolved.message)
        }
        val request = received.copy(ticket = received.ticket.copy(status = status))
        val ticket = request.ticket

        // Check in time is read from the identifier, so it has to be a time based (version 7) one
        if (ticket.identifier.toByteArray()[6].toInt() ushr 4 != 7) return AccountResult.Invalid("Ticket identifier has to be a version 7 UUID")
        if (customers.getById(request.customerIdentifier) == null) return AccountResult.Invalid("Pick a customer")
        if (ticket.device.manufacturer.isBlank() && ticket.device.model.isBlank()) return AccountResult.Invalid("Enter the device")
        if (ticket.problem.isBlank()) return AccountResult.Invalid("Describe the problem")
        if (ticket.status.status != TicketStatusType.IN_PROGRESS && ticket.status.status != TicketStatusType.ON_HOLD) {
            return AccountResult.Invalid("New tickets start in progress or on hold")
        }
        if ((ticket.estimateCents ?: 0) < 0) return AccountResult.Invalid("Estimate can't be negative")

        request.technicianIdentifier?.let { identifier ->
            val technician = users.getById(identifier)
            if (technician == null || !technician.canAccess(AccessLevel.NORMAL)) return AccountResult.Invalid("That technician can't take tickets")
        }

        request.deposit?.let { deposit ->
            if (deposit.amountCents <= 0) return AccountResult.Invalid("Deposit has to be more than zero")
            storedValue.problem(listOf(deposit), request.customerIdentifier)?.let { return AccountResult.Conflict(it) }
        }

        request.signature?.problem()?.let { return AccountResult.Invalid(it) }
        if (shopProfile.repairTerms.isNotBlank() && request.signature == null) return AccountResult.Invalid("The customer has to sign the repair terms")

        // Both numbering locks are held until the transaction commits, so a check in at the same moment waits and
        // gets the next number instead of this one. Always ticket then invoice, never the other way round, so two can't deadlock
        val added = try {
            tickets.whileNumbering {
                invoices.whileNumbering {
                    // Under the lock, so the same ticket sent twice at once is only made once
                    if (tickets.getById(ticket.identifier) != null) return@whileNumbering false

                    saveCheckIn(request, takenBy, actor, shopProfile)
                    true
                }
            }
        } catch (ex: StoredValuePayments.ShortOfValue) {
            return AccountResult.Conflict(ex.message)
        }

        if (!added) return AccountResult.Conflict("That ticket already exists")

        val saved = tickets.getById(ticket.identifier) ?: return AccountResult.Invalid("Couldn't save the ticket")
        val invoice = invoices.getByTicket(ticket.identifier)

        return AccountResult.Done(CheckInResponse(saved, invoice))
    }

    // Everything checked in goes in together or not at all
    private fun saveCheckIn(request: CheckInRequest, takenBy: String, actor: Uuid?, shopProfile: ShopProfile) {
        val ticket = request.ticket
        val now = Clock.System.now().toEpochMilliseconds()

        transaction {
            // Pickup fields are the server's to set
            tickets.add(ticket.copy(pickedUpAt = null, number = null))
            tickets.recordStatusChange(ticket.identifier, ticket.status, takenBy)
            tickets.setCustomer(ticket.identifier, request.customerIdentifier)
            request.technicianIdentifier?.let { tickets.setUser(ticket.identifier, it) }

            request.deposit?.let { deposit ->
                invoices.getOrCreate(ticket.identifier, request.customerIdentifier, shopProfile.taxBasisPoints, shopProfile.taxRates)?.let { invoice ->
                    val payment = storedValue.normalized(
                        Payment(
                            method = deposit.method, amountCents = deposit.amountCents, receivedAt = now, receivedBy = takenBy,
                            note = deposit.note.ifBlank { "Deposit" }, reference = deposit.reference
                        )
                    )
                    invoices.addPayment(invoice.identifier, payment)
                    storedValue.take(payment, request.customerIdentifier, invoice.identifier, invoice.label, takenBy)
                }
            }

            request.signature?.let { signature ->
                tickets.setSignature(
                    ticket.identifier,
                    TicketSignature(
                        strokes = signature.strokes,
                        aspectRatio = signature.aspectRatio,
                        signedName = signature.signedName.trim(),
                        terms = shopProfile.repairTerms,
                        signedAt = now,
                        takenBy = takenBy
                    )
                )
            }

            // Inside the transaction so the entry is only there if the check in is. The number was just assigned
            val label = tickets.getById(ticket.identifier)?.label ?: ticket.label
            auditLog.log(
                actor, AuditAction.CREATE, "ticket", ticket.identifier.toString(),
                "Checked in $label" + (request.deposit?.let { " with a ${Money.format(it.amountCents, shopProfile.currencySymbol)} deposit" } ?: "")
            )
        }
    }
}
