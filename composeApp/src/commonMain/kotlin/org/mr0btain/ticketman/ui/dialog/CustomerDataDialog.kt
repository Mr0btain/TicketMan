package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.Address
import org.mr0btain.ticketman.components.data.components.ContactInfo
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.CustomerInfo
import org.mr0btain.ticketman.components.data.components.CustomerName
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit_2
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x

@Composable
fun CustomerDataDialog(
    modifier: Modifier = Modifier,
    customerData: CustomerData,
    onDismiss: (CustomerData) -> Unit,
    isDarkMode: Boolean
) {
    val identifier = customerData.info.id
    val firstName = remember { mutableStateOf(customerData.info.name.firstName) }
    val lastName = remember { mutableStateOf(customerData.info.name.lastName) }
    val street = remember { mutableStateOf(customerData.info.address.street) }
    val city = remember { mutableStateOf(customerData.info.address.city) }
    val state = remember { mutableStateOf(customerData.info.address.state) }
    val zip = remember { mutableStateOf(customerData.info.address.zip) }
    val email = remember { customerData.info.contactInfo.email.toMutableList() }
    val phone = remember { customerData.info.contactInfo.phone.toMutableList() }
    val tickets = remember { customerData.tickets.toMutableList() }
    val notes = remember { customerData.notes.toMutableList() }

    // TODO: Flesh out a better way of updating most recent ticket
    Dialog(
        onDismissRequest = {
            onDismiss(
                CustomerData(
                    info = CustomerInfo(
                        id =identifier,
                        name = CustomerName(firstName.value, lastName.value),
                        address = Address(street.value, city.value, state.value, zip.value),
                        contactInfo = ContactInfo(email, phone)
                    ),
                    tickets = tickets,
                    notes = notes
                )
            )
        }
    ) {
        Surface(
            modifier = modifier
                .height(600.dp)
                .width(600.dp),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = "Edit Customer: $identifier",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(4.dp),
                    color = if (isDarkMode) MaterialTheme.colorScheme.onSurface else DividerDefaults.color
                )

                Spacer(Modifier.padding(8.dp))

                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    item {
                        // Name section
                        Text(
                            text = "Name",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        CustomerDialogTextRow(
                            modifier = Modifier,
                            firstLabel = "First",
                            firstValue = firstName,
                            secondLabel = "Last",
                            secondValue = lastName
                        )

                        Spacer(Modifier.padding(8.dp))

                        // Address section
                        Text(
                            text = "Address",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CustomerDialogTextRow(
                                modifier = Modifier,
                                firstLabel = "Street",
                                firstValue = street,
                                secondLabel = "City",
                                secondValue = city
                            )

                            CustomerDialogTextRow(
                                modifier = Modifier,
                                firstLabel = "State",
                                firstValue = state,
                                secondLabel = "Zip",
                                secondValue = zip
                            )
                        }

                        Spacer(Modifier.padding(8.dp))

                        Text(
                            text = "Contact Info",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Email",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            IconButton(
                                modifier = Modifier.size(26.dp),
                                onClick = {}
                            ) {
                                Icon(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(20.dp),
                                    painter = painterResource(Res.drawable.plus),
                                    contentDescription = "Add Email Address"
                                )
                            }
                        }

                        // Email list
                        CustomerEmailListRow(
                            modifier = Modifier,
                            items = email
                        )

                        Spacer(Modifier.padding(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Phone",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.padding(4.dp))

                            IconButton(
                                modifier = Modifier.size(26.dp),
                                onClick = {}
                            ) {
                                Icon(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(20.dp),
                                    painter = painterResource(Res.drawable.plus),
                                    contentDescription = "Add Phone Number"
                                )
                            }
                        }

                        // Phone List
                        CustomerPhoneListRow(
                            modifier = Modifier,
                            items = phone
                        )

                        Text(
                            text = "Tickets",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        Row {

                        }

                        Spacer(Modifier.padding(4.dp))

                        Text(
                            text = "Notes",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.padding(4.dp))

                        Row {

                        }

                        Spacer(Modifier.padding(4.dp))
                    }
                }

                Spacer(Modifier.padding(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            onDismiss(
                                CustomerData(
                                    info = CustomerInfo(
                                        id =identifier,
                                        name = CustomerName(firstName.value, lastName.value),
                                        address = Address(street.value, city.value, state.value, zip.value),
                                        contactInfo = ContactInfo(email, phone)
                                    ),
                                    tickets = tickets,
                                    notes = notes
                                )
                            )
                        }
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDialogTextRow(
    modifier: Modifier = Modifier,
    firstLabel: String,
    firstValue: MutableState<String>,
    secondLabel: String,
    secondValue: MutableState<String>
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(0.5f),
            label = {
                Text(firstLabel)
            },
            value = firstValue.value,
            onValueChange = { firstValue.value = it },
            singleLine = true
        )

        Spacer(Modifier.padding(8.dp))

        OutlinedTextField(
            modifier = Modifier.weight(0.5f),
            label = {
                Text(secondLabel)
            },
            value = secondValue.value,
            onValueChange = { secondValue.value = it },
            singleLine = true
        )
    }
}

@Composable
private fun CustomerEmailListRow(
    modifier: Modifier = Modifier,
    items: MutableList<String>
) {
    items.indices.step(2).forEachIndexed { index, value ->
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomChip(
                label = items.getOrElse(index, { "" }),
                editOnClick =  {},
                removeOnClick =  {}
            )

            if (items.getOrElse(index + 1, { "" }).isNotEmpty()) {
                CustomChip(
                    label = items.getOrElse(index + 1, { "" }),
                    editOnClick = {},
                    removeOnClick = {}
                )
            }
        }
    }
}

@Composable
private fun CustomerPhoneListRow(
    modifier: Modifier = Modifier,
    items: MutableList<String>
) {
    items.indices.step(3).forEachIndexed { index, value ->
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomChip(
                label = items.getOrElse(index, { "" }),
                editOnClick =  {},
                removeOnClick =  {}
            )

            if (items.getOrElse(index + 1, { "" }).isNotEmpty()) {
                CustomChip(
                    label = items.getOrElse(index + 1, { "" }),
                    editOnClick = {},
                    removeOnClick = {}
                )
            }

            if (items.getOrElse(index + 2, { "" }).isNotEmpty()) {
                CustomChip(
                    label = items.getOrElse(index + 2, { "" }),
                    editOnClick =  {},
                    removeOnClick =  {}
                )
            }
        }
    }
}

@Composable
private fun CustomChip(
    modifier: Modifier = Modifier,
    label: String,
    editOnClick: () -> Unit,
    removeOnClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(InputChipDefaults.Height)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(InputChipDefaults.AvatarSize)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(
                        onClick = { editOnClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(InputChipDefaults.AvatarSize - 4.dp),
                    painter = painterResource(Res.drawable.edit_2),
                    contentDescription = "Remove Email",
                )
            }

            // Label
            Text(
                modifier = Modifier.padding(4.dp),
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Trailing icon
            Box(
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize + 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(
                        onClick = { removeOnClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    painter = painterResource(Res.drawable.x),
                    contentDescription = "Remove Email",
                )
            }
        }
    }
}