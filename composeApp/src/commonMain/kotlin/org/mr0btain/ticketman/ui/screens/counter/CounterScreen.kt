package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.ui.screens.counter.components.CounterView
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

/**
 * The shop counter. One search box for everything, the queue on the side, and keyboard shortcuts so the common
 * jobs are a key away. Restricted users can look up anything but not change it
 */
@Composable
fun CounterScreen(
    viewModel: MainViewModel
) {
    val counter = viewModel.counter
    val loginState by viewModel.loginState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

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

    val canEdit = viewModel.canEdit.collectAsState().value
    val searchFocus = remember { FocusRequester() }

    // Keep the queue fresh while the counter is open
    LaunchedEffect(loginState) {
        if (loginState != LoginState.LOGGED_IN) return@LaunchedEffect

        while (true) {
            counter.refreshQueue()
            delay(QUEUE_REFRESH_MILLIS)
        }
    }

    Surface(
        modifier = Modifier
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
            },
        color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
    ) {
        if (loginState != LoginState.LOGGED_IN) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Log in to use the counter", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.padding(8.dp))
                Button(onClick = { viewModel.changeShowLogin(true) }) { Text("Log In") }
            }
            return@Surface
        }

        Column(Modifier.fillMaxSize()) {
            // ------------------------------------------ Top bar ------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus),
                    value = query,
                    onValueChange = { counter.setQuery(it) },
                    label = { Text("Search tickets and customers (F2)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge
                )

                if (canEdit) {
                    Button(onClick = { counter.startCheckIn() }) { Text("Check In (F3)") }
                    OutlinedButton(onClick = { counter.startNewCustomer() }) { Text("New Customer") }
                }

                OutlinedButton(onClick = { counter.openPriceCheck() }) { Text("Price Check (F4)") }

                if (!canEdit) {
                    Text(text = "View only", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }

                currentUser?.let { user ->
                    Text(text = user.cleanName().ifBlank { user.username }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ------------------------------------------ Main area and queue ------------------------------------------
            Row(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp)
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
                                    Spacer(Modifier.padding(8.dp))
                                    Button(onClick = { counter.startPickup(detail.ticket.identifier) }) { Text("Pick Up (F5)") }
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
                                        Button(onClick = { counter.startCheckIn(detail.customer) }) { Text("Check In a Device") }
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
                                        label = "Print Claim Slip",
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
                                        label = "Print Receipt",
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
                        .width(380.dp)
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
