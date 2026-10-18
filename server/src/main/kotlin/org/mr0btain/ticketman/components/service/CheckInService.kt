package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabaseImpl
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.money.Money
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
    private val tickets: TicketDatabaseImpl,
    private val customers: CustomerDatabaseImpl,
    private val users: UserDatabaseImpl,
    private val invoices: InvoiceDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase
) {
    /**
     * Makes the ticket, links the customer and tech, takes any deposit and saves the signature. Everything is
     * checked first, then saved together
     */
    fun checkIn(request: CheckInRequest, takenBy: String, actor: Uuid?): AccountResult<CheckInResponse> {
        val ticket = request.ticket

        // Check in time is read from the identifier, so it has to be a time based (version 7) one
        if (ticket.identifier.toByteArray()[6].toInt() ushr 4 != 7) return AccountResult.Invalid("Ticket identifier has to be a version 7 UUID")
        if (tickets.getById(ticket.identifier) != null) return AccountResult.Conflict("That ticket already exists")
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

        request.deposit?.let { if (it.amountCents <= 0) return AccountResult.Invalid("Deposit has to be more than zero") }

        val shopProfile = shop.get()
        request.signature?.problem()?.let { return AccountResult.Invalid(it) }
        if (shopProfile.repairTerms.isNotBlank() && request.signature == null) return AccountResult.Invalid("The customer has to sign the repair terms")

        val now = Clock.System.now().toEpochMilliseconds()

        transaction {
            // Pickup fields are the server's to set
            tickets.add(ticket.copy(pickedUpAt = null, number = null))
            tickets.recordStatusChange(ticket.identifier, ticket.status.status, takenBy)
            tickets.setCustomer(ticket.identifier, request.customerIdentifier)
            request.technicianIdentifier?.let { tickets.setUser(ticket.identifier, it) }

            request.deposit?.let { deposit ->
                val taxRate = Money.percentToBasisPoints(shopProfile.taxRate) ?: 0
                invoices.getOrCreate(ticket.identifier, request.customerIdentifier, taxRate)?.let { invoice ->
                    invoices.addPayment(
                        invoice.identifier,
                        Payment(method = deposit.method, amountCents = deposit.amountCents, receivedAt = now, receivedBy = takenBy, note = deposit.note.ifBlank { "Deposit" })
                    )
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
        }

        val saved = tickets.getById(ticket.identifier) ?: return AccountResult.Invalid("Couldn't save the ticket")
        val invoice = invoices.getByTicket(ticket.identifier)

        auditLog.log(
            actor, AuditAction.CREATE, "ticket", saved.identifier.toString(),
            "Checked in ${saved.label}" + (request.deposit?.let { " with a ${Money.format(it.amountCents)} deposit" } ?: "")
        )

        return AccountResult.Done(CheckInResponse(saved, invoice))
    }
}
