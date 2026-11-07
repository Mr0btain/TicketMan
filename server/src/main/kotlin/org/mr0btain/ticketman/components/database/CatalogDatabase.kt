package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.catalog.CatalogTable
import org.mr0btain.ticketman.data.catalog.CatalogModel
import org.mr0btain.ticketman.data.ticket.DeviceKind
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The shop's repair menu, see [CatalogModel]. Nothing points at a model or its repairs once they're on an invoice
 * (the line is a copy), so they're deleted outright
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class CatalogDatabase {
    private val TAG = "CatalogDatabase"
    private val LIST_TAG = "$TAG:List"
    private val ADD_TAG = "$TAG:Add"
    private val UPDATE_TAG = "$TAG:Update"
    private val REMOVE_TAG = "$TAG:Remove"

    /** The whole menu, by manufacturer then model. Null when it couldn't be read */
    fun all(): List<CatalogModel>? =
        dbQuery(LIST_TAG) { CatalogTable.selectAll().map { it.toModel() } }
            ?.sortedWith(compareBy<CatalogModel> { it.manufacturer.lowercase() }.thenBy { it.model.lowercase() })

    fun get(identifier: Uuid): CatalogModel? =
        dbQuery(LIST_TAG) { CatalogTable.selectAll().where { CatalogTable.id eq identifier }.firstOrNull()?.toModel() }

    fun add(model: CatalogModel): Boolean =
        dbWrite(ADD_TAG) {
            CatalogTable.insert { row ->
                row[CatalogTable.id] = model.identifier
                row[kind] = model.kind.name
                row[manufacturer] = model.manufacturer
                row[CatalogTable.model] = model.model
                row[repairs] = model.repairs
                row[updatedAt] = Clock.System.now().toEpochMilliseconds()
            }
            true
        }

    /** Saves [model], its repairs replacing the ones it had. False when it isn't there */
    fun update(model: CatalogModel): Boolean =
        dbWrite(UPDATE_TAG) {
            CatalogTable.update(where = { CatalogTable.id eq model.identifier }) { row ->
                row[kind] = model.kind.name
                row[manufacturer] = model.manufacturer
                row[CatalogTable.model] = model.model
                row[repairs] = model.repairs
                row[updatedAt] = Clock.System.now().toEpochMilliseconds()
            } > 0
        }

    fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) { CatalogTable.deleteWhere { CatalogTable.id eq identifier } > 0 }

    private fun ResultRow.toModel(): CatalogModel =
        CatalogModel(
            identifier = this[CatalogTable.id].value,
            kind = DeviceKind.entries.firstOrNull { it.name == this[CatalogTable.kind] } ?: DeviceKind.OTHER,
            manufacturer = this[CatalogTable.manufacturer],
            model = this[CatalogTable.model],
            repairs = this[CatalogTable.repairs]
        )
}
