package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.StoredValueDatabase
import org.mr0btain.ticketman.components.service.StoredValueService
import org.mr0btain.ticketman.server.GiftCardPaths
import org.mr0btain.ticketman.server.request.BalanceAdjustRequest

/**
 * Gift cards: anyone logged in can check a card's balance, admins list them and correct one
 */
fun Route.giftCardRoute(
    database: StoredValueDatabase,
    service: StoredValueService
) {
    authenticate("restricted") {
        get(GiftCardPaths.GET) {
            val card = call.parameters["code"]?.let { database.giftCard(it) }

            if (card != null) call.respond(HttpStatusCode.OK, card) else call.respond(HttpStatusCode.NotFound)
        }
    }

    authenticate("admin") {
        get(GiftCardPaths.FETCH) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()
            val cards = database.giftCards(request)

            if (cards != null) call.respond(HttpStatusCode.OK, cards) else call.respond(HttpStatusCode.InternalServerError)
        }

        post(GiftCardPaths.ADJUST) {
            val code = call.parameters["code"]

            if (code == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.adjustGiftCard(code, call.receive<BalanceAdjustRequest>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }
}
