package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
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
    val nameColumnWidth = 200.dp
    val addressColumnWidth = 200.dp
    val contactColumnWidth = 200.dp
    val ticketColumnWidth = 120.dp

    // Scroll stuff
    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .wrapContentSize()
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 10.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(10.dp))

                HeaderBox(
                    label = "Name",
                    width = nameColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                HeaderBox(
                    label = "Address",
                    width = addressColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                HeaderBox(
                    label = "Contact",
                    width = contactColumnWidth
                )

                Spacer(Modifier.padding(10.dp))

                HeaderBox(
                    label = "Last Ticket",
                    width = ticketColumnWidth,
                    dividerEnable = false
                )
            }

            HorizontalDivider(modifier = Modifier.width(746.dp))

            VerticalScrollBar(
                modifier = Modifier.wrapContentSize(),
                scope = scope,
                clipToContent = true,
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
                                nameWidth = nameColumnWidth,
                                addressWidth = addressColumnWidth,
                                contactWidth = contactColumnWidth,
                                ticketWidth = ticketColumnWidth
                            )

                            Spacer(Modifier.padding(2.dp))

                            indexed.value++
                        }
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
    width: Dp,
    dividerEnable: Boolean = true
) {
    Row(
        modifier.width(width),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        if (dividerEnable) {
            VerticalDivider(Modifier.height(42.dp), thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// TODO: Make onclick go to customer details screen when its done. or a popup?
// TODO: Make icon reflect if there is a ticket not completed?

@Composable
private fun TableItem(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    customerInfo: CustomerInfo,
    nameWidth: Dp,
    addressWidth: Dp,
    contactWidth: Dp,
    ticketWidth: Dp,
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
                modifier = Modifier.width(nameWidth + sizeAdjust.dp),
                label = customerInfo.name,
                resource = Res.drawable.users
            )

            TableDivider()

            TableItemEntry(
                modifier = Modifier.width(addressWidth + sizeAdjust.dp),
                label = customerInfo.address,
                resource = Res.drawable.navigation
            )

            TableDivider()

            if (showEmail) {
                TableItemEntry(
                    modifier = Modifier.width(contactWidth + sizeAdjust.dp),
                    label = customerInfo.contactInfo.email,
                    resource = Res.drawable.mail
                )
            } else {
                TableItemEntry(
                    modifier = Modifier.width(contactWidth + sizeAdjust.dp),
                    label = customerInfo.contactInfo.phone.first(),
                    resource = Res.drawable.phone
                )
            }

            TableDivider()

            TableItemEntry(
                modifier = Modifier.width(ticketWidth + sizeAdjust.dp),
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
                modifier = Modifier.size(16.dp),
                painter = painterResource(resource),
                contentDescription = ""
            )
        },
        label = label,
        textStyle = TextStyle.Default.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    )
}

@Composable
private fun SimpleTableItemEntry(
    label: String,
    width: Dp
) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .width(width)
    ) {
        TableCardTextItem(label)
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