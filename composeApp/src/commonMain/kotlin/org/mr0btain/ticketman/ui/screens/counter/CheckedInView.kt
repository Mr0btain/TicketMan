package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.utils.formatDay
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.server.response.CheckInResponse

/**
 * Shown right after a check in, with the number big enough to read out to the customer
 * @param actions Room for more buttons, like printing the claim slip
 */
@Composable
fun CheckedInView(
    result: CheckInResponse,
    onOpenTicket: () -> Unit,
    onCheckInAnother: () -> Unit,
    onDone: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val ticket = result.ticket

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Checked in", fontSize = 22.sp)
        Text(text = ticket.label, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text(text = ticket.device.toString(), fontSize = 20.sp)

        ticket.promisedAt?.let { Text(modifier = Modifier.padding(top = 8.dp), text = "Promised ${formatDay(it).lowercase()}", fontSize = 16.sp) }
        result.invoice?.let { Text(text = "Deposit taken: ${Money.format(it.paidCents)}", fontSize = 16.sp) }

        Spacer(Modifier.padding(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            actions()
            OutlinedButton(onClick = onOpenTicket) { Text("Open Ticket") }
            OutlinedButton(onClick = onCheckInAnother) { Text("Check In Another") }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}
