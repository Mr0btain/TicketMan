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
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun NoteDialog(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    seedData: Note = Note(),
    label: String = "Add",
    onConfirm: (Note) -> Unit,
    onDismissRequest: () -> Unit
) {
    var author by remember { mutableStateOf(seedData.author) }
    var text by remember { mutableStateOf(seedData.message) }

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
                    text = label,
                    fontSize = 20.sp
                )

                Spacer(Modifier.padding(4.dp))

                OutlinedTextField(
                    label = { Text("Author") },
                    value = author,
                    onValueChange = { author = it },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )

                Spacer(Modifier.padding(4.dp))

                OutlinedTextField(
                    label = { Text("Text") },
                    value = text,
                    onValueChange = { text = it },
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
                        // Prettify author name
                        val authorName = author.split(" ")
                            .joinToString(" ") { name -> name.lowercase().replaceFirstChar { it.uppercase() } }

                        // Call onConfirm
                        onConfirm(Note(seedData.identifier, authorName, text.replaceFirstChar { it.uppercase() }))

                        // Dismiss
                        onDismissRequest()
                    }
                )
            }
        }
    }
}