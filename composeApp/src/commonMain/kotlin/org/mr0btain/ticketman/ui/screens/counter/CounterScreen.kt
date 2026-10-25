package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.StatusLink
import androidx.compose.foundation.layout.*
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import org.mr0btain.ticketman.ui.screens.CheckForUpdates
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.ui.screens.counter.components.CounterView
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.dollar_sign
import ticketman.composeapp.generated.resources.lock
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.shopping_cart

/**
 * The shop counter. One search box for everything, the queue on the side (in a dialog on narrow windows, see
 * [CounterLayout]), and keyboard shortcuts so the common jobs are a key away. Restricted users can look up anything
 * but not change it
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
    val checkInCustomer by counter.checkIn.customer.collectAsState()
    val lastCheckIn by counter.checkIn.last.collectAsState()
    val pickup by counter.pickup.detail.collectAsState()
    val lastPickup by counter.pickup.last.collectAsState()
    val saleCustomer by counter.sale.customer.collectAsState()
    val lastSale by counter.sale.last.collectAsState()

    val canEdit = viewModel.session.canEdit.collectAsState().value
    val isAdmin = viewModel.session.isAdmin.collectAsState().value
    val searchFocus = remember { FocusRequester() }

    // The queue when the counter opens, then changes from other devices on the interval set in Settings. Not in the
    // middle of a check-in or pickup, which have their own state
    LaunchedEffect(loginState) {
        if (loginState == LoginState.LOGGED_IN) counter.refreshQueue()
    }
    val updateInterval by viewModel.settings.updateInterval.collectAsState()
    CheckForUpdates(
        interval = updateInterval,
        enabled = loginState == LoginState.LOGGED_IN && view in setOf(CounterView.HOME, CounterView.TICKET, CounterView.CUSTOMER)
    ) {
        counter.refreshQueue()
        if (view == CounterView.TICKET) counter.reloadTicket(quiet = true)
    }

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
                        counter.checkIn.start()
                        true
                    }

                    Key.F4 -> {
                        counter.openPriceCheck()
                        true
                    }

                    // Picks up whichever ticket is open
                    Key.F5 -> {
                        if (view == CounterView.TICKET) ticket?.let { counter.pickup.start(it.ticket.identifier) }
                        true
                    }

                    Key.F6 -> {
                        counter.sale.start()
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

        CounterLayout(
            searchField = { fieldModifier ->
                TmSearchField(
                    modifier = fieldModifier.focusRequester(searchFocus),
                    query = query,
                    onQueryChange = { counter.setQuery(it) },
                    placeholder = "Search tickets and customers${keyHint("F2")}",
                    loading = searching,
                    size = TmControlSize.LARGE
                )
            },
            canEdit = canEdit,
            queue = queue,
            onCheckIn = { counter.checkIn.start() },
            onNewCustomer = { counter.checkIn.startNewCustomer() },
            onSell = { counter.sale.start() },
            onPriceCheck = { counter.openPriceCheck() },
            onQueueTicketClick = { counter.openTicket(it) }
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
                        statusLink = StatusLink.url(shop.publicUrl, detail.ticket),
                        onReplaceStatusLink = { counter.replaceStatusLink() },
                        estimates = EstimateActions(
                            laborCents = shop.laborRateCents,
                            taxRateBasisPoints = Money.percentToBasisPoints(shop.taxRate) ?: 0,
                            onSearchParts = { counter.pickup.searchParts(it) },
                            onSend = { lines, note, notify, onSent -> counter.estimates.send(detail.ticket.identifier, lines, note, notify, onSent) },
                            onAnswer = { estimate, approved, method, note -> counter.estimates.decide(estimate, approved, method, note) },
                            onWithdraw = { counter.estimates.withdraw(it) }
                        ),
                        onSendStatusLink = StatusLink.url(shop.publicUrl, detail.ticket)?.let { { counter.estimates.message(detail.ticket.identifier) } },
                        printAction = {
                            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                                PrintButton(
                                    size = TmControlSize.LARGE,
                                    templates = listOfNotNull(
                                        PrintTemplate.CLAIM_SLIP,
                                        PrintTemplate.DEVICE_TAG,
                                        PrintTemplate.RECEIPT.takeIf { detail.invoice != null }
                                    ),
                                    defaultPaper = shop.defaultPaper,
                                    onPrint = { template, paper, onResult -> counter.printer.print(detail.ticket.identifier, template, paper, onResult) }
                                )
                                detail.invoice?.let { invoice -> SavePdfButton { counter.printer.savePdf(invoice) } }
                            }
                        },
                        extraActions = {
                            TmButton(
                                text = "Pick up${keyHint("F5")}",
                                onClick = { counter.pickup.start(detail.ticket.identifier) },
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
                        onAdjustCredit = if (isAdmin) { amount, reason, onResult -> counter.adjustCredit(amount, reason, onResult) } else null,
                        actions = {
                            if (canEdit) {
                                TmButton(text = "Check in a device", onClick = { counter.checkIn.start(detail.customer) }, leadingIcon = Res.drawable.plus)
                                TmButton(
                                    text = "Sell",
                                    onClick = { counter.sale.start(detail.customer) },
                                    variant = TmButtonVariant.SECONDARY,
                                    leadingIcon = Res.drawable.shopping_cart
                                )
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
                    onSearchCustomers = { counter.checkIn.searchCustomers(it) },
                    onAddCustomer = { customer, onResult -> counter.checkIn.addCustomer(customer, onResult) },
                    onSearchTechnicians = { counter.checkIn.searchTechnicians(it) },
                    onRepeatCheck = { customer, device, exclude -> counter.checkIn.repeatCheck(customer, device, exclude) },
                    onOpenTicket = { counter.openTicket(it) },
                    onSubmit = { request, onResult -> counter.checkIn.submit(request, onResult) },
                    onCancel = { counter.goHome() }
                )

                CounterView.CHECKED_IN -> lastCheckIn?.let { result ->
                    CheckedInView(
                        result = result,
                        onOpenTicket = { counter.openTicket(result.ticket.identifier) },
                        onCheckInAnother = { counter.checkIn.start() },
                        onDone = { counter.goHome() },
                        actions = {
                            PrintButton(
                                label = "Print claim slip",
                                size = TmControlSize.LARGE,
                                templates = listOf(PrintTemplate.CLAIM_SLIP, PrintTemplate.DEVICE_TAG),
                                defaultPaper = shop.defaultPaper,
                                onPrint = { template, paper, onResult -> counter.printer.print(result.ticket.identifier, template, paper, onResult) }
                            )
                        }
                    )
                }

                CounterView.PICKUP -> pickup?.let { detail ->
                    PickupScreen(
                        detail = detail,
                        onSearchParts = { counter.pickup.searchParts(it) },
                        onSave = { charges, discount, onResult -> counter.pickup.saveCharges(charges, discount, onResult) },
                        onRemovePayment = { counter.pickup.removePayment(it) },
                        onLookupGiftCard = { counter.findGiftCard(it) },
                        onFinish = { charges, discount, payments, onResult -> counter.pickup.finish(charges, discount, payments, onResult) },
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
                                onPrint = { template, paper, onResult -> invoice.ticketIdentifier?.let { counter.printer.print(it, template, paper, onResult) } }
                            )
                            SavePdfButton { counter.printer.savePdf(invoice) }
                        }
                    )
                }

                CounterView.SALE -> SaleScreen(
                    taxRateBasisPoints = Money.percentToBasisPoints(shop.taxRate) ?: 0,
                    laborCents = shop.laborRateCents,
                    startingCustomer = saleCustomer,
                    onSearchParts = { counter.pickup.searchParts(it) },
                    onSearchCustomers = { counter.checkIn.searchCustomers(it) },
                    onLookupGiftCard = { counter.findGiftCard(it) },
                    onFinish = { request, onResult -> counter.sale.finish(request, onResult) },
                    onBack = { counter.goHome() }
                )

                CounterView.SOLD -> lastSale?.let { invoice ->
                    PickedUpView(
                        invoice = invoice,
                        title = "Sold",
                        onDone = { counter.goHome() },
                        actions = {
                            PrintButton(
                                label = "Print receipt",
                                size = TmControlSize.LARGE,
                                templates = listOf(PrintTemplate.RECEIPT),
                                defaultPaper = shop.defaultPaper,
                                onPrint = { _, paper, onResult -> counter.printer.printSale(invoice, paper, onResult) }
                            )
                            SavePdfButton { counter.printer.savePdf(invoice) }
                        }
                    )
                }

                CounterView.NEW_CUSTOMER -> NewCustomerScreen(
                    onSave = { customer, onResult ->
                        counter.checkIn.addCustomer(customer) { saved, problem ->
                            onResult(problem)
                            saved?.let { counter.openCustomer(it.identifier) }
                        }
                    },
                    onCancel = { counter.goHome() }
                )
            }
        }
    }

    // Ready to type as soon as the counter opens, unless it's being touched, where focus would pop up the on screen keyboard
    val keyboard = usingKeyboard()
    LaunchedEffect(loginState) {
        if (loginState == LoginState.LOGGED_IN && keyboard) searchFocus.requestFocus()
    }
}

