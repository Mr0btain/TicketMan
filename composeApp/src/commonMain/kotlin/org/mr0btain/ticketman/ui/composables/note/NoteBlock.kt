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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit
import java.util.UUID

// TODO: Setup editable option
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
            notes.sortedByDescending { it.time().toLong() }.forEach { data ->
                NoteColumn(data, textSize) {
                    onImageClick(data.identifier)
                }
            }
        } else {
            noteData.sortedByDescending { it.note.time().toLong() }.forEach { data ->
                EditableNoteColumn(
                    noteData = data,
                    textSize = textSize,
                    onImageClick = { onImageClick(data.note.identifier) },
                    onEditClick = { onEditClick(data.note.identifier) }
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
    val

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

        if (noteData.images.isNotEmpty()) {
            Spacer(Modifier.padding(4.dp))

            ImagePager(
                modifier = Modifier.fillMaxWidth(),
                imageData = noteData.images,
                onImageClick = { onImageClick(noteData.note.identifier) },
            )

            Spacer(Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun EditableNoteColumn(
    noteData: NoteData,
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
                    text = noteData.note.author + ": ",
                    fontSize = textSize,
                    fontWeight = FontWeight.SemiBold
                )

                // Time
                Text(
                    text = noteData.note.time().toString(),
                    fontSize = (textSize.value - 4).sp,
                    maxLines = 1
                )
            }

            // Text
            Text(
                modifier = Modifier,
                text = noteData.note.message,
                fontSize = textSize
            )

            if (noteData.images.isNotEmpty()) {
                Spacer(Modifier.padding(4.dp))

                ImagePager(
                    modifier = Modifier.fillMaxWidth(),
                    imageData = noteData.images,
                    onImageClick = { onImageClick(noteData.note.identifier) },
                )

                Spacer(Modifier.padding(4.dp))
            }
        }
    }
}