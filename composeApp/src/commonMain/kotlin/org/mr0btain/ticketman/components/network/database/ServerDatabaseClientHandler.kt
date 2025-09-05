package org.mr0btain.ticketman.components.network.database

import io.ktor.client.HttpClient
import org.mr0btain.ticketman.components.network.encryption.ClientEncryptionHandler
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Basically just a holder to organize all these in a single class for use in view model
 */
class ServerDatabaseClientHandler(
    httpClient: HttpClient,
    encryptionHandler: ClientEncryptionHandler
) {
    companion object {
        private const val TAG = "ServerDatabaseClient"
        private const val CHANGE_SERVER_URL_TAG = "$TAG:ChangeServerUrl"
    }

    // Clients used to interact with server. Only real difference is the types and server urls
    // Could possibly combine this into one client somehow. just supplying the url for each call?
    val customerClient = CustomerRemoteRepositoryImpl(httpClient, encryptionHandler)
    val ticketClient = TicketRemoteRepositoryImpl(httpClient, encryptionHandler)
    val inventoryClient = InventoryRemoteRepositoryImpl(httpClient, encryptionHandler)
    val userClient = UserRemoteRepositoryImpl(httpClient, encryptionHandler)

    // For updating the server url
    fun changeServerUrl(url: String) {
        customerClient.changeUrl(url)
        ticketClient.changeUrl(url)
        inventoryClient.changeUrl(url)
        AppLogger.m(CHANGE_SERVER_URL_TAG, SimpleError.SUCCESS)
    }
}