package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import org.mr0btain.ticketman.data.inventory.Inventory

class InventoryRemoteRepositoryImpl(
    client: HttpClient
) : RemoteRepository<Inventory>(
    tag = "InventoryRemoteRepositoryImpl",
    client = client,
    route = "api/data/inventory"
)