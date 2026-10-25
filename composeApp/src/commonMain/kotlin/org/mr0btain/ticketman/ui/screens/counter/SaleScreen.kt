package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.InvoiceMath
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.shopping_cart
import ticketman.composeapp.generated.resources.user

/**
 * Sells from stock without a ticket: lines, an optional customer, and payment, all sent at once when it's paid
 * @param taxRateBasisPoints The shop's rate now, which the server uses too
 * @param onFinish Sends the sale, calls back with what went wrong or null if it worked
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaleScreen(
    taxRateBasisPoints: Int,
    laborCents: Long,
    startingCustomer: Customer?,
    onSearchParts: suspend (String) -> List<Inventory>,
    onSearchCustomers: suspend (String) -> List<Customer>,
    onFinish: (SaleRequest, (String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val spacing = TmTheme.spacing

    val drafts = remember { mutableStateListOf<ChargeDraft>() }
    var discountCents by remember { mutableLongStateOf(0L) }
    var customer by remember(startingCustomer) { mutableStateOf(startingCustomer) }
    val pending = remember { mutableStateListOf<PaymentRequest>() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val validCharges = drafts.mapNotNull { it.toCharge() }
    val problem = if (drafts.isEmpty()) null else chargesProblem(drafts)
    val totals = InvoiceMath.totals(validCharges, discountCents, taxRateBasisPoints)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmPageHeader(title = "Sale", subtitle = "Selling from stock, no ticket", onBack = onBack)

        BoxWithConstraints {
            val compact = maxWidth < SALE_MIN_WIDTH
            val paymentWidth = min(400.dp, maxWidth)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                Column(
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = SALE_MIN_WIDTH, max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    TmPanel(
                        title = "Items",
                        subtitle = when (drafts.size) {
                            0 -> "Add a part from inventory or another line"
                            1 -> "1 line"
                            else -> "${drafts.size} lines"
                        },
                        icon = Res.drawable.shopping_cart
                    ) {
                        ChargeLines(drafts = drafts, compact = compact, laborCents = laborCents, onSearchParts = onSearchParts)

                        TmMoneyField(
                            modifier = Modifier.width(200.dp),
                            label = "Discount",
                            cents = discountCents,
                            onCentsChange = { discountCents = it }
                        )
                    }

                    SaleCustomer(customer = customer, onSearch = onSearchCustomers, onPick = { customer = it })
                }

                PaymentPanel(
                    modifier = Modifier.width(paymentWidth),
                    totals = totals,
                    taxLabel = "Tax, ${taxRateBasisPoints / 100.0}%",
                    taken = emptyList(),
                    onRemoveTaken = null,
                    pending = pending,
                    problem = message ?: problem,
                    finishText = "Complete sale",
                    finishEnabled = drafts.isNotEmpty() && problem == null,
                    busy = busy,
                    onFinish = {
                        busy = true
                        message = null
                        onFinish(SaleRequest(customer?.identifier, validCharges, discountCents, pending.toList())) { failed ->
                            busy = false
                            message = failed
                        }
                    }
                )
            }
        }
    }
}

/** Narrower than this, sale lines take two rows */
private val SALE_MIN_WIDTH = 560.dp

/** Who's buying, which a sale doesn't need */
@Composable
private fun SaleCustomer(customer: Customer?, onSearch: suspend (String) -> List<Customer>, onPick: (Customer?) -> Unit) {
    if (customer != null) {
        TmPanel(
            title = customer.name,
            subtitle = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(", "),
            icon = Res.drawable.user,
            actions = { TmButton(text = "Remove", onClick = { onPick(null) }, variant = TmButtonVariant.SECONDARY) }
        ) {}
        return
    }

    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Customer>>(emptyList()) }

    LaunchedEffect(query) {
        delay(300)
        matches = if (query.isBlank()) emptyList() else onSearch(query)
    }

    TmPanel(title = "Customer", subtitle = "Optional, to keep the sale on their record", icon = Res.drawable.user) {
        TmSearchField(
            modifier = Modifier.fillMaxWidth(),
            query = query,
            onQueryChange = { query = it },
            placeholder = "Phone number or name"
        )

        if (query.isNotBlank() && matches.isEmpty()) {
            Text(text = "Nobody matches", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
        }

        Column {
            matches.forEach { match ->
                TmListRow(
                    title = match.name,
                    subtitle = listOf(match.phone, match.email).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                    onClick = { onPick(match) }
                )
            }
        }
    }
}
