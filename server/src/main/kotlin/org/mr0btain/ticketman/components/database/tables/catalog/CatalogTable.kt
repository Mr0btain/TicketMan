package org.mr0btain.ticketman.components.database.tables.catalog

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.catalog.RepairOffer

/** The repair menu: one row per device model, its repairs kept with it as JSON since they're always read together */
object CatalogTable : UuidTable("repair_catalog") {
    val kind = text("kind")
    val manufacturer = text("manufacturer").default("")
    val model = text("model")
    val repairs = json<List<RepairOffer>>("repairs", Json { ignoreUnknownKeys = true }).default(emptyList())
    val updatedAt = long("updated_at").default(0)
}
