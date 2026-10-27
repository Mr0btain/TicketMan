package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.PaymentDatabase
import org.mr0btain.ticketman.components.database.LocationDatabase
import org.mr0btain.ticketman.components.payments.CardPayments
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AppointmentDatabase
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.DeviceDatabase
import org.mr0btain.ticketman.components.database.PasscodeDatabase
import org.mr0btain.ticketman.data.ticket.IntakeChecklists
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.components.utils.formatDate
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
import org.mr0btain.ticketman.server.request.CheckInItem
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.data.ticket.CustomerDevice
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
    values: StoredValueDatabase = StoredValueDatabase(),
    private val devices: DeviceDatabase = DeviceDatabase(),
    private val passcodes: PasscodeDatabase = PasscodeDatabase(),
    private val vault: PasscodeVault? = null,
    private val appointments: AppointmentDatabase = AppointmentDatabase(),
    private val cards: CardPayments = CardPayments(PaymentDatabase()),
    private val locations: LocationDatabase = LocationDatabase()
) {
    private val storedValue = StoredValuePayments(values) { Money.format(it, shop.get().currencySymbol) }

    /**
     * Makes the ticket, links the customer and tech, takes any deposit and saves the signature. Every device brought
     * in at the visit gets its own ticket, linked to the others, and the customer's device record. Everything is
     * checked first, then saved together
     */
    fun checkIn(received: CheckInRequest, takenBy: String, actor: Uuid?, location: Uuid? = null): AccountResult<CheckInResponse> {
        // Every device is checked in at the counter's location, taxed at its rates
        val at = location ?: locations.main().identifier
        val shopProfile = shop.at(at)
        if (customers.getById(received.customerIdentifier) == null) return AccountResult.Invalid("Pick a customer")

        // Each device's ticket, checked the same way
        val items = (listOf(CheckInItem(received.ticket, received.deviceIdentifier, received.passcode)) + received.more).map { item ->
            // A custom status is saved with the built-in one it acts as, which is then checked like any other
            val status = when (val resolved = CustomStatusRules.resolve(item.ticket.status, shopProfile.statuses)) {
                is CustomStatusRules.Resolved.Ok -> resolved.status
                is CustomStatusRules.Resolved.Problem -> return AccountResult.Invalid(resolved.message)
            }
            val ticket = item.ticket.copy(status = status, locationIdentifier = at)

            // Check in time is read from the identifier, so it has to be a time based (version 7) one
            if (ticket.identifier.toByteArray()[6].toInt() ushr 4 != 7) return AccountResult.Invalid("Ticket identifier has to be a version 7 UUID")
            if (ticket.device.manufacturer.isBlank() && ticket.device.model.isBlank()) return AccountResult.Invalid("Enter the device")
            if (ticket.problem.isBlank()) return AccountResult.Invalid("Describe the problem")
            if (ticket.status.status != TicketStatusType.IN_PROGRESS && ticket.status.status != TicketStatusType.ON_HOLD) {
                return AccountResult.Invalid("New tickets start in progress or on hold")
            }
            if ((ticket.estimateCents ?: 0) < 0) return AccountResult.Invalid("Estimate can't be negative")
            item.deviceIdentifier?.let { device ->
                if (devices.get(device)?.customerIdentifier != received.customerIdentifier) return AccountResult.Invalid("That device isn't the customer's")
            }
            if (item.passcode.trim().length > IntakeChecklists.MAX_PASSCODE) return AccountResult.Invalid("A passcode can be up to ${IntakeChecklists.MAX_PASSCODE} characters")
            if (item.passcode.isNotBlank() && vault == null) return AccountResult.Invalid("Passcodes can't be kept on this server")
            warrantyProblem(ticket, received.customerIdentifier)?.let { return AccountResult.Invalid(it) }

            // The passcode is the server's to say is set, the ticket can't claim one, nor a warranty of its own yet
            item.copy(ticket = ticket.copy(passcodeSet = false, warrantyUntil = null))
        }
        if (items.map { it.ticket.identifier }.toSet().size != items.size) return AccountResult.Invalid("Each device needs its own ticket")
        if (items.size > MAX_DEVICES) return AccountResult.Invalid("Check in up to $MAX_DEVICES devices at once")
        val request = received.copy(ticket = items.first().ticket, more = items.drop(1))

        request.technicianIdentifier?.let { identifier ->
            val technician = users.getById(identifier)
            if (technician == null || !technician.canAccess(AccessLevel.NORMAL)) return AccountResult.Invalid("That technician can't take tickets")
        }

        request.deposit?.let { deposit ->
            if (deposit.amountCents <= 0) return AccountResult.Invalid("Deposit has to be more than zero")
            storedValue.problem(listOf(deposit), request.customerIdentifier)?.let { return AccountResult.Conflict(it) }
            cards.problem(listOf(deposit))?.let { return AccountResult.Conflict(it) }
        }

        request.appointmentIdentifier?.let { identifier ->
            val appointment = appointments.get(identifier) ?: return AccountResult.Invalid("That appointment doesn't exist")
            if (!appointment.isOpen) return AccountResult.Invalid("That appointment is ${appointment.status.label.lowercase()} already")
            if (appointment.customerIdentifier != null && appointment.customerIdentifier != request.customerIdentifier) {
                return AccountResult.Invalid("That appointment is for another customer")
            }
        }

        request.signature?.problem()?.let { return AccountResult.Invalid(it) }
        if (shopProfile.repairTerms.isNotBlank() && request.signature == null) return AccountResult.Invalid("The customer has to sign the repair terms")

        // Both numbering locks are held until the transaction commits, so a check in at the same moment waits and
        // gets the next number instead of this one. Always ticket then invoice, never the other way round, so two can't deadlock
        val added = try {
            tickets.whileNumbering {
                invoices.whileNumbering {
                    // Under the lock, so the same ticket sent twice at once is only made once
                    if (items.any { tickets.getById(it.ticket.identifier) != null }) return@whileNumbering false

                    saveCheckIn(request, items, takenBy, actor, shopProfile)
                    true
                }
            }
        } catch (ex: StoredValuePayments.ShortOfValue) {
            return AccountResult.Conflict(ex.message)
        } catch (ex: CardPayments.ChargeTaken) {
            return AccountResult.Conflict(ex.message)
        } catch (ex: AppointmentTaken) {
            return AccountResult.Conflict("That appointment was checked in already")
        }

        if (!added) return AccountResult.Conflict("That ticket already exists")

        val saved = items.map { tickets.getById(it.ticket.identifier) ?: return AccountResult.Invalid("Couldn't save the ticket") }
        val invoice = invoices.getByTicket(saved.first().identifier)

        return AccountResult.Done(CheckInResponse(saved.first(), invoice, saved.drop(1)))
    }

    // Everything checked in goes in together or not at all
    private fun saveCheckIn(request: CheckInRequest, items: List<CheckInItem>, takenBy: String, actor: Uuid?, shopProfile: ShopProfile) {
        val now = Clock.System.now().toEpochMilliseconds()
        // Linked when more than one came in, by the first ticket's id
        val group = if (items.size > 1) items.first().ticket.identifier else null

        transaction {
            items.forEachIndexed { index, item ->
                val ticket = item.ticket.copy(pickedUpAt = null, number = null, deviceIdentifier = deviceFor(request.customerIdentifier, item, now), groupIdentifier = group)

                // Pickup fields are the server's to set
                tickets.add(ticket)
                tickets.recordStatusChange(ticket.identifier, ticket.status, takenBy)
                tickets.setCustomer(ticket.identifier, request.customerIdentifier)
                request.technicianIdentifier?.let { tickets.setUser(ticket.identifier, it) }
                item.passcode.trim().takeIf { it.isNotEmpty() }?.let { passcode ->
                    if (!passcodes.set(ticket.identifier, vault!!.seal(passcode), now, takenBy)) throw IllegalStateException("Passcode not saved")
                }

                request.deposit?.takeIf { index == 0 }?.let { deposit ->
                    invoices.getOrCreate(ticket.identifier, request.customerIdentifier, shopProfile.taxBasisPoints, shopProfile.taxRates)?.let { invoice ->
                        val payment = cards.withCharge(
                            storedValue.normalized(
                                Payment(
                                    method = deposit.method, amountCents = deposit.amountCents, receivedAt = now, receivedBy = takenBy,
                                    note = deposit.note.ifBlank { "Deposit" }, reference = deposit.reference
                                )
                            ),
                            deposit.chargeIdentifier
                        )
                        invoices.addPayment(invoice.identifier, payment)
                        storedValue.take(payment, request.customerIdentifier, invoice.identifier, invoice.label, takenBy)
                        cards.claim(payment, invoice.identifier, invoice.label)
                    }
                }

                // One signature at the counter covers every device left
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
                    "Checked in $label" + (request.deposit?.takeIf { index == 0 }?.let { " with a ${Money.format(it.amountCents, shopProfile.currencySymbol)} deposit" } ?: "")
                )
            }

            // The appointment it was booked as, with the first ticket. Someone else checking it in at the same moment undoes this one
            request.appointmentIdentifier?.let { identifier ->
                if (!appointments.markArrived(identifier, items.first().ticket.identifier, request.customerIdentifier)) throw AppointmentTaken()
            }
        }
    }

    /**
     * The customer's device record for a ticket: the one picked, kept up to date with what was typed; theirs with the
     * same serial; or a new one. Inside the check in's transaction
     */
    private fun deviceFor(customer: Uuid, item: CheckInItem, now: Long): Uuid {
        val typed = item.ticket.device
        val existing = item.deviceIdentifier?.let { devices.get(it) }
            ?: typed.serial.trim().takeIf { it.isNotEmpty() }?.let { devices.withSerial(customer, it) }

        if (existing != null) {
            if (existing.device != typed) devices.update(existing.copy(device = typed))
            return existing.identifier
        }

        val made = CustomerDevice(customerIdentifier = customer, device = typed, createdAt = now)
        if (!devices.add(made)) throw IllegalStateException("Device not saved")
        return made.identifier
    }

    // A warranty return has to be of one of the customer's tickets that's still covered
    private fun warrantyProblem(ticket: Ticket, customer: Uuid): String? {
        val original = ticket.warrantyOf?.let { tickets.getById(it) } ?: return if (ticket.warrantyOf != null) "That ticket doesn't exist" else null
        if (tickets.getCustomer(original.identifier)?.identifier != customer) return "Ticket ${original.label} isn't the customer's"
        val until = original.warrantyUntil ?: return "Ticket ${original.label} has no warranty"
        if (until < Clock.System.now().toEpochMilliseconds()) return "Ticket ${original.label}'s warranty ran out on ${formatDate(until)}"
        return null
    }

    companion object {
        /** Most devices at one check in */
        const val MAX_DEVICES = 10
    }
}

/** The appointment being checked in wasn't booked any more when it came to saving */
private class AppointmentTaken : Exception()
