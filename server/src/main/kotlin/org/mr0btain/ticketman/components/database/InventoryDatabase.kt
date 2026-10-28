package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.mr0btain.ticketman.components.database.tables.location.InventoryStockTable
import org.mr0btain.ticketman.components.database.tables.location.StockTransferTable
import org.mr0btain.ticketman.data.inventory.StockTransfer
import org.mr0btain.ticketman.data.inventory.StockTransferRules
import org.mr0btain.ticketman.components.database.tables.location.LocationTable
import org.mr0btain.ticketman.data.inventory.LocationStock
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.error.InventoryError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryNoteTable

/**
 * Used for all Inventory data actions in database. The catalog is the company's; stock is counted per location in
 * [InventoryStockTable], and `InventoryTable.stock` only says whether it's counted at all (`Int.MAX_VALUE` when it
 * isn't). Reads take the location to give the count for, null for every location added up
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class InventoryDatabase(tag: String = "InventoryLocalDatabase") : Database<Inventory>(tag) {
    private val GET_IMAGES_TAG = "$tag:GetImages"
    private val SET_IMAGES_TAG = "$tag:SetImages"
    private val UPDATE_STOCK_TAG = "$tag:UpdateStock"
    private val SET_STOCK_TAG = "$tag:SetStock"
    private val MIGRATE_PRICES_TAG = "$tag:MigrateLegacyPrices"
    private val NAMES_TAG = "$tag:Names"
    private val CODE_TAG = "$tag:Code"
    private val TRANSFER_TAG = "$tag:Transfer"

    override fun getById(identifier: Uuid): Inventory? = getById(identifier, null)

    /** The item with its count at [location], every location added up for null */
    fun getById(identifier: Uuid, location: Uuid?): Inventory? =
        dbQuery(GET_BY_ID_TAG) {
            InventoryEntity.findById(identifier)?.let { withStock(listOf(it), location).single() }
                ?: null.also { AppLogger.i(GET_BY_ID_TAG, InventoryError.NOT_FOUND) }
        }

    /** The catalog, with each item's count at the page's location */
    override fun getPagedItems(request: PagedRequest): List<Inventory>? =
        dbQuery(GET_PAGED_ITEMS_TAG) {
            InventoryEntity
                .all()
                .orderBy(InventoryTable.name.lowerCase() to SortOrder.ASC, InventoryTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .toList()
                .let { withStock(it, request.location) }
        }

    /**
     * The items with their counts filled in from [InventoryStockTable]: at [location], or added up, and each
     * location's in `stockAt`. Inside a transaction
     */
    private fun withStock(entities: List<InventoryEntity>, location: Uuid?): List<Inventory> {
        val rows = entities.map { it.id.value }.chunked(STOCK_CHUNK).flatMap { chunk ->
            InventoryStockTable.selectAll().where { InventoryStockTable.item inList chunk }.map { row ->
                row[InventoryStockTable.item].value to LocationStock(row[InventoryStockTable.location], row[InventoryStockTable.stock], row[InventoryStockTable.reorderPoint])
            }
        }.groupBy({ it.first }, { it.second })

        return entities.map { entity ->
            val item = entity.toInventory()
            if (!item.tracksStock) return@map item.copy(reorderPoint = null)

            val counts = rows[item.identifier].orEmpty()
            val here = location?.let { id -> counts.firstOrNull { it.locationIdentifier == id } }
            item.copy(
                stock = if (location == null) counts.sumOf { it.stock } else here?.stock ?: 0,
                reorderPoint = if (location == null) null else here?.reorderPoint,
                stockAt = counts
            )
        }
    }

    /**
     * Counted items at or below their reorder point at [location], or [threshold] when they have none there,
     * lowest first, with their counts at [location]. Only locations that have had some count: an item never
     * stocked somewhere isn't low there. For null, items low at any location, with their totals
     */
    fun lowStock(threshold: Int, location: Uuid?): List<Inventory>? =
        dbQuery(GET_PAGED_ITEMS_TAG) {
            val low = (InventoryStockTable innerJoin InventoryTable)
                .select(InventoryStockTable.item, InventoryStockTable.stock, InventoryStockTable.reorderPoint)
                .where { (InventoryTable.stock neq Int.MAX_VALUE) and (location?.let { InventoryStockTable.location eq it } ?: Op.TRUE) }
                .filter { it[InventoryStockTable.stock] <= (it[InventoryStockTable.reorderPoint] ?: threshold) }
                .map { it[InventoryStockTable.item].value }
                .distinct()

            low.chunked(STOCK_CHUNK)
                .flatMap { chunk -> withStock(InventoryEntity.find { InventoryTable.id inList chunk }.toList(), location) }
                .sortedWith(compareBy<Inventory> { it.stock }.thenBy { it.name.lowercase() })
        }

    /**
     * How many of an item are at [location]: [Int.MAX_VALUE] when it isn't counted, null when there's no such item
     */
    fun stockAt(identifier: Uuid, location: Uuid): Int? =
        dbQuery(GET_BY_ID_TAG) {
            val item = InventoryEntity.findById(identifier) ?: return@dbQuery null
            if (item.stock == Int.MAX_VALUE) return@dbQuery Int.MAX_VALUE
            InventoryStockTable.select(InventoryStockTable.stock)
                .where { (InventoryStockTable.item eq identifier) and (InventoryStockTable.location eq location) }
                .firstOrNull()?.get(InventoryStockTable.stock) ?: 0
        }

    override fun count(): Long =
        dbQuery(COUNT_TAG) { InventoryEntity.count() } ?: 0L

    override fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) {
            val inventoryEntity = InventoryEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(REMOVE_TAG, InventoryError.NOT_FOUND) }

            val notes = inventoryEntity.notes.toList()
            markImagesDeleted(inventoryEntity.images)
            inventoryEntity.delete()
            deleteNotes(notes)
            true
        }

    override fun search(query: String, limit: Int): List<TmSearchResult>? =
        dbQuery(SEARCH_TAG) {
            val pattern = "${query.trim().lowercase()}%"
            val contains = "%${query.trim().lowercase()}%"

            InventoryEntity
                .find {
                    (InventoryTable.name.lowerCase() like contains) or
                            (InventoryTable.description.lowerCase() like contains) or
                            (InventoryTable.category.lowerCase() like contains) or
                            (InventoryTable.sku.lowerCase() like pattern) or
                            (InventoryTable.barcode.lowerCase() like pattern) or
                            (InventoryTable.id.castTo(VarCharColumnType()) like pattern)
                }
                .orderBy(InventoryTable.name.lowerCase() to SortOrder.ASC, InventoryTable.id to SortOrder.ASC)
                .limit(limit)
                .map { inventoryEntity ->
                    TmSearchResult(
                        identifier = inventoryEntity.id.value,
                        text = when {
                            inventoryEntity.name.startsWith(query, true) -> inventoryEntity.name
                            inventoryEntity.id.value.toString().startsWith(query, true) -> inventoryEntity.id.value.toString()
                            else -> inventoryEntity.name
                        }
                    )
                }
        }

    /** What the shop last paid for one, set when an order arrives */
    fun setCost(identifier: Uuid, costCents: Long): Boolean =
        dbWrite(UPDATE_TAG) {
            InventoryTable.update(where = { InventoryTable.id eq identifier }) { row -> row[InventoryTable.costCents] = costCents } > 0
        }

    /** The item whose barcode or SKU is exactly [code], ignoring case, barcodes first, with its count at [location] */
    fun getByCode(code: String, location: Uuid? = null): Inventory? =
        dbQuery(CODE_TAG) {
            val wanted = code.trim().lowercase()
            if (wanted.isEmpty()) return@dbQuery null
            val found = InventoryEntity.find { InventoryTable.barcode.lowerCase() eq wanted }.firstOrNull()
                ?: InventoryEntity.find { InventoryTable.sku.lowerCase() eq wanted }.firstOrNull()
            found?.let { withStock(listOf(it), location).single() }
        }

    /**
     * Another item already using [item]'s SKU or barcode, so two can't share one
     * @return What's taken and by what, or null
     */
    fun codeClash(item: Inventory): String? =
        dbQuery(CODE_TAG) {
            fun other(column: Column<String>, value: String) =
                value.trim().takeIf { it.isNotEmpty() }?.let { code ->
                    InventoryEntity.find { (column.lowerCase() eq code.lowercase()) and (InventoryTable.id neq item.identifier) }.firstOrNull()
                }

            other(InventoryTable.sku, item.sku)?.let { return@dbQuery "SKU ${item.sku.trim()} is already on ${it.name}" }
            other(InventoryTable.barcode, item.barcode)?.let { return@dbQuery "Barcode ${item.barcode.trim()} is already on ${it.name}" }
            null
        }

    /** Every category in use, by name */
    fun categories(): List<String>? =
        dbQuery(CODE_TAG) {
            InventoryTable.select(InventoryTable.category).withDistinct()
                .map { it[InventoryTable.category] }
                .filter { it.isNotBlank() }
                .sortedBy { it.lowercase() }
        }

    /** Every item's name, for an import to find ones already there. Null when the read failed */
    fun names(): List<String>? =
        dbQuery(NAMES_TAG) {
            InventoryTable.select(InventoryTable.name).map { it[InventoryTable.name] }
        }

    override fun add(data: Inventory): Boolean = add(data, null)

    /**
     * Adds an item, its [Inventory.stock] and reorder point being the count at [location] (the main one for null).
     * Other locations start with none
     */
    fun add(data: Inventory, location: Uuid?): Boolean =
        dbWrite(ADD_TAG) {
            insertInventory(data)
            if (data.tracksStock) {
                InventoryStockTable.insert { row ->
                    row[item] = data.identifier
                    row[InventoryStockTable.location] = location ?: mainLocation()
                    row[stock] = data.stock
                    row[reorderPoint] = data.reorderPoint
                }
            }
            true
        }

    // Must be called inside a transaction
    private fun insertInventory(data: Inventory) {
        InventoryEntity.new(data.identifier) {
            name = data.name
            description = data.description
            // Only whether it's counted, the counts are per location
            stock = if (data.tracksStock) 0 else Int.MAX_VALUE
            costCents = data.costCents
            priceCents = data.priceCents
            taxClass = data.taxClass
            sku = data.sku.trim()
            barcode = data.barcode.trim()
            category = data.category.trim()
            reorderPoint = null
            supplier = data.supplierIdentifier
        }
    }

    override fun update(data: Inventory): Boolean = update(data, null)

    /**
     * Saves everything but the stock count, which only changes through [setStock] and [adjustStock]. The reorder
     * point is [location]'s (the main one for null)
     */
    fun update(data: Inventory, location: Uuid?): Boolean =
        dbWrite(UPDATE_TAG) {
            val inventoryEntity = InventoryEntity.findById(data.identifier)
                ?: return@dbWrite false.also { AppLogger.i(UPDATE_TAG, InventoryError.NOT_FOUND) }

            inventoryEntity.apply {
                name = data.name
                description = data.description
                costCents = data.costCents
                priceCents = data.priceCents
                taxClass = data.taxClass
                sku = data.sku.trim()
                barcode = data.barcode.trim()
                category = data.category.trim()
                supplier = data.supplierIdentifier
            }

            if (inventoryEntity.stock != Int.MAX_VALUE) {
                val at = location ?: mainLocation()
                val updated = InventoryStockTable.update(where = { (InventoryStockTable.item eq data.identifier) and (InventoryStockTable.location eq at) }) { row ->
                    row[reorderPoint] = data.reorderPoint
                }
                if (updated == 0 && data.reorderPoint != null) {
                    InventoryStockTable.insert { row ->
                        row[item] = data.identifier
                        row[InventoryStockTable.location] = at
                        row[stock] = 0
                        row[reorderPoint] = data.reorderPoint
                    }
                }
            }

            true
        }

    /**
     * Moves prices typed as text by older versions, like "$12.50", into the cents columns
     */
    fun migrateLegacyPrices() {
        dbWrite(MIGRATE_PRICES_TAG) {
            InventoryEntity
                .find { (InventoryTable.legacyCost neq "") or (InventoryTable.legacyPrice neq "") }
                .forEach { item ->
                    if (item.costCents == 0L) item.costCents = Money.parse(item.legacyCost) ?: 0
                    if (item.priceCents == 0L) item.priceCents = Money.parse(item.legacyPrice) ?: 0
                    item.legacyCost = ""
                    item.legacyPrice = ""
                }

            true
        }
    }

    /** How [setStock] went */
    enum class StockChange { DONE, NOT_FOUND, CHANGED_SINCE }

    enum class Transfer { DONE, NOT_FOUND, UNTRACKED, SHORT }

    /**
     * Moves [transfer]'s parts between locations in one transaction: taken from the first only while there are
     * enough there (in the same statement, like a pickup), then added at the second, and the move kept
     * @return Null when it failed
     */
    fun transfer(transfer: StockTransfer): Transfer? =
        dbQuery(TRANSFER_TAG) {
            val item = InventoryEntity.findById(transfer.itemIdentifier) ?: return@dbQuery Transfer.NOT_FOUND
            if (item.stock == Int.MAX_VALUE) return@dbQuery Transfer.UNTRACKED

            val from = (InventoryStockTable.item eq transfer.itemIdentifier) and (InventoryStockTable.location eq transfer.fromLocation)
            val taken = InventoryStockTable.update(where = { from and (InventoryStockTable.stock greaterEq transfer.quantity) }) { row ->
                row[stock] = stock - transfer.quantity
            }
            if (taken == 0) return@dbQuery Transfer.SHORT

            val to = (InventoryStockTable.item eq transfer.itemIdentifier) and (InventoryStockTable.location eq transfer.toLocation)
            val added = InventoryStockTable.update(where = { to }) { row -> row[stock] = stock + transfer.quantity }
            if (added == 0) {
                InventoryStockTable.insert { row ->
                    row[InventoryStockTable.item] = transfer.itemIdentifier
                    row[location] = transfer.toLocation
                    row[stock] = transfer.quantity
                }
            }

            StockTransferTable.insert { row ->
                row[StockTransferTable.id] = transfer.identifier
                row[StockTransferTable.item] = transfer.itemIdentifier
                row[fromLocation] = transfer.fromLocation
                row[toLocation] = transfer.toLocation
                row[quantity] = transfer.quantity
                row[note] = transfer.note
                row[movedBy] = transfer.movedBy
                row[movedAt] = transfer.movedAt
            }
            Transfer.DONE
        }

    /** An item's moves between locations, newest first. Null when the read failed */
    fun transfers(identifier: Uuid, limit: Int = StockTransferRules.HISTORY): List<StockTransfer>? =
        dbQuery(TRANSFER_TAG) {
            val name = InventoryEntity.findById(identifier)?.name.orEmpty()
            StockTransferTable.selectAll()
                .where { StockTransferTable.item eq identifier }
                .orderBy(StockTransferTable.movedAt to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    StockTransfer(
                        identifier = row[StockTransferTable.id].value,
                        itemIdentifier = identifier,
                        itemName = name,
                        fromLocation = row[StockTransferTable.fromLocation],
                        toLocation = row[StockTransferTable.toLocation],
                        quantity = row[StockTransferTable.quantity],
                        note = row[StockTransferTable.note],
                        movedBy = row[StockTransferTable.movedBy],
                        movedAt = row[StockTransferTable.movedAt]
                    )
                }
        }

    /**
     * Sets the count at [location] by hand, only if it's still [from]. Done in one statement, so a pickup taking
     * stock at the same moment either lands first (and this is [StockChange.CHANGED_SINCE]) or after.
     * [Int.MAX_VALUE] stops counting the item anywhere; a count for one that wasn't counted starts counting it,
     * with none at the other locations
     * @return How it went, null if it failed
     */
    fun setStock(identifier: Uuid, location: Uuid, from: Int, to: Int): StockChange? =
        dbQuery(SET_STOCK_TAG) {
            val item = InventoryEntity.findById(identifier) ?: return@dbQuery StockChange.NOT_FOUND
            val tracked = item.stock != Int.MAX_VALUE
            val here = (InventoryStockTable.item eq identifier) and (InventoryStockTable.location eq location)
            val current = if (!tracked) Int.MAX_VALUE
                else InventoryStockTable.select(InventoryStockTable.stock).where { here }.firstOrNull()?.get(InventoryStockTable.stock) ?: 0
            if (current != from) return@dbQuery StockChange.CHANGED_SINCE

            when {
                to == Int.MAX_VALUE -> {
                    item.stock = Int.MAX_VALUE
                    InventoryStockTable.deleteWhere { InventoryStockTable.item eq identifier }
                    StockChange.DONE
                }

                !tracked -> {
                    item.stock = 0
                    InventoryStockTable.insert { row ->
                        row[InventoryStockTable.item] = identifier
                        row[InventoryStockTable.location] = location
                        row[stock] = to
                    }
                    StockChange.DONE
                }

                else -> {
                    val updated = InventoryStockTable.update(where = { here and (InventoryStockTable.stock eq from) }) { row -> row[stock] = to }
                    when {
                        updated > 0 -> StockChange.DONE
                        // No row yet is a count of none
                        from == 0 && InventoryStockTable.selectAll().where { here }.empty() -> {
                            InventoryStockTable.insert { row ->
                                row[InventoryStockTable.item] = identifier
                                row[InventoryStockTable.location] = location
                                row[stock] = to
                            }
                            StockChange.DONE
                        }
                        else -> StockChange.CHANGED_SINCE
                    }
                }
            }
        }

    /**
     * Takes stock away at [location], or puts it back with a negative [amount], in one statement so nothing
     * written in between is lost. Taking never goes below zero. Items with untracked stock, and ones that are gone,
     * are left alone
     * @return False when there isn't [amount] to take
     */
    fun adjustStock(identifier: Uuid, location: Uuid, amount: Int): Boolean =
        dbWrite(UPDATE_STOCK_TAG) {
            val item = InventoryEntity.findById(identifier)
            if (item == null || item.stock == Int.MAX_VALUE) return@dbWrite true

            val here = (InventoryStockTable.item eq identifier) and (InventoryStockTable.location eq location)
            val updated = InventoryStockTable.update(where = { if (amount > 0) here and (InventoryStockTable.stock greaterEq amount) else here }) { row ->
                row[stock] = stock - amount
            }
            if (updated > 0) return@dbWrite true

            // Nothing counted here yet: putting some back starts the count, taking some can't
            if (amount <= 0 && InventoryStockTable.selectAll().where { here }.empty()) {
                InventoryStockTable.insert { row ->
                    row[InventoryStockTable.item] = identifier
                    row[InventoryStockTable.location] = location
                    row[stock] = -amount
                }
                return@dbWrite true
            }
            false
        }

    /**
     * Moves the counts from before locations, kept on each item, to [main], with the reorder points. Run once, by
     * LocalDatabase
     */
    fun moveStockTo(main: Uuid) {
        InventoryEntity.find { InventoryTable.stock neq Int.MAX_VALUE }.forEach { entity ->
            InventoryStockTable.insert { row ->
                row[item] = entity.id.value
                row[location] = main
                row[stock] = entity.stock
                row[reorderPoint] = entity.reorderPoint
            }
            entity.stock = 0
            entity.reorderPoint = null
        }
    }

    // The main location, for writes that don't say. Inside a transaction
    private fun mainLocation(): Uuid =
        LocationTable.select(LocationTable.id).where { LocationTable.main eq true }.firstOrNull()?.get(LocationTable.id)?.value
            ?: LocationDatabase().ensureMain().identifier

    override val noteLinkColumn: Column<EntityID<Uuid>> = InventoryNoteTable.note

    private companion object {
        const val STOCK_CHUNK = 500
    }

    override fun addNote(identifier: Uuid, note: Note): Boolean =
        attachNote(note, InventoryError.NOT_FOUND, { InventoryEntity.findById(identifier) }) { inventoryEntity, noteEntity ->
            inventoryEntity.notes = SizedCollection(inventoryEntity.notes + noteEntity)
        }

    override fun getNotes(identifier: Uuid): List<Note>? =
        dbQuery(GET_NOTES_TAG) {
            InventoryEntity.findById(identifier)?.notes?.map { noteEntity -> noteEntity.toNote() }
                ?: null.also { AppLogger.i(GET_NOTES_TAG, InventoryError.NOT_FOUND) }
        }

    /**
     * Gets images for an inventory item
     * @param identifier The identifier of the parent inventory item
     */
    fun getImages(identifier: Uuid): List<StoredImage>? =
        dbQuery(GET_IMAGES_TAG) {
            InventoryEntity.findById(identifier)?.images?.map { it.toStoredImage() }
                ?: null.also { AppLogger.i(GET_IMAGES_TAG, InventoryError.NOT_FOUND) }
        }

    /**
     * Makes an item's photos exactly [images], see [linkImages] for which are taken. Photos left off are marked
     * deleted and their files go with a later sweep
     * @param identifier The identifier of the inventory item
     * @return Whether the item was found
     */
    fun setImages(identifier: Uuid, images: List<StoredImage>): Boolean =
        dbWrite(SET_IMAGES_TAG) {
            val inventoryEntity = InventoryEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(SET_IMAGES_TAG, InventoryError.NOT_FOUND) }

            inventoryEntity.images = SizedCollection(linkImages(images, inventoryEntity.images.toList()))
            true
        }
}
