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
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.utils.ImageUtils
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import org.mr0btain.ticketman.ui.composables.loading.CircularLoading
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus

@Composable
fun EditNoteDialog(
    modifier: Modifier = Modifier,
    seed: NoteSeed,
    label: String = "",
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    if (isVisible) {
        // For ui loading
        var isLoading by remember { mutableStateOf(false) }

        // TODO: Add a dialog for editing image details
        // File dialog for adding an image
        val launcher = rememberFilePickerLauncher(
            type = FileKitType.Image
        ) { file ->
            file?.let { file ->
                scope.launch {
                    isLoading = true

                    // Init a new image
                    val newImage = StoredImage()

                    // Save image to database
                    val dbImage = ImageUtils.saveImageFromFile(
                        parentId = seed.identifier,
                        identifier = newImage.identifier,
                        file = file
                    )

                    // Add to list resizing to a max of 1080p
                    if (dbImage) seed.addImage(newImage)

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

                    if (seed.images.isNotEmpty()) {
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
                                storedImages = seed.images,
                                onImageClick = { /* TODO */ },
                                coroutineScope = scope,
                                clickEnabled = false
                            )

                            if (isLoading) CircularLoading() // sits centered on top
                        }
                    }

                    Spacer(Modifier.padding(4.dp))

                    // Cancel and Confirm buton row
                    ConfirmCancelButtons(
                        onCancelClick = onDismissRequest,
                        onConfirmClick =  {
                            // Confirm
                            onConfirm()

                            // Dismiss
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}