package org.mr0btain.ticketman.ui.composables.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.utils.ImageUtils
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit
import java.util.UUID

// TODO: Figure out better way to do images.
@Composable
fun NoteBlock(
    modifier: Modifier = Modifier,
    label: String = "Notes",
    notes: List<Note>,
    editable: Boolean = false,
    labelSize: TextUnit = 18.sp,
    textSize: TextUnit = 16.sp,
    onImageClick: (UUID) -> Unit = {},
    onEditClick: (UUID) -> Unit = {}
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        if (label.isNotBlank()) {
            Text(
                modifier = Modifier.padding(2.dp),
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = labelSize
            )

            Spacer(Modifier.padding(2.dp))
        }

        if (!editable) {
            notes
                .sortedByDescending { it.time().toLong() }
                .forEach { note ->
                    NoteColumn(note, textSize) {
                        onImageClick(it)
                    }
                }
        } else {
            notes
                .sortedByDescending { it.time().toLong() }
                .forEach { note ->
                    EditableNoteColumn(
                        note = note,
                        textSize = textSize,
                        onImageClick = { onImageClick(it) },
                        onEditClick = { onEditClick(note.identifier) }
                    )
                }
        }
    }
}

@Composable
private fun NoteColumn(
    note: Note,
    textSize: TextUnit,
    onImageClick: (UUID) -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Author
            Text(
                text = note.author + ": ",
                fontSize = textSize,
                fontWeight = FontWeight.SemiBold
            )

            // Time
            Text(
                text = note.time().toString(),
                fontSize = (textSize.value - 4).sp,
                maxLines = 1
            )
        }

        // Text
        Text(
            modifier = Modifier,
            text = note.message,
            fontSize = textSize
        )

        if (note.images.isNotEmpty()) {
            Spacer(Modifier.padding(4.dp))

            ImagePager(
                modifier = Modifier.fillMaxWidth(),
                storedImages = note.images,
                onImageClick = { onImageClick(note.identifier) },
            )

            Spacer(Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun EditableNoteColumn(
    note: Note,
    textSize: TextUnit,
    onImageClick: (UUID) -> Unit,
    onEditClick: () -> Unit
) {
    Row {
        IconButton(
            onClick = onEditClick
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(Res.drawable.edit),
                contentDescription = "Edit note"
            )
        }

        Spacer(Modifier.padding(2.dp))

        Column{
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author
                Text(
                    text = note.author + ": ",
                    fontSize = textSize,
                    fontWeight = FontWeight.SemiBold
                )

                // Time
                Text(
                    text = note.time().toString(),
                    fontSize = (textSize.value - 4).sp,
                    maxLines = 1
                )
            }

            // Text
            Text(
                modifier = Modifier,
                text = note.message,
                fontSize = textSize
            )

            if (note.images.isNotEmpty()) {
                Spacer(Modifier.padding(4.dp))

                ImagePager(
                    modifier = Modifier.fillMaxWidth(),
                    storedImages = note.images,
                    onImageClick = { onImageClick(note.identifier) },
                )

                Spacer(Modifier.padding(4.dp))
            }
        }
    }
}