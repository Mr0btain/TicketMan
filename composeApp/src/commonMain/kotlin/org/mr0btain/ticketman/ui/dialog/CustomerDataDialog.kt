package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.ui.theme.onErrorDark
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

@Composable
fun CustomerDataDialog(
    modifier: Modifier = Modifier,
    customerData: CustomerData,
    onClick: (CustomerData) -> Unit,
    onDismiss: () -> Unit,
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

    Dialog(
        onDismissRequest = {
            onDismiss()
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
                        CustomerListTextRow(
                            modifier = Modifier,
                            items = customerData.info.contactInfo.email,
                            onEdit = { }
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
                        CustomerListTextRow(
                            modifier = Modifier,
                            items = customerData.info.contactInfo.phone,
                            onEdit = { }
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
                            onClick(customerData)
                            onDismiss()
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
private fun CustomerListTextRow(
    modifier: Modifier = Modifier,
    items: List<String>,
    onEdit: (List<String>) -> Unit
) {
    items.indices.step(2).forEachIndexed { index, value ->
        Row(
            modifier = modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InputChip(
                onClick = {},
                label = {
                    Text(items.getOrElse(index, { "" }))
                },
                modifier = Modifier,
                leadingIcon = {},
                trailingIcon = {},
                selected = false
            )

            InputChip(
                onClick = {},
                label = {
                    Text(items.getOrElse(index + 1, { "" }))
                },
                modifier = Modifier,
                leadingIcon = {},
                trailingIcon = {},
                selected = false
            )
        }
    }
}