package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.ui.composables.DialogTextRow
import org.mr0btain.ticketman.ui.composables.SectionCard

/**
 * Fields for a new customer. Only the name and a phone or email are required
 * @param onSave Gets the customer to save, calls back with what went wrong or null if it worked
 */
@Composable
fun CustomerForm(
    modifier: Modifier = Modifier,
    title: String = "New Customer",
    onSave: (Customer, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val problem = when {
        firstName.isBlank() -> "Enter their name"
        phone.isBlank() && email.isBlank() -> "Enter a phone number or email so they can be reached"
        else -> null
    }

    SectionCard(modifier = modifier.widthIn(max = 640.dp), title = title) {
        DialogTextRow(
            firstLabel = "First Name",
            firstValue = firstName,
            onFirstValueChange = { firstName = it },
            secondLabel = "Last Name",
            secondValue = lastName,
            onSecondValueChange = { lastName = it }
        )

        DialogTextRow(
            firstLabel = "Phone",
            firstValue = phone,
            onFirstValueChange = { phone = it },
            secondLabel = "Email",
            secondValue = email,
            onSecondValueChange = { email = it }
        )

        DialogTextRow(
            firstLabel = "Street",
            firstValue = street,
            onFirstValueChange = { street = it },
            secondLabel = "City",
            secondValue = city,
            onSecondValueChange = { city = it }
        )

        DialogTextRow(
            firstLabel = "State",
            firstValue = state,
            onFirstValueChange = { state = it },
            secondLabel = "Zip",
            secondValue = zip,
            onSecondValueChange = { zip = it }
        )

        (error ?: problem.takeIf { firstName.isNotEmpty() || phone.isNotEmpty() })?.let {
            Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }

            Button(
                enabled = problem == null && !saving,
                onClick = {
                    saving = true
                    error = null

                    val name = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }

                    onSave(
                        Customer(
                            name = joinName(name).lowercase(),
                            phone = phone.trim(),
                            email = email.trim(),
                            address = Address(street.trim(), city.trim(), state.trim(), zip.trim())
                        )
                    ) { problemSaving ->
                        saving = false
                        error = problemSaving
                    }
                }
            ) { Text("Save Customer") }
        }
    }
}

/**
 * The counter's quick add customer screen
 */
@Composable
fun NewCustomerScreen(
    onSave: (Customer, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "New Customer", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        CustomerForm(title = "Details", onSave = onSave, onCancel = onCancel)
    }
}
