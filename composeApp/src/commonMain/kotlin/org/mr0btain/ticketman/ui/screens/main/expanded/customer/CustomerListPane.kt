package org.mr0btain.ticketman.ui.screens.main.expanded.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.ui.composables.TableCardTextItem
import org.mr0btain.ticketman.ui.composables.TableDivider
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import java.util.*

@Composable
fun CustomerListPane(
    modifier: Modifier = Modifier,
    customerList: List<Customer>,
    onClick: (UUID) -> Unit,
    selectedCustomerId: UUID?,
    lazyListState: LazyListState,
    scope: CoroutineScope,
    isDarkMode: Boolean
) {
    // Column widths
    val nameColumnWidth = 200.dp
    val addressColumnWidth = 200.dp
    val contactColumnWidth = 240.dp

    Surface(
        modifier = modifier
            .wrapContentSize()
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(16.dp))

                CustomerHeaderBox(
                    label = "Name",
                    width = nameColumnWidth
                )

                Spacer(Modifier.padding(11.dp))

                CustomerHeaderBox(
                    label = "Address",
                    width = addressColumnWidth
                )

                Spacer(Modifier.padding(11.dp))

                CustomerHeaderBox(
                    label = "Contact",
                    width = contactColumnWidth,
                    dividerEnable = false
                )
            }

            HorizontalDivider(modifier = Modifier.width(680.dp))

            PlatformLazyColumn(
                modifier = Modifier
                    .width(700.dp),
                state = lazyListState,
                scope = scope,
                isVisible = customerList.isNotEmpty()
            ) {
                itemsIndexed(
                    items = customerList,
                    key = { _, customer -> customer.identifier }
                ) { index, customer ->
                    CustomerTableItem(
                        customer = customer,
                        selected = customer.identifier == selectedCustomerId,
                        color = if (!isDarkMode) {
                            if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        } else {
                            if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        },
                        nameWidth = nameColumnWidth,
                        addressWidth = addressColumnWidth,
                        contactWidth = contactColumnWidth,
                        onClick = onClick
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerHeaderBox(
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
    onClick: (UUID) -> Unit,
    selected: Boolean = false,
    color: Color = MaterialTheme.colorScheme.surface,
    customer: Customer,
    nameWidth: Dp,
    addressWidth: Dp,
    contactWidth: Dp,
    showEmail: Boolean = true
) {
    Surface(
        modifier = modifier
            .padding(2.dp)
            .clickable(
                onClick = { onClick(customer.identifier) }
            ),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = color,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent)
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
                    customer.name.firstName + " " + customer.name.lastName
                )
            }

            TableDivider()

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(addressWidth - 6.dp)
            ) {
                TableCardTextItem(customer.address.street)
            }

            TableDivider()

            if (showEmail) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(contactWidth - 8.dp)
                ) {
                    TableCardTextItem(customer.contactInfo.email.getOrElse(0) { "" })
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(contactWidth - 6.dp)
                ) {
                    TableCardTextItem(customer.contactInfo.phone.getOrElse(0) { "" })
                }
            }
        }
    }
}