package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.network.encryption.ClientEncryptionHandler
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.data.encryption.EncryptedData
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse

class TicketServerDatabaseClientImpl(
    private val client: HttpClient,
    private val encryptionHandler: ClientEncryptionHandler
) : ServerDatabaseClient(
    tag = "TicketDatabaseClient",
    client = client,
    encryptionHandler = encryptionHandler
) {
    override var serverUrl = ""

    override fun changeUrl(url: String) {
        serverUrl = "$url/api/data/ticket"
    }

    override suspend fun sync(data: List<MainDataItem>): List<TicketInfo> {
        if (serverUrl.isNotBlank()) {
            val identifiers = data.map { it.identifier }

            if (identifiers.isNotEmpty()) {
                networkErrorTryCatch(SYNC_TAG) {
                    client.post {
                        url("$serverUrl/sync")
                        contentType(ContentType.Application.Json)
                        setBody(
                            encryptionHandler.encryptData(
                                Json.encodeToString(SyncRequest(identifiers))
                            )
                        )
                    }
                }?.let { response -> handleHttpResponse<EncryptedData>(SYNC_TAG, response) }
                    ?.onSuccess { encryptedMessage ->
                        encryptionHandler.decryptData(encryptedMessage)?.let { decrypted ->
                            val syncResponse = Json.decodeFromString<SyncResponse>(decrypted)
                            val requested = data.filter { item -> syncResponse.requested.contains(item.identifier) }

                            if (requested.isNotEmpty()) batchSend(requested)

                            return syncResponse.data.filterIsInstance<TicketInfo>()
                        }
                    }
            } else return getAll()
        }

        return listOf()
    }

    override suspend fun getAll(): List<TicketInfo> {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(GET_ALL_TAG) {
                client.get("${serverUrl}/all")
            }?.let { response -> handleHttpResponse<EncryptedData>(GET_ALL_TAG, response) }
                ?.onSuccess { encryptedMessage ->
                    encryptionHandler.decryptData(encryptedMessage)?.let { decrypted ->
                        return Json.decodeFromString<List<TicketInfo>>(decrypted)
                    }
                }
        }

        return emptyList()
    }
}