package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.image.newImageData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.utils.toImageBitmap
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.time.getTime
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import org.mr0btain.ticketman.ui.composables.loading.CircularLoading
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun EditNoteDialog(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    noteData: NoteData,
    label: String = "",
    onConfirm: (NoteData) -> Unit,
    onDismissRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    var author by remember { mutableStateOf(noteData.note.author) }
    var message by remember { mutableStateOf(noteData.note.message) }
    val time = remember { if (noteData.note.time != Long.MAX_VALUE) noteData.note.time else getTime() }
    val imageData = remember { noteData.images.toMutableStateList() }

    // For ui loading
    var isLoading by remember { mutableStateOf(false) }

    // File dialog for adding an image
    val launcher = rememberFilePickerLauncher(
        type = FileKitType.Image
    ) { file ->
        file?.let { file ->
            scope.launch {
                isLoading = true

                // Add to list resizing to a max of 1080p
                imageData.add(
                    newImageData(
                        noteData.note.identifier,
                        FileKit.compressImage(
                            file = file,
                            imageFormat = ImageFormat.PNG,
                            maxWidth = 1920,
                            maxHeight = 1080
                        ).toImageBitmap()
                    )
                )

                isLoading = false
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
                    text = if (noteData.note.time != Long.MAX_VALUE) "Edit $label" else "Add $label",
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
                    value = message,
                    onValueChange = { message = it },
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

                Box(
                    modifier = Modifier
                        .height(150.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center // ensures loader is centered
                ) {
                    if (imageData.isNotEmpty()) {
                        ImagePager(
                            modifier = Modifier
                                .graphicsLayer {
                                    if (isLoading) {
                                        alpha = 0.5f   // dim
                                        // you could also apply scale or rotation effects here
                                    }
                                }
                                .blur(if (isLoading) 8.dp else 0.dp), // blur while loading
                            imageData = imageData,
                            imageMaxWidth = 200.dp,
                            imageMaxHeight = 150.dp,
                            onImageClick = { /* TODO */ },
                            coroutineScope = scope,
                            clickEnabled = false
                        )
                    }

                    if (isLoading) {
                        CircularLoading() // sits centered on top
                    }
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
                            onConfirm(
                                NoteData(
                                    note = Note(
                                        identifier = noteData.note.identifier,
                                        author = author,
                                        message = message,
                                        time = time
                                    ),
                                    images = imageData
                                )
                            )

                            // Dismiss
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(8.dp),
                        enabled = author.isNotBlank() && message.isNotBlank()
                    ) {
                        Text(if (noteData.note.time != Long.MAX_VALUE) "Confirm" else "Add")
                    }
                }
            }
        }
    }
}