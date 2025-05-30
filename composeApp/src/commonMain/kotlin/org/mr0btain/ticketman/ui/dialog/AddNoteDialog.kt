package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import org.mr0btain.ticketman.components.data.components.Note
import org.mr0btain.ticketman.components.data.components.SubItemType
import org.mr0btain.ticketman.components.data.components.getLabelTextForType

@Composable
fun AddNoteDialog(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    onConfirm: (Note) -> Unit,
    onDismissRequest: () -> Unit
) {
    var author by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

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
                    text = "Add New Note",
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

                Button(
                    onClick = {
                        onConfirm(Note(author, text))
                        onDismissRequest()
                              },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add")
                }
            }
        }
    }
}