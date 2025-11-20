package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.utils.toImageBitmap
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import org.mr0btain.ticketman.ui.composables.loading.CircularLoading
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun EditNoteDialog(
    modifier: Modifier = Modifier,
    seedData: NoteData?,
    label: String = "",
    isVisible: Boolean,
    onConfirm: (NoteData) -> Unit,
    onDismissRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    if (isVisible) {
        seedData?.let { noteData ->
            var author by remember { mutableStateOf(noteData.note.author) }
            var message by remember { mutableStateOf(noteData.note.message) }
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
                            ImageData(
                                directory = noteData.note.identifier.toString(),
                                image = FileKit.compressImage(
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
                    modifier = modifier
                        .wrapContentSize()
                        .sizeIn(maxHeight = 600.dp),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp
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
                            value = message,
                            onValueChange = { message = it },
                            maxLines = 6,
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

                        if (imageData.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .height(150.dp)
                                    .width(300.dp),
                                contentAlignment = Alignment.Center // ensures loader is centered
                            ) {
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
                                    onImageClick = { /* TODO */ },
                                    coroutineScope = scope,
                                    clickEnabled = false
                                )

                                if (isLoading) {
                                    CircularLoading() // sits centered on top
                                }
                            }
                        }

                        Spacer(Modifier.padding(4.dp))

                        // Cancel and Confirm buton row
                        ConfirmCancelButtons(
                            onCancelClick = onDismissRequest,
                            onConfirmClick =  {
                                onConfirm(
                                    NoteData(
                                        note = Note(
                                            identifier = noteData.note.identifier,
                                            author = author,
                                            message = message
                                        ),
                                        images = imageData
                                    )
                                )

                                // Dismiss
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        } ?: onDismissRequest()
    }
}