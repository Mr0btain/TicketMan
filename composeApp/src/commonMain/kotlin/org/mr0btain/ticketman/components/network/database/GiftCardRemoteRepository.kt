package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.giftcard.GiftCardCode
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiQuery
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.GiftCardPaths
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.request.BalanceAdjustRequest

/**
 * Client for gift cards under /api/giftcard
 */
@Suppress("PrivatePropertyName")
class GiftCardRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "GiftCardRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.GIFT_CARD}"
        }

    /** The card with this code, `NOT_FOUND` when there's none */
    suspend fun get(code: String): TmResult<GiftCard, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${GiftCardPaths.GET.fill(GiftCardCode.normalize(code).encodeURLPathPart())}") } }

    /** Admin only, newest first */
    suspend fun fetch(offset: Long, limit: Int): TmResult<List<GiftCard>, ServerError> =
        data(GET_TAG) {
            client.get {
                url("$baseUrl${GiftCardPaths.FETCH}")
                parameter(ApiQuery.OFFSET, offset)
                parameter(ApiQuery.LIMIT, limit)
            }
        }

    /** Admin only */
    suspend fun adjust(code: String, request: BalanceAdjustRequest): TmResult<GiftCard, ServerError> =
        data(CHANGE_TAG) {
            client.post {
                url("$baseUrl${GiftCardPaths.ADJUST.fill(code.encodeURLPathPart())}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}
