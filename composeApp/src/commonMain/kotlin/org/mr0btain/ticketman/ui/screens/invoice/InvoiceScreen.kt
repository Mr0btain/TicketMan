package org.mr0btain.ticketman.ui.screens.invoice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import kotlin.uuid.Uuid

/**
 * Every invoice, newest first. Clicking one opens its ticket at the counter, admins can void them
 * @param onOpenTicket Shows the invoice's ticket on the counter screen
 */
@Composable
fun InvoiceScreen(
    viewModel: MainViewModel,
    onOpenTicket: (Uuid) -> Unit
) {
    val counter = viewModel.counter
    val invoices by counter.invoices.collectAsState()
    val endReached by counter.invoicesEndReached.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    var voiding by remember { mutableStateOf<Invoice?>(null) }

    LaunchedEffect(loginState) {
        if (loginState == LoginState.LOGGED_IN) counter.loadInvoices(reset = true)
    }

    voiding?.let { invoice ->
        AlertDialog(
            onDismissRequest = { voiding = null },
            title = { Text("Void ${invoice.label}?") },
            text = {
                Text(
                    if (invoice.status == InvoiceStatus.PAID) "Its parts go back into stock. The ticket stays picked up and the money isn't refunded automatically."
                    else "It can't be used again."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        counter.voidInvoice(invoice)
                        voiding = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Void") }
            },
            dismissButton = { OutlinedButton(onClick = { voiding = null }) { Text("Cancel") } }
        )
    }

    Surface(
        color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(modifier = Modifier.weight(1f), text = "Invoices", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { counter.loadInvoices(reset = true) }) { Text("Refresh") }
            }

            if (invoices.isEmpty()) {
                EmptyMessage(if (loginState == LoginState.LOGGED_IN) "No invoices yet" else "Log in to see invoices")
                return@Column
            }

            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                listOf("Invoice" to 170, "Created" to 150, "Status" to 90, "Total" to 100, "Paid" to 100, "Balance" to 100)
                    .forEach { (label, width) -> Text(modifier = Modifier.width(width.dp), text = label, fontWeight = FontWeight.SemiBold) }
            }

            HorizontalDivider()

            LazyColumn(Modifier.fillMaxSize()) {
                items(invoices, key = { it.identifier }) { invoice ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        onClick = { onOpenTicket(invoice.ticketIdentifier) },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(modifier = Modifier.width(170.dp), text = invoice.label, fontWeight = FontWeight.SemiBold)
                            Text(modifier = Modifier.width(150.dp), text = formatDateTime(invoice.createdAt), fontSize = 13.sp)
                            Text(
                                modifier = Modifier.width(90.dp),
                                text = formatEnumName(invoice.status.name),
                                color = when (invoice.status) {
                                    InvoiceStatus.PAID -> CustomMaterialColors.success
                                    InvoiceStatus.VOID -> MaterialTheme.colorScheme.error
                                    InvoiceStatus.OPEN -> CustomMaterialColors.inProgress
                                }
                            )
                            Text(modifier = Modifier.width(100.dp), text = Money.format(invoice.totalCents))
                            Text(modifier = Modifier.width(100.dp), text = Money.format(invoice.paidCents))
                            Text(modifier = Modifier.width(100.dp), text = Money.format(invoice.balanceCents))

                            if (isAdmin && invoice.status != InvoiceStatus.VOID) {
                                TextButton(onClick = { voiding = invoice }) { Text("Void") }
                            }
                        }
                    }
                }

                item {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        if (endReached) Text("That's all of them", fontSize = 12.sp)
                        else OutlinedButton(onClick = { counter.loadInvoices(reset = false) }) { Text("Load More") }
                    }
                }
            }
        }
    }
}
