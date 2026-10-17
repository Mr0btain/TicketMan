package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.user_plus

/**
 * Fields for a new customer. Only the name and a phone or email are required
 * @param onSave Gets the customer to save, calls back with what went wrong or null if it worked
 */
@Composable
fun CustomerForm(
    modifier: Modifier = Modifier,
    title: String = "New customer",
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
    val spacing = TmTheme.spacing

    val problem = when {
        firstName.isBlank() -> "Enter their name"
        phone.isBlank() && email.isBlank() -> "Enter a phone number or email so they can be reached"
        else -> null
    }

    TmPanel(modifier = modifier.widthIn(max = 640.dp), title = title, icon = Res.drawable.user_plus) {
        TmFieldPair(
            { TmTextField(modifier = it, value = firstName, onValueChange = { v -> firstName = v }, label = "First name") },
            { TmTextField(modifier = it, value = lastName, onValueChange = { v -> lastName = v }, label = "Last name") }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = phone, onValueChange = { v -> phone = v }, label = "Phone") },
            { TmTextField(modifier = it, value = email, onValueChange = { v -> email = v }, label = "Email") }
        )
        TmTextField(modifier = Modifier.fillMaxWidth(), value = street, onValueChange = { street = it }, label = "Street")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmTextField(modifier = Modifier.weight(2f), value = city, onValueChange = { city = it }, label = "City")
            TmTextField(modifier = Modifier.weight(1f), value = state, onValueChange = { state = it }, label = "State")
            TmTextField(modifier = Modifier.weight(1f), value = zip, onValueChange = { zip = it }, label = "Zip")
        }

        (error ?: problem.takeIf { firstName.isNotEmpty() || phone.isNotEmpty() })?.let {
            TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.WARNING)
        }

        TmButtonRow(
            confirmText = "Save customer",
            confirmEnabled = problem == null,
            loading = saving,
            onCancel = onCancel,
            onConfirm = {
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
        )
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)
    ) {
        TmPageHeader(title = "New customer", onBack = onCancel)
        CustomerForm(title = "Details", onSave = onSave, onCancel = onCancel)
    }
}
