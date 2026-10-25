package org.mr0btain.ticketman.ui.screens.purchasing.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.purchase.PurchaseLine
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.server.request.OrderPartRequest
import org.mr0btain.ticketman.server.request.PurchaseOrderRequest
import org.mr0btain.ticketman.server.request.ReceiveRequest
import org.mr0btain.ticketman.server.request.ReceivedLine
import kotlin.uuid.Uuid

/** Which orders the list shows */
enum class OrderFilter(val label: String, val statuses: List<PurchaseStatus>?) {
    WAITING("Waiting", listOf(PurchaseStatus.ORDERED, PurchaseStatus.PARTLY_RECEIVED)),
    DRAFTS("Drafts", listOf(PurchaseStatus.DRAFT)),
    ALL("All", null)
}

/**
 * State and actions for the purchasing screen: suppliers, purchase orders and receiving them. Owned by the main
 * view model, which also uses it to order parts from a ticket and draft orders for low stock
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 */
class PurchasingHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean
) {
    private val repo get() = remoteHandler.purchaseRepo

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    /** By name */
    val suppliers = _suppliers.asStateFlow()

    private val _filter = MutableStateFlow(OrderFilter.WAITING)
    val filter = _filter.asStateFlow()

    private val _orders = MutableStateFlow<List<PurchaseOrder>>(emptyList())
    /** Newest first, matching [filter] */
    val orders = _orders.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    private val _selected = MutableStateFlow<PurchaseOrder?>(null)
    val selected = _selected.asStateFlow()

    /** Loads the suppliers and the orders */
    fun refresh() {
        scope.launch {
            repo.suppliers().getOrNull()?.let { _suppliers.value = it }
            loadOrders()
        }
    }

    fun setFilter(filter: OrderFilter) {
        _filter.value = filter
        scope.launch { loadOrders() }
    }

    fun select(order: PurchaseOrder?) {
        _selected.value = order
    }

    private suspend fun loadOrders() {
        val page = repo.orders(0, PAGE_SIZE, _filter.value.statuses).valueOr { error ->
            _loadError.value = "Couldn't load orders: ${error.message}"
            return
        }
        _loadError.value = null
        _orders.value = page
        // Keep showing the open one as it is now
        _selected.value = _selected.value?.let { open -> page.firstOrNull { it.identifier == open.identifier } ?: open }
    }

    // Shows what came back and puts it in the list
    private suspend fun changed(order: PurchaseOrder) {
        _selected.value = order
        loadOrders()
    }

    // ------------------------------------------ Orders ------------------------------------------

    /** Starts an empty draft and opens it */
    fun newDraft(supplier: Uuid? = null) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.add(PurchaseOrderRequest(supplierIdentifier = supplier))) {
                is TmResult.Success -> {
                    _filter.value = OrderFilter.DRAFTS
                    changed(result.data)
                }
                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    /**
     * Saves a draft's supplier, lines and note
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun saveDraft(order: PurchaseOrder, request: PurchaseOrderRequest, onResult: (String?) -> Unit) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.update(order.identifier, request)) {
                is TmResult.Success -> {
                    changed(result.data)
                    onResult(null)
                }
                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Sends the draft to the supplier, saving [request] first when it has changes */
    fun place(order: PurchaseOrder, request: PurchaseOrderRequest?, onResult: (String?) -> Unit) {
        if (!canEdit()) return
        scope.launch {
            if (request != null) {
                repo.update(order.identifier, request).valueOr { error ->
                    onResult(error.message)
                    return@launch
                }
            }

            when (val result = repo.place(order.identifier)) {
                is TmResult.Success -> {
                    notify("${order.label} placed with ${result.data.supplierName}")
                    _filter.value = OrderFilter.WAITING
                    changed(result.data)
                    onResult(null)
                }
                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /**
     * Takes in what arrived, a count for each line
     * @param onResult Gets null when it's done, otherwise what went wrong
     */
    fun receive(order: PurchaseOrder, counts: Map<Uuid, Int>, onResult: (String?) -> Unit) {
        if (!canEdit()) return
        scope.launch {
            val request = ReceiveRequest(counts.filterValues { it > 0 }.map { (line, quantity) -> ReceivedLine(line, quantity) })
            when (val result = repo.receive(order.identifier, request)) {
                is TmResult.Success -> {
                    notify(if (result.data.status == PurchaseStatus.RECEIVED) "${order.label} is all in" else "Received on ${order.label}")
                    changed(result.data)
                    onResult(null)
                }
                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    fun cancel(order: PurchaseOrder) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.cancel(order.identifier)) {
                is TmResult.Success -> changed(result.data)
                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    /** Deletes a draft */
    fun remove(order: PurchaseOrder) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.remove(order.identifier)) {
                is TmResult.Success -> {
                    _selected.value = null
                    loadOrders()
                }
                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    /** Items for an order's lines */
    suspend fun searchParts(text: String): List<Inventory> {
        if (text.isBlank()) return emptyList()
        val inventory = remoteHandler.inventoryRepo
        return inventory.search(text.trim(), SEARCH_LIMIT).getOrDefault(emptyList()).mapNotNull { inventory.get(it.identifier).getOrNull() }
    }

    /**
     * Drafts orders for running low items, one per supplier, each ordering enough to get back to twice its reorder
     * point. Items with no supplier go on a draft with none, to pick one
     * @param threshold The reorder point for items without their own
     */
    fun draftForLowStock(items: List<Inventory>, threshold: Int, onResult: (String?) -> Unit) {
        if (!canEdit()) return
        scope.launch {
            val drafts = items.filter { it.tracksStock }.groupBy { it.supplierIdentifier }.map { (supplier, group) ->
                val lines = group.map { item ->
                    val point = item.reorderPoint ?: threshold
                    PurchaseLine(inventoryIdentifier = item.identifier, description = item.name, quantity = (point * 2 - item.stock).coerceAtLeast(1), unitCostCents = item.costCents)
                }
                repo.add(PurchaseOrderRequest(supplierIdentifier = supplier, lines = lines)).valueOr { error ->
                    notify("Couldn't draft the orders: ${error.message}")
                    onResult(error.message)
                    return@launch
                }
            }

            notify(if (drafts.size == 1) "Drafted ${drafts.single().label}, see Purchasing" else "Drafted ${drafts.size} orders, see Purchasing")
            _filter.value = OrderFilter.DRAFTS
            loadOrders()
            onResult(null)
        }
    }

    /**
     * Orders a part for a ticket, onto the supplier's draft
     * @param onResult Gets null when it's on an order, otherwise what went wrong
     */
    fun orderPart(ticket: Uuid, item: Inventory, quantity: Int, supplier: Uuid?, onResult: (String?) -> Unit) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.orderPart(OrderPartRequest(ticket, item.identifier, quantity, supplier))) {
                is TmResult.Success -> {
                    notify("$quantity x ${item.name} is on ${result.data.label}, place it from Purchasing")
                    loadOrders()
                    onResult(null)
                }
                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    // ------------------------------------------ Suppliers ------------------------------------------

    /**
     * Adds a supplier, or saves one that exists
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun saveSupplier(supplier: Supplier, isNew: Boolean, onResult: (String?) -> Unit) {
        if (!canEdit()) return
        scope.launch {
            val result = if (isNew) repo.addSupplier(supplier) else repo.updateSupplier(supplier)
            when (result) {
                is TmResult.Success -> {
                    repo.suppliers().getOrNull()?.let { _suppliers.value = it }
                    onResult(null)
                }
                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    fun removeSupplier(supplier: Supplier) {
        if (!canEdit()) return
        scope.launch {
            when (val result = repo.removeSupplier(supplier.identifier)) {
                is TmResult.Success -> _suppliers.value = _suppliers.value.filter { it.identifier != supplier.identifier }
                is TmResult.Error -> notify(result.error.message)
            }
        }
    }

    /** Clears everything, on log out */
    fun clear() {
        _suppliers.value = emptyList()
        _orders.value = emptyList()
        _selected.value = null
        _loadError.value = null
        _filter.value = OrderFilter.WAITING
    }

    // Snackbars suspend until dismissed, so show them on the side
    private fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val SEARCH_LIMIT = 20
    }
}
