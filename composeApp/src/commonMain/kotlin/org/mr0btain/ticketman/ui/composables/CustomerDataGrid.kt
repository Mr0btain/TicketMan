package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.CustomerInfo
import org.mr0btain.ticketman.ui.composables.scrollbar.HorizontalScrollBar
import org.mr0btain.ticketman.ui.composables.scrollbar.VerticalScrollBar
import org.mr0btain.ticketman.ui.screens.main.components.IconText
import org.mr0btain.ticketman.ui.screens.main.components.TableCardTextItem
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.mail
import ticketman.composeapp.generated.resources.navigation
import ticketman.composeapp.generated.resources.phone
import ticketman.composeapp.generated.resources.tag
import ticketman.composeapp.generated.resources.users

@Composable
fun CustomerDataGrid(
    modifier: Modifier = Modifier,
    customerInfo: List<CustomerInfo>,
    scrollBarTop: Boolean,
    scrollBarRight: Boolean,
    isDarkMode: Boolean
) {
    // Column widths
    val nameColumnWidth = remember { mutableStateOf(270f) }
    val addressColumnWidth = remember { mutableStateOf(280f) }
    val contactColumnWidth = remember { mutableStateOf(200f) }
    val ticketColumnWidth = remember { mutableStateOf(120f) }

    // Scroll stuff
    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
    ) {
        HorizontalScrollBar(
            scope = scope,
            scrollState = horizontalScrollState,
            scrollBarOnTop = scrollBarTop
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
            clipToContent = false,
            scrollBarOnRight = scrollBarRight
        ) {
            Column(
                modifier = Modifier.horizontalScroll(horizontalScrollState)
            ) {
                // Almost have to use manual indexing to alternate item colors. If you use forEachIndexed or something of the like
                // it ends up producing wrong values for some reason
                val indexed = remember { mutableIntStateOf(0) }

                repeat(20) {
                    customerInfo.forEach { info ->
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
            .height(42.dp)
            .width(width.value.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier,
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
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
// TODO: Make icon reflect if there is a ticket not completed?

@Composable
private fun TableItem(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    customerInfo: CustomerInfo,
    nameWidth: Float,
    addressWidth: Float,
    contactWidth: Float,
    ticketWidth: Float,
    showEmail: Boolean = true
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
        val sizeAdjust = -4

        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableItemEntry(
                modifier = Modifier.width(nameWidth.dp + sizeAdjust.dp),
                label = customerInfo.name,
                resource = Res.drawable.users
            )

            TableDivider()

            TableItemEntry(
                modifier = Modifier.width(addressWidth.dp + sizeAdjust.dp),
                label = customerInfo.address,
                resource = Res.drawable.navigation
            )

            TableDivider()

            if (showEmail) {
                TableItemEntry(
                    modifier = Modifier.width(contactWidth.dp + sizeAdjust.dp),
                    label = customerInfo.contactInfo.email,
                    resource = Res.drawable.mail
                )
            } else {
                TableItemEntry(
                    modifier = Modifier.width(contactWidth.dp + sizeAdjust.dp),
                    label = customerInfo.contactInfo.phone.first(),
                    resource = Res.drawable.phone
                )
            }

            TableDivider()

            TableItemEntry(
                modifier = Modifier.width(ticketWidth.dp + sizeAdjust.dp),
                label = customerInfo.mostRecentTicket,
                resource = Res.drawable.tag
            )
        }
    }
}

@Composable
private fun TableItemEntry(
    modifier: Modifier = Modifier,
    label: String,
    resource: DrawableResource
) {
    IconText(
        modifier = modifier,
        leadingIcon = {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(resource),
                contentDescription = ""
            )
        },
        label = label,
        textStyle = TextStyle.Default.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
    )
}

@Composable
private fun TableDivider() {
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
}