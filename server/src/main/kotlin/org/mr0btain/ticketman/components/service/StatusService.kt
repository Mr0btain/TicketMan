package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.customerChoice
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.logging.AppLogger
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What the customer status page shows, and the lookup that finds it without the link. Only ever hands out what a
 * customer may see: statuses as the shop shares them, notes marked for the customer and their photos, the
 * estimate and balance, and the customer's first name. Never internal notes, who did what, or contact details
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class StatusService(
    private val tickets: TicketDatabase,
    private val customers: CustomerDatabase,
    private val invoices: InvoiceDatabase,
    private val shop: ShopDatabase,
    private val images: ImageService,
    private val estimates: EstimateService,
    // Wrong lookups per device, and per ticket number so guessing 4 digits from many addresses goes nowhere
    private val lookupsByAddress: RequestLimiter = RequestLimiter(LOOKUPS_PER_ADDRESS, 15.minutes),
    private val lookupsByNumber: RequestLimiter = RequestLimiter(LOOKUPS_PER_NUMBER, 24.hours),
    private val badLinksByAddress: RequestLimiter = RequestLimiter(BAD_LINKS_PER_ADDRESS, 15.minutes)
) {
    private val TAG = "StatusService"
    private val LOOKUP_TAG = "$TAG:Lookup"

    /** One status in the timeline, as the customer sees it */
    data class Step(val choice: StatusChoice, val time: Long)

    /** A note marked for the customer */
    data class Update(val time: Long, val message: String, val photos: List<StoredImage>)

    data class View(
        val shop: ShopProfile,
        val ticket: Ticket,
        val firstName: String,
        val current: StatusChoice,
        val timeline: List<Step>,
        val updates: List<Update>,
        /** Null when there isn't one, or it was voided */
        val invoice: Invoice?,
        /** Waiting for the customer to answer */
        val estimate: Estimate? = null,
        /** The newest one they answered, shown when nothing's waiting */
        val answered: Estimate? = null,
        /** Why their last answer didn't go through */
        val problem: String? = null
    )

    sealed interface Found {
        data class Page(val view: View) : Found
        data object NotFound : Found
        data object Limited : Found
    }

    sealed interface Lookup {
        data class Match(val token: String) : Lookup
        data object NoMatch : Lookup

        /** Too many wrong tries, from this device or for this ticket */
        data object Limited : Lookup
    }

    /** The page for [token], counting a bad one against [address] */
    fun page(token: String, address: String): Found {
        if (badLinksByAddress.isLimited(address)) return Found.Limited

        val ticket = tickets.getByStatusToken(token) ?: return Found.NotFound.also { badLinksByAddress.record(address) }
        return Found.Page(view(ticket))
    }

    sealed interface Answer {
        data object Saved : Answer

        /** Shown on the page again, with why */
        data class Problem(val view: View) : Answer
        data object NotFound : Answer
        data object Limited : Answer
    }

    /**
     * The customer approving or declining the waiting estimate on their page, signing with their typed name
     */
    fun answerEstimate(token: String, approve: Boolean, name: String, address: String): Answer {
        if (badLinksByAddress.isLimited(address)) return Answer.Limited
        val ticket = tickets.getByStatusToken(token) ?: return Answer.NotFound.also { badLinksByAddress.record(address) }

        val signed = name.trim()
        if (signed.length !in MIN_NAME..MAX_NAME) return Answer.Problem(view(ticket).copy(problem = "Type your full name to sign"))

        return when (val result = estimates.decideOnPage(ticket, approve, signed)) {
            is AccountResult.Done -> Answer.Saved
            AccountResult.NotFound -> Answer.Problem(view(ticket).copy(problem = "There's no estimate waiting any more"))
            is AccountResult.Conflict -> Answer.Problem(view(ticket).copy(problem = "${result.message}. Call us if that's not right"))
            is AccountResult.Invalid -> Answer.Problem(view(ticket).copy(problem = "That didn't go through, call us to approve it"))
        }
    }

    private fun view(ticket: Ticket): View {
        val profile = shop.get()
        val statuses = profile.statuses

        // A status the shop doesn't share reads as the one it acts as, so two in a row can look the same
        val timeline = tickets.getStatusHistory(ticket.identifier).orEmpty()
            .map { Step(statuses.customerChoice(it.choice), it.time) }
            .fold(emptyList<Step>()) { steps, step -> if (steps.lastOrNull()?.choice == step.choice) steps else steps + step }
            .ifEmpty { listOf(Step(statuses.customerChoice(ticket.status.choice), ticket.status.time)) }

        val updates = tickets.getNotes(ticket.identifier).orEmpty()
            .filter { it.customerVisible }
            .map { Update(it.identifier.timestampMillis(), it.message, it.images) }
            .sortedByDescending { it.time }

        val customer = tickets.getCustomer(ticket.identifier)?.let { customers.getById(it.identifier) }

        return View(
            shop = profile,
            ticket = ticket,
            firstName = customer?.name?.trim()?.substringBefore(' ').orEmpty(),
            current = statuses.customerChoice(ticket.status.choice),
            timeline = timeline,
            updates = updates,
            invoice = invoices.getByTicket(ticket.identifier)?.takeIf { it.status != InvoiceStatus.VOID },
            estimate = estimates.pending(ticket.identifier),
            answered = estimates.lastAnswered(ticket.identifier)
        )
    }

    /**
     * Finds a ticket by its number and the last 4 digits of its customer's phone. Wrong tries are counted per
     * device and per ticket number; a match doesn't count
     */
    fun lookup(number: String, phoneEnding: String, address: String): Lookup {
        val parsed = TicketNumber.parse(number.filterNot { it.isWhitespace() })
        val numberKey = parsed?.toString() ?: number.trim()

        if (lookupsByAddress.isLimited(address) || lookupsByNumber.isLimited(numberKey)) return Lookup.Limited

        val digits = phoneEnding.filter { it.isDigit() }
        val ticket = parsed?.takeIf { digits.length == PHONE_DIGITS }?.let { tickets.getByNumber(it) }
        val phone = ticket?.let { tickets.getCustomer(it.identifier) }?.let { customers.getById(it.identifier) }?.phone
            ?.filter { it.isDigit() }.orEmpty()
        val token = ticket?.statusToken

        if (token != null && phone.length >= PHONE_DIGITS && phone.takeLast(PHONE_DIGITS) == digits) return Lookup.Match(token)

        lookupsByAddress.record(address)
        lookupsByNumber.record(numberKey)
        AppLogger.i(LOOKUP_TAG, "No match for $numberKey from $address")
        return Lookup.NoMatch
    }

    /**
     * A photo on one of the ticket's customer updates, with its hash for the ETag. Null when the link is wrong or
     * the photo isn't on an update the customer can see
     */
    fun photo(token: String, identifier: Uuid, variant: ImageVariant, address: String): Pair<File, String>? {
        if (variant !in PHOTO_VARIANTS || badLinksByAddress.isLimited(address)) return null

        val ticket = tickets.getByStatusToken(token) ?: return null.also { badLinksByAddress.record(address) }
        val shared = tickets.getNotes(ticket.identifier).orEmpty()
            .filter { it.customerVisible }
            .any { note -> note.images.any { it.identifier == identifier } }

        return if (shared) images.fileFor(identifier, variant, null) else null
    }

    companion object {
        const val PHONE_DIGITS = 4
        const val LOOKUPS_PER_ADDRESS = 10
        const val LOOKUPS_PER_NUMBER = 10
        const val BAD_LINKS_PER_ADDRESS = 30
        const val MIN_NAME = 2
        const val MAX_NAME = 100

        /** The original is only for staff */
        val PHOTO_VARIANTS = setOf(ImageVariant.THUMB, ImageVariant.DISPLAY)
    }
}
