package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.util.toImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.utils.encodeToBase64
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.getTime
import org.mr0btain.ticketman.data.ticket.TicketUpdate
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

@Composable
fun EditUpdateDialog(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    seedData: TicketUpdate = TicketUpdate(),
    label: String = "",
    onConfirm: (TicketUpdate) -> Unit,
    onDismissRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    var author by remember { mutableStateOf(seedData.note.author) }
    var text by remember { mutableStateOf(seedData.note.text) }
    val time = remember { if (seedData.note.time != Long.MAX_VALUE) seedData.note.time else getTime() }
    val photos = remember { seedData.images.toMutableStateList() }

    // File dialog for adding an image
    val launcher = rememberFilePickerLauncher(
        type = FileKitType.Image
    ) { file ->
        file?.let { image ->
            scope.launch {
                try {
                    photos.add(
                        image
                            .toImageBitmap()
                            .encodeToBase64()
                    )
                } catch (ex: Exception) {
                    AppLogger.i("EDIT_UPDATE_DIALOG", ex.toString())
                }
            }
        }
    }

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
                    text = if (seedData.note.time != Long.MAX_VALUE) "Edit $label" else "Add $label",
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

                Spacer(Modifier.padding(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photos",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )

                    Spacer(Modifier.padding(4.dp))

                    IconButton(
                        modifier = Modifier.size(26.dp),
                        onClick = { launcher.launch() }
                    ) {
                        Icon(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(20.dp),
                            painter = painterResource(Res.drawable.plus),
                            contentDescription = "Add new photo"
                        )
                    }
                }

                if (photos.isNotEmpty()) {
                    ImagePager(
                        modifier = Modifier
                            .height(150.dp),
                        imageData = photos,
                        imageMaxWidth = 200.dp,
                        imageMaxHeight = 150.dp
                    )
                }

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
                            onConfirm(
                                TicketUpdate(
                                    identifier = seedData.identifier,
                                    note = Note(authorName, text.replaceFirstChar { it.uppercase() }, time),
                                    images = photos
                                )
                            )

                            // Dismiss
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(8.dp),
                        enabled = author.isNotBlank() && text.isNotBlank()
                    ) {
                        Text(if (seedData.note.time != Long.MAX_VALUE) "Confirm" else "Add")
                    }
                }
            }
        }
    }
}