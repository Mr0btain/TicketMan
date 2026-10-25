package org.mr0btain.ticketman.ui.screens.purchasing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.truck

/**
 * The shop's suppliers, to add, change and delete
 * @param onSave Saves one, new when the flag is set; calls back with what went wrong or null
 */
@Composable
fun SuppliersPane(
    suppliers: List<Supplier>,
    canEdit: Boolean,
    onSave: (Supplier, Boolean, (String?) -> Unit) -> Unit,
    onRemove: (Supplier) -> Unit
) {
    var editing by remember { mutableStateOf<Pair<Supplier, Boolean>?>(null) }
    var removing by remember { mutableStateOf<Supplier?>(null) }

    editing?.let { (supplier, isNew) ->
        SupplierDialog(supplier = supplier, isNew = isNew, onSave = onSave, onDismiss = { editing = null })
    }

    removing?.let { supplier ->
        TmConfirmDialog(
            title = "Delete ${supplier.name}?",
            message = "Orders keep its name. Items ordered from it won't have a supplier any more.",
            confirmText = "Delete supplier",
            destructive = true,
            onConfirm = {
                onRemove(supplier)
                removing = null
            },
            onDismissRequest = { removing = null }
        )
    }

    TmPanel(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        title = "Suppliers",
        subtitle = "Who parts are ordered from",
        icon = Res.drawable.truck,
        actions = { if (canEdit) TmButton(text = "New supplier", onClick = { editing = Supplier() to true }, leadingIcon = Res.drawable.plus) }
    ) {
        if (suppliers.isEmpty()) {
            TmEmptyState(icon = Res.drawable.truck, title = "No suppliers yet", message = "Add the places you buy parts from, then order from them.")
            return@TmPanel
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            suppliers.forEach { supplier ->
                TmListRow(
                    title = supplier.name,
                    subtitle = listOf(supplier.contactName, supplier.phone, supplier.email).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                    trailing = if (canEdit) ({ TmButton(text = "Delete", onClick = { removing = supplier }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL) }) else null,
                    onClick = if (canEdit) ({ editing = supplier to false }) else null
                )
            }
        }
    }
}

@Composable
private fun SupplierDialog(supplier: Supplier, isNew: Boolean, onSave: (Supplier, Boolean, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(supplier) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TmDialog(
        title = if (isNew) "New supplier" else "Edit ${supplier.name}",
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
        actions = {
            TmButtonRow(
                confirmText = if (isNew) "Add supplier" else "Save supplier",
                confirmEnabled = draft.name.isNotBlank() && draft != supplier,
                loading = busy,
                onCancel = onDismiss,
                onConfirm = {
                    busy = true
                    error = null
                    onSave(draft, isNew) { failed ->
                        busy = false
                        error = failed
                        if (failed == null) onDismiss()
                    }
                }
            )
        }
    ) {
        TmTextField(modifier = Modifier.fillMaxWidth(), value = draft.name, onValueChange = { draft = draft.copy(name = it) }, label = "Name")
        TmFieldPair(
            { TmTextField(modifier = it, value = draft.contactName, onValueChange = { v -> draft = draft.copy(contactName = v) }, label = "Contact") },
            { TmTextField(modifier = it, value = draft.phone, onValueChange = { v -> draft = draft.copy(phone = v) }, label = "Phone") }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = draft.email, onValueChange = { v -> draft = draft.copy(email = v) }, label = "Email") },
            { TmTextField(modifier = it, value = draft.website, onValueChange = { v -> draft = draft.copy(website = v) }, label = "Website") }
        )
        TmTextField(modifier = Modifier.fillMaxWidth(), value = draft.accountNumber, onValueChange = { draft = draft.copy(accountNumber = it) }, label = "Account number")
        TmTextField(modifier = Modifier.fillMaxWidth(), value = draft.notes, onValueChange = { draft = draft.copy(notes = it) }, label = "Notes", singleLine = false, minLines = 2)

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}
