package org.mr0btain.ticketman.ui.screens.records.components.inventory

import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.asEmptyDataResult
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.components.network.isNotFound
import org.mr0btain.ticketman.components.network.ServerError
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
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.ui.screens.records.components.syncNotes
import kotlin.uuid.Uuid

/**
 * Inventory on the records screen: the list, the item being edited, and saving it with its notes and photos
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 * @param cacheSize How many opened items to keep
 * @param searchLimit Most search results to ask for
 */
@Suppress("PrivatePropertyName")
class InventoryRecords(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean,
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

    /** Opens an item in the seed, from the cache if it's there. [quiet] says nothing when it can't be loaded */
    fun select(identifier: Uuid, quiet: Boolean = false) {
        if (selection.pending.value == identifier) return

        selection.select(identifier) {
            // Notes and photos that failed to load aren't shown as none, saving would then drop them
            val loaded = cache.get(identifier)?.let { TmResult.Success(it) } ?: coroutineScope {
                val notes = async { remoteHandler.inventoryRepo.getNotes(identifier) }
                val images = async { remoteHandler.inventoryRepo.getImages(identifier) }
                val inventory = remoteHandler.inventoryRepo.get(identifier).valueOr { return@coroutineScope TmResult.Error(it) }

                TmResult.Success(
                    InventoryCache.InventoryData(
                        inventory,
                        notes.await().valueOr { return@coroutineScope TmResult.Error(it) },
                        images.await().valueOr { return@coroutineScope TmResult.Error(it) }
                    ).also { cache.put(it.inventory, it.notes, it.images) }
                )
            }

            val (inventory, notes, images) = loaded.valueOr { return@select loadFailed(identifier, it, quiet) }

            return@select { seed.setInitial(inventory, notes, images) }
        }
    }

    /**
     * Loads the open item again from the server, for the periodic check for updates. Leaves a new or changed one
     * alone and says nothing if the server can't be reached; one deleted on another device is still dropped
     */
    fun reloadSelected() {
        if (seed.isNew || seed.isDirty) return

        cache.remove(seed.identifier)
        select(seed.identifier, quiet = true)
    }

    /** Empties the seed for a new item, dropping any item still loading */
    fun clearSelection() {
        selection.cancel()
        seed.clear()
    }

    // Loading the open item found nothing, like reloading it after Cancel when another device deleted it. Stop
    // showing a copy that can't be saved, and reload the list without it
    private fun forgetIfOpen(identifier: Uuid): (() -> Unit)? =
        if (seed.identifier == identifier && !seed.isNew) ({
            seed.clear()
            cache.remove(identifier)
            scope.launch {
                pager.refreshCurrentData()
                snackBarHost.showSnackbar("That item couldn't be loaded, it may have been deleted")
            }
        }) else null

    // Loading failed: a record that's gone is forgotten, anything else is said and what's shown stays
    private fun loadFailed(identifier: Uuid, error: ServerError, quiet: Boolean = false): (() -> Unit)? =
        if (error.isNotFound) forgetIfOpen(identifier)
        else if (quiet) null
        else null.also { scope.launch { snackBarHost.showSnackbar("That item couldn't be loaded: ${error.message}") } }

    /** Saves the seed: the item, its stock count, its notes and its photos */
    fun save() {
        if (!canEdit()) return

        if (!seed.isDirty) return

        val inventory = seed.inventory
        val loadedStock = seed.loadedStock
        val notes = seed.notes.toList()
        val loadedNotes = seed.savedNotes
        val images = seed.images.toList()
        val changed = seed.getChangedFields().toSet()
        val isNew = seed.isNew

        scope.launch {
            val repo = remoteHandler.inventoryRepo
            // Stock is sent on its own, so a pickup since this was opened isn't overwritten
            val separateFields = setOf(ChangedField.NOTE, ChangedField.IMAGE, ChangedField.STOCK)

            if (isNew || (changed - separateFields).isNotEmpty()) {
                val saved = if (isNew) repo.add(inventory).asEmptyDataResult() else repo.update(inventory)

                if (saved is TmResult.Error) {
                    snackBarHost.showSnackbar("Item not saved: ${saved.error.message}")
                    return@launch
                }
            }

            if (!isNew && ChangedField.STOCK in changed) {
                val stock = repo.setStock(inventory.identifier, loadedStock, inventory.stock)
                if (stock is TmResult.Error) snackBarHost.showSnackbar("Stock not saved: ${stock.error.message}")
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(inventory.identifier, notes, loadedNotes, repo, snackBarHost)

            if (isNew || ChangedField.IMAGE in changed) {
                val photos = repo.setImages(inventory.identifier, images)
                if (photos is TmResult.Error) snackBarHost.showSnackbar("Photos not saved: ${photos.error.message}")
            }

            cache.remove(inventory.identifier)
            select(inventory.identifier)
            pager.refreshCurrentData()
        }
    }

    /** Items matching [query], at most the search limit setting */
    /** An item from the cache, or the server if it isn't cached. Null if neither has it */
    suspend fun get(identifier: Uuid): Inventory? =
        cache.get(identifier)?.inventory
            ?: remoteHandler.inventoryRepo.get(identifier).getOrNull()

    suspend fun search(query: String) =
        remoteHandler.inventoryRepo.search(query, searchLimit()).getOrDefault(emptyList())

    /**
     * Deletes the record if it's the one selected. Once the server has, clears the selection and reloads the list;
     * if it refuses, says why and keeps it open. Does nothing for people who can only look
     */
    fun remove(identifier: Uuid) {
        if (!canEdit()) return

        scope.launch {
            if (seed.identifier != identifier) return@launch

            // The selection stays until the server agrees, so a refused delete doesn't look like it worked
            when (val removed = remoteHandler.inventoryRepo.remove(identifier)) {
                is TmResult.Success -> {
                    clearSelection()
                    cache.remove(identifier)
                    pager.refreshCurrentData()
                }

                is TmResult.Error -> snackBarHost.showSnackbar("Item not deleted: ${removed.error.message}")
            }
        }
    }

    /** Drops everything loaded, for when whoever loaded it is gone */
    fun clear() {
        clearSelection()
        pager.clear()
    }
}
