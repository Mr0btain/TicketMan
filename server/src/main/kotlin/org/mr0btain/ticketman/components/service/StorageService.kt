package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.notify.NotificationService
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.shop.Storage
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.time.Clock

/**
 * Devices left after they're ready, under the shop's [org.mr0btain.ticketman.data.shop.StorageTerms]: customers are
 * told before the fee starts, and devices left past the abandon limit are treated as abandoned. The fee itself goes
 * on the invoice when it's opened (`InvoiceService.forTicket`). Run by the hourly sweep
 * @param now Epoch millis, swapped in tests
 */
class StorageService(
    private val tickets: TicketDatabase,
    private val shop: ShopDatabase,
    private val notifications: NotificationService,
    private val auditLog: AuditLogDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    /** @return How many tickets were treated as abandoned */
    fun sweep(): Int {
        notifications.queueStorageNotices()

        val terms = shop.get().storage
        if (terms.abandonAfterDays <= 0) return 0

        val time = now()
        return tickets.readyForPickup().orEmpty()
            .filter { Storage.daysReady(it, time) >= terms.abandonAfterDays }
            .count { ticket ->
                if (!tickets.markAbandoned(ticket.identifier, time)) return@count false
                auditLog.log(null, AuditAction.UPDATE, "ticket", ticket.identifier.toString(), "Ticket ${ticket.label} left ${terms.abandonAfterDays} days after it was ready, treated as abandoned", actorName = "server")
                notifications.abandoned(ticket.copy(abandonedAt = time))
                true
            }
            .also { if (it > 0) AppLogger.i(TAG, "$it devices abandoned") }
    }

    private companion object {
        const val TAG = "StorageService"
    }
}
