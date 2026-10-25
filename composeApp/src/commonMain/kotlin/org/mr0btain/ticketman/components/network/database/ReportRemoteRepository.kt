package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.report.PeriodReport
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.ReportPaths

/**
 * Client for the reports under /api/report, admin only
 */
@Suppress("PrivatePropertyName")
class ReportRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "ReportRemoteRepository"
    private val PERIOD_TAG = "$TAG:Period"
    private val COMMISSIONS_TAG = "$TAG:Commissions"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.REPORT}"
        }

    /** From [from] to [to], days like 2026-10-01, both included */
    suspend fun period(from: String, to: String): TmResult<PeriodReport, ServerError> =
        data(PERIOD_TAG) {
            client.get {
                url("$baseUrl${ReportPaths.PERIOD}")
                parameter("from", from)
                parameter("to", to)
            }
        }

    suspend fun commissions(): TmResult<List<CommissionRate>, ServerError> =
        data(COMMISSIONS_TAG) { client.get { url("$baseUrl${ReportPaths.COMMISSIONS}") } }

    /** Replaces every rate, a user left out has none */
    suspend fun setCommissions(rates: List<CommissionRate>): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(COMMISSIONS_TAG) {
            client.post {
                url("$baseUrl${ReportPaths.COMMISSIONS}")
                contentType(ContentType.Application.Json)
                setBody(rates)
            }
        }

        return handleActionHttpResponse(COMMISSIONS_TAG, response)
    }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}
