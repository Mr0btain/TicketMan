package org.mr0btain.ticketman.ui.screens.purchasing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDate
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmTabs
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmColors
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.purchasing.components.OrderFilter
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.truck

/** Narrower than this, the order list and the open order take turns */
private val SIDE_BY_SIDE_WIDTH = 900.dp

/**
 * Ordering parts: purchase orders, drafted, placed with a supplier and received into stock, and the suppliers
 * themselves. Restricted users can look but not change anything
 */
@Composable
fun PurchasingScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val purchasing = viewModel.purchasing
    val loginState by viewModel.session.loginState.collectAsState()
    val canEdit = viewModel.session.canEdit.collectAsState().value
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(loginState) {
        if (loginState == LoginState.LOGGED_IN) purchasing.refresh()
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
        TmPageHeader(title = "Purchasing", onBack = null, subtitle = "Orders to suppliers, and taking them into stock")
        TmTabs(tabs = listOf("Orders", "Suppliers"), selectedIndex = tab, onSelect = { tab = it })

        when (tab) {
            0 -> OrdersPane(viewModel, canEdit)
            else -> SuppliersPane(
                suppliers = purchasing.suppliers.collectAsState().value,
                canEdit = canEdit,
                onSave = { supplier, isNew, onResult -> purchasing.saveSupplier(supplier, isNew, onResult) },
                onRemove = { purchasing.removeSupplier(it) }
            )
        }
    }
}

@Composable
private fun OrdersPane(viewModel: MainViewModel, canEdit: Boolean) {
    val purchasing = viewModel.purchasing
    val orders by purchasing.orders.collectAsState()
    val selected by purchasing.selected.collectAsState()
    val filter by purchasing.filter.collectAsState()
    val loadError by purchasing.loadError.collectAsState()
    val suppliers by purchasing.suppliers.collectAsState()
    val spacing = TmTheme.spacing

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= SIDE_BY_SIDE_WIDTH

        val list: @Composable (Modifier) -> Unit = { listModifier ->
            OrderList(
                modifier = listModifier,
                orders = orders,
                selected = selected?.identifier,
                filter = filter,
                loadError = loadError,
                canEdit = canEdit,
                onFilter = { purchasing.setFilter(it) },
                onNew = { purchasing.newDraft() },
                onOpen = { purchasing.select(it) }
            )
        }

        val detail: @Composable (PurchaseOrder) -> Unit = { order ->
            OrderDetail(
                order = order,
                suppliers = suppliers,
                canEdit = canEdit,
                onSearchParts = { purchasing.searchParts(it) },
                onSave = { request, onResult -> purchasing.saveDraft(order, request, onResult) },
                onPlace = { request, onResult -> purchasing.place(order, request, onResult) },
                onReceive = { counts, onResult -> purchasing.receive(order, counts, onResult) },
                onCancel = { purchasing.cancel(order) },
                onDelete = { purchasing.remove(order) },
                onClose = if (sideBySide) null else ({ purchasing.select(null) })
            )
        }

        if (sideBySide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                list(Modifier.width(380.dp).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    selected?.let { detail(it) } ?: TmEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Res.drawable.truck,
                        title = "No order open",
                        message = "Pick one from the list, or start a new one."
                    )
                }
            }
        } else {
            selected?.let { detail(it) } ?: list(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun OrderList(
    modifier: Modifier,
    orders: List<PurchaseOrder>,
    selected: kotlin.uuid.Uuid?,
    filter: OrderFilter,
    loadError: String?,
    canEdit: Boolean,
    onFilter: (OrderFilter) -> Unit,
    onNew: () -> Unit,
    onOpen: (PurchaseOrder) -> Unit
) {
    TmPanel(
        modifier = modifier,
        title = "Orders",
        icon = Res.drawable.truck,
        actions = { if (canEdit) TmButton(text = "New order", onClick = onNew, leadingIcon = Res.drawable.plus) }
    ) {
        TmSegmented(options = OrderFilter.entries, selected = filter, onSelect = onFilter, optionLabel = { it.label })

        loadError?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        if (orders.isEmpty() && loadError == null) {
            TmEmptyState(
                icon = Res.drawable.truck,
                title = when (filter) {
                    OrderFilter.WAITING -> "Nothing on order"
                    OrderFilter.DRAFTS -> "No drafts"
                    OrderFilter.ALL -> "No orders yet"
                },
                message = "Start one with New order, from low stock on the admin screen, or with Order a part on a ticket."
            )
            return@TmPanel
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            orders.forEach { order ->
                TmListRow(
                    title = order.label,
                    subtitle = listOf(
                        order.supplierName.ifBlank { "No supplier yet" },
                        "${order.lines.size} line${if (order.lines.size == 1) "" else "s"}",
                        formatDate(order.orderedAt ?: order.createdAt)
                    ).joinToString(", "),
                    selected = order.identifier == selected,
                    trailing = {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            PurchaseStatusBadge(order.status)
                            TmMoneyText(order.totalCents)
                        }
                    },
                    onClick = { onOpen(order) }
                )
            }
        }
    }
}

/** An order's status in its tone */
@Composable
fun PurchaseStatusBadge(status: PurchaseStatus) {
    TmBadge(text = status.label, tone = TmTheme.colors.toneOf(status))
}

private fun TmColors.toneOf(status: PurchaseStatus): TmTone = when (status) {
    PurchaseStatus.DRAFT -> neutral
    PurchaseStatus.ORDERED -> accent
    PurchaseStatus.PARTLY_RECEIVED -> warning
    PurchaseStatus.RECEIVED -> success
    PurchaseStatus.CANCELLED -> danger
}

/** Small print under a heading */
@Composable
internal fun Caption(text: String) {
    Text(text = text, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
}
