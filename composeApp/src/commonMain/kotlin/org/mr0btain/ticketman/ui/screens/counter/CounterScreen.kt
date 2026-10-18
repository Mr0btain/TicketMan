package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.ui.screens.counter.components.CounterView
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.dashboard.components.MainViewModel
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.dollar_sign
import ticketman.composeapp.generated.resources.lock
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.tag
import ticketman.composeapp.generated.resources.user_plus

/**
 * The shop counter. One search box for everything, the queue on the side, and keyboard shortcuts so the common
 * jobs are a key away. Restricted users can look up anything but not change it
 */
@Composable
fun CounterScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val counter = viewModel.counter
    val loginState by viewModel.session.loginState.collectAsState()

    val view by counter.view.collectAsState()
    val query by counter.query.collectAsState()
    val results by counter.results.collectAsState()
    val searching by counter.searching.collectAsState()
    val queue by counter.queue.collectAsState()
    val ticket by counter.ticket.collectAsState()
    val customer by counter.customer.collectAsState()
    val priceResults by counter.priceResults.collectAsState()
    val shop by counter.shop.collectAsState()
    val checkInCustomer by counter.checkInCustomer.collectAsState()
    val lastCheckIn by counter.lastCheckIn.collectAsState()
    val pickup by counter.pickup.collectAsState()
    val lastPickup by counter.lastPickup.collectAsState()

    val canEdit = viewModel.session.canEdit.collectAsState().value
    val searchFocus = remember { FocusRequester() }

    // Keep the queue fresh while the counter is open
    LaunchedEffect(loginState) {
        if (loginState != LoginState.LOGGED_IN) return@LaunchedEffect

        while (true) {
            counter.refreshQueue()
            delay(QUEUE_REFRESH_MILLIS)
        }
    }

    val spacing = TmTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.F2 -> {
                        counter.goHome()
                        searchFocus.requestFocus()
                        true
                    }

                    Key.F3 -> {
                        counter.startCheckIn()
                        true
                    }

                    Key.F4 -> {
                        counter.openPriceCheck()
                        true
                    }

                    // Picks up whichever ticket is open
                    Key.F5 -> {
                        if (view == CounterView.TICKET) ticket?.let { counter.startPickup(it.ticket.identifier) }
                        true
                    }

                    Key.Escape -> {
                        if (view == CounterView.HOME) counter.clearSearch() else counter.goHome()
                        true
                    }

                    else -> false
                }
            }
    ) {
        if (loginState != LoginState.LOGGED_IN) {
            TmEmptyState(
                modifier = Modifier.align(Alignment.Center),
                icon = Res.drawable.lock,
                title = "Log in to use the counter",
                message = "Check-ins, pickups and the queue show up once you're signed in.",
                actionText = "Log in",
                onAction = { viewModel.session.changeShowLogin(true) }
            )
            return@Box
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            // ------------------------------------------ Top bar ------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                TmSearchField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus),
                    query = query,
                    onQueryChange = { counter.setQuery(it) },
                    placeholder = "Search tickets and customers  (F2)",
                    loading = searching,
                    size = TmControlSize.LARGE
                )

                if (canEdit) {
                    TmButton(text = "Check in  (F3)", onClick = { counter.startCheckIn() }, size = TmControlSize.LARGE, leadingIcon = Res.drawable.plus)
                    TmButton(
                        text = "New customer",
                        onClick = { counter.startNewCustomer() },
                        variant = TmButtonVariant.SECONDARY,
                        size = TmControlSize.LARGE,
                        leadingIcon = Res.drawable.user_plus
                    )
                }

                TmButton(
                    text = "Price check  (F4)",
                    onClick = { counter.openPriceCheck() },
                    variant = TmButtonVariant.SECONDARY,
                    size = TmControlSize.LARGE,
                    leadingIcon = Res.drawable.tag
                )

                if (!canEdit) TmBadge(text = "View only", tone = TmTheme.colors.neutral)
            }

            // ------------------------------------------ Main area and queue ------------------------------------------
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (view) {
                        CounterView.HOME -> SearchResults(
                            query = query,
                            results = results,
                            searching = searching,
                            onTicketClick = { counter.openTicket(it) },
                            onCustomerClick = { counter.openCustomer(it) }
                        )

                        CounterView.TICKET -> ticket?.let { detail ->
                            TicketCard(
                                detail = detail,
                                canEdit = canEdit,
                                onStatusChange = { counter.setTicketStatus(it) },
                                onAddNote = { counter.addTicketNote(it) },
                                onCustomerClick = { detail.customer?.let { counter.openCustomer(it.identifier) } },
                                onBack = { counter.goHome() },
                                printAction = {
                                    PrintButton(
                                        size = TmControlSize.LARGE,
                                        templates = listOfNotNull(
                                            PrintTemplate.CLAIM_SLIP,
                                            PrintTemplate.DEVICE_TAG,
                                            PrintTemplate.RECEIPT.takeIf { detail.invoice != null }
                                        ),
                                        defaultPaper = shop.defaultPaper,
                                        onPrint = { template, paper, onResult -> counter.print(detail.ticket.identifier, template, paper, onResult) }
                                    )
                                },
                                extraActions = {
                                    TmButton(
                                        text = "Pick up  (F5)",
                                        onClick = { counter.startPickup(detail.ticket.identifier) },
                                        size = TmControlSize.LARGE,
                                        leadingIcon = Res.drawable.dollar_sign
                                    )
                                }
                            )
                        }

                        CounterView.CUSTOMER -> customer?.let { detail ->
                            CustomerCard(
                                detail = detail,
                                onTicketClick = { counter.openTicket(it) },
                                onBack = { counter.goHome() },
                                actions = {
                                    if (canEdit) {
                                        TmButton(text = "Check in a device", onClick = { counter.startCheckIn(detail.customer) }, leadingIcon = Res.drawable.plus)
                                    }
                                }
                            )
                        }

                        CounterView.PRICE_CHECK -> PriceCheck(
                            results = priceResults,
                            onSearch = { counter.searchPrices(it) },
                            onBack = { counter.goHome() }
                        )

                        CounterView.CHECK_IN -> CheckInWizard(
                            startingCustomer = checkInCustomer,
                            terms = shop.repairTerms,
                            onSearchCustomers = { counter.searchCustomers(it) },
                            onAddCustomer = { customer, onResult -> counter.addCustomer(customer, onResult) },
                            onSearchTechnicians = { counter.searchTechnicians(it) },
                            onRepeatCheck = { customer, device, exclude -> counter.repeatCheck(customer, device, exclude) },
                            onOpenTicket = { counter.openTicket(it) },
                            onSubmit = { request, onResult -> counter.submitCheckIn(request, onResult) },
                            onCancel = { counter.goHome() }
                        )

                        CounterView.CHECKED_IN -> lastCheckIn?.let { result ->
                            CheckedInView(
                                result = result,
                                onOpenTicket = { counter.openTicket(result.ticket.identifier) },
                                onCheckInAnother = { counter.startCheckIn() },
                                onDone = { counter.goHome() },
                                actions = {
                                    PrintButton(
                                        label = "Print claim slip",
                                        size = TmControlSize.LARGE,
                                        templates = listOf(PrintTemplate.CLAIM_SLIP, PrintTemplate.DEVICE_TAG),
                                        defaultPaper = shop.defaultPaper,
                                        onPrint = { template, paper, onResult -> counter.print(result.ticket.identifier, template, paper, onResult) }
                                    )
                                }
                            )
                        }

                        CounterView.PICKUP -> pickup?.let { detail ->
                            PickupScreen(
                                detail = detail,
                                onSearchParts = { counter.searchParts(it) },
                                onSave = { charges, discount, onResult -> counter.saveCharges(charges, discount, onResult) },
                                onRemovePayment = { counter.removePayment(it) },
                                onFinish = { charges, discount, payments, onResult -> counter.finishPickup(charges, discount, payments, onResult) },
                                onBack = { counter.openTicket(detail.ticket.identifier) }
                            )
                        }

                        CounterView.PICKED_UP -> lastPickup?.let { invoice ->
                            PickedUpView(
                                invoice = invoice,
                                onDone = { counter.goHome() },
                                actions = {
                                    PrintButton(
                                        label = "Print receipt",
                                        size = TmControlSize.LARGE,
                                        templates = listOf(PrintTemplate.RECEIPT),
                                        defaultPaper = shop.defaultPaper,
                                        onPrint = { template, paper, onResult -> counter.print(invoice.ticketIdentifier, template, paper, onResult) }
                                    )
                                }
                            )
                        }

                        CounterView.NEW_CUSTOMER -> NewCustomerScreen(
                            onSave = { customer, onResult ->
                                counter.addCustomer(customer) { saved, problem ->
                                    onResult(problem)
                                    saved?.let { counter.openCustomer(it.identifier) }
                                }
                            },
                            onCancel = { counter.goHome() }
                        )
                    }
                }

                QueuePanel(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                    queue = queue,
                    onTicketClick = { counter.openTicket(it) }
                )
            }
        }
    }

    // Ready to type as soon as the counter opens
    LaunchedEffect(loginState) {
        if (loginState == LoginState.LOGGED_IN) searchFocus.requestFocus()
    }
}

private const val QUEUE_REFRESH_MILLIS = 60_000L
