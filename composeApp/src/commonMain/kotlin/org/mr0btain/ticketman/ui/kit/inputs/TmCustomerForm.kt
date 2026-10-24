package org.mr0btain.ticketman.ui.kit.inputs

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.user_plus

/**
 * Fields for a new customer, on a panel: the counter's quick add and check-in. Only the name and a phone or email
 * are required
 * @param onSave Gets the customer to save, calls back with what went wrong or null if it worked
 */
@Composable
fun TmCustomerForm(
    onSave: (Customer, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "New customer"
) {
    val form = rememberCustomerFormState()

    TmPanel(modifier = modifier.widthIn(max = 640.dp), title = title, icon = Res.drawable.user_plus) {
        CustomerFields(form)
        CustomerFormActions(form, onSave, onCancel)
    }
}

/**
 * [TmCustomerForm] in its own dialog, for adding a customer from inside another dialog, like the ticket dialog
 * @param onSave Gets the customer to save, calls back with what went wrong or null if it worked. The caller closes it
 */
@Composable
fun TmCustomerDialog(
    onSave: (Customer, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit,
    title: String = "New customer"
) {
    val form = rememberCustomerFormState()

    TmDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        maxWidth = 640.dp,
        dismissOnOutsideClick = false,
        actions = { CustomerFormActions(form, onSave, onDismissRequest) }
    ) {
        CustomerFields(form)
    }
}

private class CustomerFormState {
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var street by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var zip by mutableStateOf("")
    var notifyEmail by mutableStateOf(false)
    var notifySms by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val problem: String?
        get() = when {
            name.isBlank() -> "Enter their name"
            phone.isBlank() && email.isBlank() -> "Enter a phone number or email so they can be reached"
            else -> null
        }

    val customer: Customer
        get() = Customer(
            name = name.trim(),
            phone = phone.trim(),
            email = email.trim(),
            address = Address(street.trim(), city.trim(), state.trim(), zip.trim()),
            notifyEmail = notifyEmail && email.isNotBlank(),
            notifySms = notifySms && phone.isNotBlank()
        )
}

@Composable
private fun rememberCustomerFormState() = remember { CustomerFormState() }

@Composable
private fun CustomerFields(form: CustomerFormState) {
    val spacing = TmTheme.spacing

    TmTextField(modifier = Modifier.fillMaxWidth(), value = form.name, onValueChange = { form.name = it }, label = "Name")
    TmFieldPair(
        { TmTextField(modifier = it, value = form.phone, onValueChange = { v -> form.phone = v }, label = "Phone") },
        { TmTextField(modifier = it, value = form.email, onValueChange = { v -> form.email = v }, label = "Email") }
    )
    TmTextField(modifier = Modifier.fillMaxWidth(), value = form.street, onValueChange = { form.street = it }, label = "Street")
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        TmTextField(modifier = Modifier.weight(2f), value = form.city, onValueChange = { form.city = it }, label = "City")
        TmTextField(modifier = Modifier.weight(1f), value = form.state, onValueChange = { form.state = it }, label = "State")
        TmTextField(modifier = Modifier.weight(1f), value = form.zip, onValueChange = { form.zip = it }, label = "Zip")
    }

    TmMessageConsent(
        email = form.notifyEmail,
        sms = form.notifySms,
        onEmailChange = { form.notifyEmail = it },
        onSmsChange = { form.notifySms = it },
        hasEmail = form.email.isNotBlank(),
        hasPhone = form.phone.isNotBlank()
    )

    (form.error ?: form.problem.takeIf { form.name.isNotEmpty() || form.phone.isNotEmpty() })?.let {
        TmNotice(text = it, kind = if (form.error != null) TmNoticeKind.ERROR else TmNoticeKind.WARNING)
    }
}

@Composable
private fun CustomerFormActions(
    form: CustomerFormState,
    onSave: (Customer, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    TmButtonRow(
        confirmText = "Save customer",
        confirmEnabled = form.problem == null,
        loading = form.saving,
        onCancel = onCancel,
        onConfirm = {
            form.saving = true
            form.error = null

            onSave(form.customer) { problemSaving ->
                form.saving = false
                form.error = problemSaving
            }
        }
    )
}
