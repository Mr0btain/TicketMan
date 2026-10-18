package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField

/**
 * Dialog for changing one text value, like a setting
 * @param onConfirm Gets the new value, returns false to keep the dialog open and mark the value as wrong
 * @param hint Shown under the field, and in place of the generic message when a value is rejected
 */
@Composable
fun TmTextEditDialog(
    label: String,
    value: String,
    visible: Boolean,
    onConfirm: (String) -> Boolean,
    onCancel: () -> Unit,
    keyboardType: KeyboardType,
    hint: String? = null
) {
    if (!visible) return

    val focusRequester = remember { FocusRequester() }
    var isError by remember { mutableStateOf(false) }
    var field by remember(value) { mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length))) }

    fun save() {
        if (onConfirm(field.text)) {
            isError = false
            onCancel()
        } else {
            isError = true
        }
    }

    TmDialog(
        title = label,
        onDismissRequest = onCancel,
        actions = { TmButtonRow(confirmText = "Save", onConfirm = ::save, onCancel = onCancel) }
    ) {
        TmTextField(
            modifier = Modifier.focusRequester(focusRequester),
            value = field,
            onValueChange = { field = it },
            supportingText = hint,
            error = if (isError) hint ?: "That value can't be used" else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() })
        )
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
