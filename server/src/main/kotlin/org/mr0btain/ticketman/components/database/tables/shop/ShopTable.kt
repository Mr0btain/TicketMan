package org.mr0btain.ticketman.components.database.tables.shop

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.shop.ShopProfile

/**
 * Holds a single row with the shop's profile
 */
object ShopTable : IntIdTable("shop") {
    val profile = json<ShopProfile>("profile", Json { ignoreUnknownKeys = true })
}
