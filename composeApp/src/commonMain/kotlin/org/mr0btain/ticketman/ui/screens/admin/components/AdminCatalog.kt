package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.catalog.CatalogModel
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.valueOr
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The Repair menu tab: device models and the repairs offered on each at set prices, picked at check-in
 */
@OptIn(ExperimentalUuidApi::class)
class AdminCatalog(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport
) {
    private val catalog get() = remoteHandler.catalogRepo

    private val _menu = MutableStateFlow<List<CatalogModel>?>(null)
    /** The whole menu, null until loaded or when it couldn't be */
    val menu = _menu.asStateFlow()

    suspend fun reload() {
        _menu.value = catalog.all().valueOr { error ->
            support.notify("Couldn't load the repair menu: ${error.message}")
            _menu.value
        }
    }

    /**
     * Adds a model or saves changes to one
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun save(model: CatalogModel, isNew: Boolean, onResult: (String?) -> Unit) {
        support.scope.launch {
            when (val result = if (isNew) catalog.add(model) else catalog.update(model)) {
                is TmResult.Success -> {
                    onResult(null)
                    support.notify(if (isNew) "${result.data.label} added to the repair menu" else "${result.data.label} saved")
                    reload()
                    support.auditChanged()
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    fun remove(model: CatalogModel) {
        support.scope.launch {
            support.report(catalog.remove(model.identifier), "${model.label} taken off the repair menu") { reload() }
        }
    }

    /** Parts for a repair, from inventory */
    suspend fun searchParts(text: String): List<TmSearchResult> =
        remoteHandler.inventoryRepo.search(text.trim(), PART_LIMIT).getOrDefault(emptyList())

    /** Names of the parts [identifiers] are, for showing a model's repairs. Ones that can't be read are left out */
    suspend fun partNames(identifiers: Collection<Uuid>): Map<Uuid, String> =
        identifiers.toSet().mapNotNull { id ->
            (remoteHandler.inventoryRepo.get(id) as? TmResult.Success)?.data?.let { id to it.name }
        }.toMap()

    private companion object {
        const val PART_LIMIT = 8
    }
}
