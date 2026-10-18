package org.mr0btain.ticketman.ui.screens.dashboard.components.inventory

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.RecordSelection
import org.mr0btain.ticketman.components.data.inventory.InventoryCache
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.ui.screens.dashboard.components.syncNotes
import kotlin.uuid.Uuid

/**
 * Inventory on the dashboard: the list, the item being edited, and saving it with its notes and photos
 * @param cacheSize How many opened items to keep
 * @param searchLimit Most search results to ask for
 */
@Suppress("PrivatePropertyName")
class InventoryRecords(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    pagerConfig: OffsetPagerConfig,
    cacheSize: Int,
    private val searchLimit: () -> Int
) {
    private val PAGER_TAG = "InventoryPager"

    val cache = InventoryCache(cacheSize)

    val pager = OffsetPager<Inventory>(
        tag = PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            remoteHandler.inventoryRepo.fetch(
                offset = (page * pageSize).toLong(),
                limit = pageSize
            )
        }
    )

    val seed = InventorySeed()

    /** Item being loaded after a click, the list highlights it until it's in the seed */
    val selection = RecordSelection(scope)

    /** Opens an item in the seed, from the cache if it's there */
    fun select(identifier: Uuid) {
        if (selection.pending.value == identifier) return

        selection.select(identifier) {
            val (inventory, notes, images) = cache.get(identifier) ?: coroutineScope {
                val notes = async { remoteHandler.inventoryRepo.getNotes(identifier) }
                val images = async { remoteHandler.inventoryRepo.getImages(identifier) }
                val inventory = remoteHandler.inventoryRepo.get(identifier) ?: return@coroutineScope null

                InventoryCache.InventoryData(inventory, notes.await(), images.await())
                    .also { cache.put(it.inventory, it.notes, it.images) }
            } ?: return@select null

            return@select { seed.setInitial(inventory, notes, images) }
        }
    }

    /** Empties the seed for a new item, dropping any item still loading */
    fun clearSelection() {
        selection.cancel()
        seed.clear()
    }

    /** Saves the seed: the item, its notes and its photos */
    fun save() {
        if (!seed.isDirty) return

        val inventory = seed.inventory
        val notes = seed.notes.toList()
        val images = seed.images.toList()
        val changed = seed.getChangedFields().toSet()
        val isNew = seed.isNew

        scope.launch {
            val repo = remoteHandler.inventoryRepo
            val linkFields = setOf(ChangedField.NOTE, ChangedField.IMAGE)

            if (isNew || (changed - linkFields).isNotEmpty()) {
                val saved = if (isNew) repo.add(inventory) else repo.update(inventory)

                if (saved is Result.Error) {
                    snackBarHost.showSnackbar("Item not saved: ${saved.error.message}")
                    return@launch
                }
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(inventory.identifier, notes, repo, snackBarHost)

            if (isNew || ChangedField.IMAGE in changed) {
                val photos = repo.setImages(inventory.identifier, images)
                if (photos is Result.Error) snackBarHost.showSnackbar("Photos not saved: ${photos.error.message}")
            }

            cache.remove(inventory.identifier)
            select(inventory.identifier)
            pager.refreshCurrentData()
        }
    }

    suspend fun search(query: String) =
        remoteHandler.inventoryRepo.search(query, searchLimit())

    fun remove(identifier: Uuid) {
        scope.launch {
            if (seed.identifier == identifier) {
                clearSelection()
                remoteHandler.inventoryRepo.remove(identifier)
                pager.refreshCurrentData()
            }
        }
    }

    /** Drops everything loaded, for when whoever loaded it is gone */
    fun clear() {
        clearSelection()
        pager.clear()
    }
}
