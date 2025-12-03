package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun NoteDialog(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    seed: NoteSeed,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = { onDismissRequest() },
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (seed.isNew) "Add" else "Edit",
                    fontSize = 20.sp
                )

                Spacer(Modifier.padding(4.dp))

                OutlinedTextField(
                    label = { Text("Author") },
                    value = seed.author,
                    onValueChange = { seed.setAuthor(it) },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )

                Spacer(Modifier.padding(4.dp))

                OutlinedTextField(
                    label = { Text("Text") },
                    value = seed.message,
                    onValueChange = { seed.setMessage(it) },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(Modifier.padding(4.dp))

                // Cancel and Confirm buton row
                ConfirmCancelButtons(
                    onCancelClick = onDismissRequest,
                    onConfirmClick = {
                        // Call onConfirm
                        onConfirm()

                        // Dismiss
                        onDismissRequest()
                    }
                )
            }
        }
    }
}