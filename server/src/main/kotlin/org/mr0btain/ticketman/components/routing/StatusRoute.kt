package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.payments.PaymentService
import org.mr0btain.ticketman.components.service.StatusPage
import org.mr0btain.ticketman.components.service.StatusService
import org.mr0btain.ticketman.components.service.BookingService
import kotlinx.datetime.LocalDate
import org.mr0btain.ticketman.data.appointment.BookingRules
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.server.StatusPaths
import kotlin.uuid.ExperimentalUuidApi

/**
 * The customer status page, under /status. HTML for a phone's browser, no login: the link's token is the key, or
 * the ticket number with the last 4 digits of the customer's phone. Meant to be the only part of the server a
 * reverse proxy puts on the internet (docs/operations.md)
 */
@OptIn(ExperimentalUuidApi::class)
fun Route.statusRoute(
    service: StatusService,
    shopDatabase: ShopDatabase,
    payments: PaymentService? = null,
    booking: BookingService? = null
) {
    fun canBook() = booking != null && shopDatabase.get().booking.enabled

    // The lookup form
    get {
        call.respondStatusPage(HttpStatusCode.OK, StatusPage.lookupForm(shopDatabase.get(), canBook = canBook()))
    }

    // Booking a visit: before the ticket's page, whose {token} would take "book" too
    get(StatusPaths.BOOK) {
        val location = call.queryParameters["location"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val day = call.queryParameters["day"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val choices = booking?.choices(location) ?: return@get call.respondStatusPage(
            HttpStatusCode.NotFound,
            StatusPage.message(shopDatabase.get(), "Booking online is off", "Call us to book a visit.", "../status")
        )
        call.respondStatusPage(HttpStatusCode.OK, StatusPage.booking(choices, day))
    }

    post(StatusPaths.BOOK) {
        val params = call.receiveParameters()
        val form = BookingService.Form(
            location = params["location"]?.let { runCatching { Uuid.parse(it) }.getOrNull() },
            startsAt = params["time"]?.toLongOrNull(),
            name = params["name"].orEmpty().take(BookingRules.MAX_FIELD + 1),
            phone = params["phone"].orEmpty().take(BookingRules.MAX_FIELD + 1),
            email = params["email"].orEmpty().take(BookingRules.MAX_FIELD + 1),
            device = params["device"].orEmpty().take(BookingRules.MAX_FIELD + 1),
            problem = params["problem"].orEmpty().take(BookingRules.MAX_PROBLEM + 1)
        )
        val shop = shopDatabase.get()

        when (val booked = booking?.book(form, call.request.origin.remoteHost) ?: BookingService.Booked.Off) {
            is BookingService.Booked.Done -> call.respondStatusPage(HttpStatusCode.OK, StatusPage.booked(booked.choices, booked.appointment))
            is BookingService.Booked.Problem -> {
                val day = form.startsAt?.let { time -> booked.choices.days.firstOrNull { time in it.times }?.date }
                call.respondStatusPage(HttpStatusCode.BadRequest, StatusPage.booking(booked.choices, day, form, booked.message))
            }
            BookingService.Booked.Limited -> call.respondStatusPage(
                HttpStatusCode.TooManyRequests,
                StatusPage.message(shop, "Too many bookings", "Wait a while, or call us to book.", "../status")
            )
            BookingService.Booked.Off -> call.respondStatusPage(
                HttpStatusCode.NotFound,
                StatusPage.message(shop, "Booking online is off", "Call us to book a visit.", "../status")
            )
        }
    }

    // The form's answer: off to the ticket's page, or the form again saying why not
    post {
        val form = call.receiveParameters()
        val number = form["number"].orEmpty().take(MAX_FIELD)
        val phone = form["phone"].orEmpty().take(MAX_FIELD)
        val shop = shopDatabase.get()

        when (val found = service.lookup(number, phone, call.request.origin.remoteHost)) {
            // Relative, so it works under whatever path the proxy puts this at
            is StatusService.Lookup.Match -> {
                call.response.header(HttpHeaders.Location, "status/${found.token}")
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(HttpStatusCode.SeeOther)
            }

            StatusService.Lookup.NoMatch -> call.respondStatusPage(
                HttpStatusCode.NotFound,
                StatusPage.lookupForm(shop, "We couldn't find a repair with that number and phone. Check both and try again.", number)
            )

            StatusService.Lookup.Limited -> call.respondStatusPage(
                HttpStatusCode.TooManyRequests,
                StatusPage.lookupForm(shop, "Too many tries. Wait a while, use the link on your claim slip, or call us.", number)
            )
        }
    }

    get(StatusPaths.PAGE) {
        val shop = shopDatabase.get()

        when (val found = service.page(call.parameters["token"].orEmpty(), call.request.origin.remoteHost)) {
            is StatusService.Found.Page -> call.respondStatusPage(HttpStatusCode.OK, StatusPage.ticket(found.view))

            StatusService.Found.NotFound -> call.respondStatusPage(
                HttpStatusCode.NotFound,
                StatusPage.message(shop, "That link doesn't work", "It may have been replaced by a newer one. Look your repair up with its ticket number instead.", "../status")
            )

            StatusService.Found.Limited -> call.respondStatusPage(
                HttpStatusCode.TooManyRequests,
                StatusPage.message(shop, "Too many tries", "Wait a while and try again.", "../status")
            )
        }
    }

    // The customer's answer to the waiting estimate, back to their page when it's saved
    post(StatusPaths.ESTIMATE) {
        val token = call.parameters["token"].orEmpty()
        val form = call.receiveParameters()
        val approve = when (form["decision"]) {
            "approve" -> true
            "decline" -> false
            else -> return@post call.respond(HttpStatusCode.BadRequest)
        }
        val shop = shopDatabase.get()

        when (val answer = service.answerEstimate(token, approve, form["name"].orEmpty().take(MAX_FIELD * 4), call.request.origin.remoteHost)) {
            StatusService.Answer.Saved -> {
                call.response.header(HttpHeaders.Location, "../${token}")
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(HttpStatusCode.SeeOther)
            }

            is StatusService.Answer.Problem -> call.respondStatusPage(HttpStatusCode.Conflict, StatusPage.ticket(answer.view))

            StatusService.Answer.NotFound -> call.respondStatusPage(
                HttpStatusCode.NotFound,
                StatusPage.message(shop, "That link doesn't work", "It may have been replaced by a newer one. Call us to answer the estimate.", "../../status")
            )

            StatusService.Answer.Limited -> call.respondStatusPage(
                HttpStatusCode.TooManyRequests,
                StatusPage.message(shop, "Too many tries", "Wait a while and try again.", "../../status")
            )
        }
    }

    // Off to the processor's checkout for the balance. A GET from a plain link, see StatusPage
    get(StatusPaths.PAY) {
        val token = call.parameters["token"].orEmpty()
        val shop = shopDatabase.get()

        when (val pay = service.pay(token, call.request.origin.remoteHost)) {
            is StatusService.Pay.Checkout -> {
                call.response.header(HttpHeaders.Location, pay.url)
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.response.header("Referrer-Policy", "no-referrer")
                call.respond(HttpStatusCode.SeeOther)
            }

            is StatusService.Pay.Problem -> call.respondStatusPage(HttpStatusCode.Conflict, StatusPage.ticket(pay.view))

            StatusService.Pay.NotFound -> call.respondStatusPage(
                HttpStatusCode.NotFound,
                StatusPage.message(shop, "That link doesn't work", "It may have been replaced by a newer one. Call us to pay.", "../../status")
            )

            StatusService.Pay.Limited -> call.respondStatusPage(
                HttpStatusCode.TooManyRequests,
                StatusPage.message(shop, "Too many tries", "Wait a while and try again.", "../../status")
            )
        }
    }

    // The card processor saying a payment changed. Checked against its signature, then only used to ask about charges
    post(StatusPaths.PAYMENT_WEBHOOK) {
        if (payments == null) return@post call.respond(HttpStatusCode.NotFound)
        val body = call.receiveText().take(MAX_WEBHOOK)
        val headers = call.request.headers

        when (payments.webhook(call.parameters["provider"].orEmpty(), { headers[it] }, body)) {
            PaymentService.Webhook.Done -> call.respond(HttpStatusCode.OK)
            PaymentService.Webhook.NotFound -> call.respond(HttpStatusCode.NotFound)
            PaymentService.Webhook.Refused -> call.respond(HttpStatusCode.BadRequest)
        }
    }

    get(StatusPaths.PHOTO) {
        val identifier = call.uuidParameter("identifier")
        val variant = call.parameters["variant"]?.let { ImageVariant.fromPath(it) }
        val found = if (identifier != null && variant != null) {
            service.photo(call.parameters["token"].orEmpty(), identifier, variant, call.request.origin.remoteHost)
        } else null

        if (found == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        // Kept briefly, since a replaced link should stop showing them
        call.response.header(HttpHeaders.CacheControl, "private, max-age=3600")
        call.response.header("Referrer-Policy", "no-referrer")
        call.response.header("X-Content-Type-Options", "nosniff")
        call.respondFile(found.first)
    }
}

/**
 * A status page with headers that keep it out of caches, search engines and frames, and the token in its address
 * out of any Referer
 */
private suspend fun RoutingCall.respondStatusPage(status: HttpStatusCode, html: String) {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("Referrer-Policy", "no-referrer")
    response.header("X-Robots-Tag", "noindex, nofollow")
    response.header("X-Content-Type-Options", "nosniff")
    response.header("X-Frame-Options", "DENY")
    response.header(
        "Content-Security-Policy",
        "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"
    )
    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
}

/** Longer than any ticket number or phone, so a huge field isn't parsed */
private const val MAX_FIELD = 40

/** Far more than a payment event, which is a few KB */
private const val MAX_WEBHOOK = 512 * 1024
