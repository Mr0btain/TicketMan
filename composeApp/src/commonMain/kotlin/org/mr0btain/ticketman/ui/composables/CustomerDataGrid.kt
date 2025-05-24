package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.data.CustomerInfo

@Composable
fun HeaderBox(
    modifier: Modifier = Modifier,
    label: String,
    width: MutableState<Float>
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .width(width.value.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
    }

    VerticalDivider(
        Modifier.draggable(
            orientation = Orientation.Horizontal,
            state =
                rememberDraggableState { delta ->
                    width.value += delta
                }
        ),
        thickness = 4.dp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun TableItem(
    customerInfo: CustomerInfo,
    nameWidth: Float,
    addressWidth: Float,
    contactWidth: Float,
    ticketWidth: Float
) {
    Row {
        Text(
            text = customerInfo.name,
            modifier = Modifier.width(nameWidth.dp)
        )

        Text(
            text = customerInfo.address,
            modifier = Modifier.width(addressWidth.dp)
        )

        Text(
            text = customerInfo.contactInfo.email,
            modifier = Modifier.width(contactWidth.dp)
        )

        Text(
            text = customerInfo.mostRecentTicket,
            modifier = Modifier.width(ticketWidth.dp)
        )
    }
}

@Composable
fun CustomerDataGrid(
    modifier: Modifier = Modifier,
    customerInfo: List<CustomerInfo>
) {
    // Column widths
    val nameColumnWidth = remember { mutableStateOf(300f) }
    val addressColumnWidth = remember { mutableStateOf(240f) }
    val contactColumnWidth = remember { mutableStateOf(200f) }
    val ticketColumnWidth = remember { mutableStateOf(180f) }

    // Scroll stuff
    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
    ) {
        HorizontalScrollBar(
            scope = scope,
            scrollState = horizontalScrollState
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderBox(
                    label = "Name",
                    width = nameColumnWidth
                )

                HeaderBox(
                    label = "Address",
                    width = addressColumnWidth
                )

                HeaderBox(
                    label = "Contact",
                    width = contactColumnWidth
                )

                HeaderBox(
                    label = "Last Ticket",
                    width = ticketColumnWidth
                )
            }
        }

        VerticalScrollBar(
            modifier = Modifier
                .fillMaxWidth(),
            scope = scope,
            clipToContent = false
        ) {
            Column(
                modifier = Modifier.horizontalScroll(horizontalScrollState)
            ) {
                repeat(20) {
                    customerInfo.forEach { info ->
                        TableItem(
                            customerInfo = info,
                            nameWidth = nameColumnWidth.value,
                            addressWidth = addressColumnWidth.value,
                            contactWidth = contactColumnWidth.value,
                            ticketWidth = ticketColumnWidth.value
                        )
                    }
                }
            }
        }
    }
}