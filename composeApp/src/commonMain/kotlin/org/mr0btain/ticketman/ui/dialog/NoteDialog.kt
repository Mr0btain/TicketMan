package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.getTime

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
    var text by remember { mutableStateOf(seedData.text) }
    val time = remember { if (seedData.time != Long.MAX_VALUE) seedData.time else getTime() }

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
                    text = if (seedData.time != Long.MAX_VALUE) "Edit $label" else "Add $label",
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
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onDismissRequest() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.padding(8.dp))

                    Button(
                        onClick = {
                            // Prettify author name
                            val authorName = author.split(" ")
                                .joinToString(" ") { name -> name.lowercase().replaceFirstChar { it.uppercase() } }

                            // Call onConfirm
                            onConfirm(Note(authorName, text.replaceFirstChar { it.uppercase() }, time))

                            // Dismiss
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(8.dp),
                        enabled = author.isNotBlank() && text.isNotBlank()
                    ) {
                        Text(if (seedData.time != Long.MAX_VALUE) "Confirm" else "Add")
                    }
                }
            }
        }
    }
}