package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import org.mr0btain.ticketman.ui.composables.scrollbar.VerticalScrollBar
import org.mr0btain.ticketman.ui.screens.main.components.IconText
import org.mr0btain.ticketman.ui.screens.main.components.TableCardTextItem
import ticketman.composeapp.generated.resources.*

@Composable
fun CustomerDataGrid(
    modifier: Modifier = Modifier,
    customerInfo: List<CustomerInfo>,
    onClick: (id: String) -> Unit,
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
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
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

            LazyColumn {
                itemsIndexed(customerInfo.sortedBy { it.name.firstName + it.name.lastName }) { index, info ->
                    CustomerTableItem(
                        customerInfo = info,
                        color = if (!isDarkMode) {
                            if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        } else {
                            if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        },
                        nameWidth = nameColumnWidth,
                        addressWidth = addressColumnWidth,
                        contactWidth = contactColumnWidth,
                        ticketWidth = ticketColumnWidth,
                        onClick = onClick
                    )

                    Spacer(Modifier.padding(2.dp))
                }
            }

            /*
            VerticalScrollBar(
                modifier = Modifier.wrapContentSize(),
                scope = scope,
                clipToContent = true,
                scrollBarOnRight = scrollBarRight
            ) {

            }
             */
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
            VerticalDivider(Modifier.height(42.dp), thickness = 3.dp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

// TODO: Make onclick go to customer details screen when its done. or a popup?
// TODO: Make icon reflect if there is a ticket not completed?

@Composable
private fun CustomerTableItem(
    modifier: Modifier = Modifier,
    onClick: (id: String) -> Unit,
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
                onClick = { onClick(customerInfo.id) }
            ),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = color
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(nameWidth - 4.dp)
            ) {
                TableCardTextItem(
                    customerInfo.name.firstName + " " + customerInfo.name.lastName
                )
            }

            TableDivider()

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(addressWidth - 6.dp)
            ) {
                TableCardTextItem(customerInfo.address.street)
            }

            TableDivider()

            if (showEmail) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(contactWidth - 8.dp)
                ) {
                    TableCardTextItem(customerInfo.contactInfo.email)
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(contactWidth - 6.dp)
                ) {
                    TableCardTextItem(customerInfo.contactInfo.phone.first())
                }
            }

            TableDivider()

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(ticketWidth - 8.dp)
            ) {
                TableCardTextItem(customerInfo.mostRecentTicket)
            }
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
            color = MaterialTheme.colorScheme.secondary,
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
            color = MaterialTheme.colorScheme.secondary,
            thickness = 2.dp
        )
    }
}