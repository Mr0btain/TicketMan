package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.data.CustomerInfo
import org.mr0btain.ticketman.ui.composables.scrollbar.HorizontalScrollBar
import org.mr0btain.ticketman.ui.composables.scrollbar.VerticalScrollBar
import org.mr0btain.ticketman.ui.screens.main.components.TableCardTextItem

@Composable
fun TicketDataGrid(
    modifier: Modifier = Modifier
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
                // Almost have to use manual indexing to alternate item colors. If you use forEachIndexed or something of the like
                // it ends up producing wrong values for some reason
                val indexed = remember { mutableIntStateOf(0) }

                repeat(20) {
                    /*
                    customerInfo.forEach{ info ->
                        TableItem(
                            customerInfo = info,
                            color = if (indexed.value % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            nameWidth = nameColumnWidth.value,
                            addressWidth = addressColumnWidth.value,
                            contactWidth = contactColumnWidth.value,
                            ticketWidth = ticketColumnWidth.value
                        )

                        indexed.value++
                    }
                     */
                }
            }
        }
    }
}

@Composable
private fun HeaderBox(
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
            modifier = Modifier,
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
    }

    Row(
        Modifier
            .width(22.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state =
                    rememberDraggableState { delta ->
                        width.value += delta
                    }
            ),
        horizontalArrangement = Arrangement.Center
    ) {
        VerticalDivider(
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// TODO: Make onclick go to customer details screen when its done. or a popup?

@Composable
private fun TableItem(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    customerInfo: CustomerInfo,
    nameWidth: Float,
    addressWidth: Float,
    contactWidth: Float,
    ticketWidth: Float
) {
    Surface(
        modifier = modifier
            .padding(2.dp)
            .clickable(
                onClick = {
                    // Do something
                }
            ),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = color
    ) {
        val sizeAdjust = -8

        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(nameWidth.dp + sizeAdjust.dp)
            ) {
                TableCardTextItem(customerInfo.name)
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 5.dp)
                    .width(addressWidth.dp + sizeAdjust.dp)
            ) {
                TableCardTextItem(customerInfo.address)
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(contactWidth.dp + sizeAdjust.dp)
            ) {
                TableCardTextItem(customerInfo.contactInfo.email)
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(ticketWidth.dp + sizeAdjust.dp)
            ) {
                TableCardTextItem(customerInfo.mostRecentTicket)
            }
        }
    }
}