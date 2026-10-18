package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.error.InventoryError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryNoteTable

/**
 * Used for all Inventory data actions in database
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class InventoryDatabase(tag: String = "InventoryLocalDatabase") : Database<Inventory>(tag) {
    private val GET_IMAGES_TAG = "$tag:GetImages"
    private val SET_IMAGES_TAG = "$tag:SetImages"
    private val UPDATE_STOCK_TAG = "$tag:UpdateStock"
    private val MIGRATE_PRICES_TAG = "$tag:MigrateLegacyPrices"

    override fun getById(identifier: Uuid): Inventory? =
        dbQuery(GET_BY_ID_TAG) {
            InventoryEntity.findById(identifier)?.toInventory()
                ?: null.also { AppLogger.i(GET_BY_ID_TAG, InventoryError.NOT_FOUND) }
        }

    override fun getPagedItems(request: PagedRequest): List<Inventory>? =
        dbQuery(GET_PAGED_ITEMS_TAG) {
            InventoryEntity
                .all()
                .orderBy(InventoryTable.name.lowerCase() to SortOrder.ASC, InventoryTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toInventory() }
        }

    override fun count(): Long =
        dbQuery(COUNT_TAG) { InventoryEntity.count() } ?: 0L

    override fun addAll(data: List<Inventory>): Boolean =
        dbWrite(ADD_ALL_TAG) {
            data.forEach { item -> insertInventory(item) }
            true
        }

    override fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) {
            val inventoryEntity = InventoryEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(REMOVE_TAG, InventoryError.NOT_FOUND) }

            inventoryEntity.delete()
            true
        }

    override fun search(query: String, limit: Int): List<SearchResult>? =
        dbQuery(SEARCH_TAG) {
            val pattern = "${query.trim().lowercase()}%"
            val contains = "%${query.trim().lowercase()}%"

            InventoryEntity
                .find {
                    (InventoryTable.name.lowerCase() like contains) or
                            (InventoryTable.description.lowerCase() like contains) or
                            (InventoryTable.id.castTo(VarCharColumnType()) like pattern)
                }
                .orderBy(InventoryTable.name.lowerCase() to SortOrder.ASC, InventoryTable.id to SortOrder.ASC)
                .limit(limit)
                .map { inventoryEntity ->
                    SearchResult(
                        identifier = inventoryEntity.id.value,
                        text = when {
                            inventoryEntity.name.startsWith(query, true) -> inventoryEntity.name
                            inventoryEntity.id.value.toString().startsWith(query, true) -> inventoryEntity.id.value.toString()
                            else -> inventoryEntity.name
                        }
                    )
                }
        }

    override fun add(data: Inventory): Boolean =
        dbWrite(ADD_TAG) {
            insertInventory(data)
            true
        }

    // Must be called inside a transaction
    private fun insertInventory(data: Inventory) {
        InventoryEntity.new(data.identifier) {
            name = data.name
            description = data.description
            stock = data.stock
            costCents = data.costCents
            priceCents = data.priceCents
        }
    }

    override fun update(data: Inventory): Boolean =
        dbWrite(UPDATE_TAG) {
            val inventoryEntity = InventoryEntity.findById(data.identifier)
                ?: return@dbWrite false.also { AppLogger.i(UPDATE_TAG, InventoryError.NOT_FOUND) }

            inventoryEntity.apply {
                name = data.name
                description = data.description
                stock = data.stock
                costCents = data.costCents
                priceCents = data.priceCents
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

    /**
     * Takes stock away, or puts it back with a negative [amount]. Items with untracked stock are left alone
     * @return Whether the item was found
     */
    fun adjustStock(identifier: Uuid, amount: Int): Boolean =
        dbWrite(UPDATE_STOCK_TAG) {
            val item = InventoryEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(UPDATE_STOCK_TAG, InventoryError.NOT_FOUND) }

            if (item.stock != Int.MAX_VALUE) item.stock -= amount
            true
        }

    override val noteLinkColumn = InventoryNoteTable.note

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
