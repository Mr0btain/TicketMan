package org.mr0btain.ticketman.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.data.TmImagePager
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit_2
import kotlin.uuid.Uuid

/**
 * Notes newest first: author, time, message and any photos
 * @param editable Shows an edit button on each note
 * @param emptyText Shown when there are no notes, null shows nothing
 * @param onImageClick Gets the note's identifier when one of its photos is clicked
 */
@Composable
fun TmNoteBlock(
    modifier: Modifier = Modifier,
    notes: List<Note>,
    editable: Boolean = false,
    emptyText: String? = "No notes yet",
    onImageClick: (Uuid) -> Unit = {},
    onEditClick: (Note) -> Unit = {}
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (notes.isEmpty() && emptyText != null) {
            Text(text = emptyText, style = TmTheme.typography.body, color = colors.textMuted)
        }

        notes.sortedByDescending { it.timestamp.toEpochMilliseconds() }.forEach { note ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = note.author.ifBlank { "Unknown" }, style = TmTheme.typography.label, color = colors.text)
                    Text(
                        modifier = Modifier.weight(1f),
                        text = formatDateTime(note.timestamp.toEpochMilliseconds()),
                        style = TmTheme.typography.caption,
                        color = colors.textMuted,
                        maxLines = 1
                    )
                    if (editable) {
                        TmIconButton(icon = Res.drawable.edit_2, contentDescription = "Edit note", onClick = { onEditClick(note) }, size = TmControlSize.SMALL)
                    }
                }

                Text(text = note.message, style = TmTheme.typography.body, color = colors.text)

                if (note.images.isNotEmpty()) {
                    TmImagePager(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        storedImages = note.images,
                        onImageClick = { onImageClick(note.identifier) }
                    )
                }
            }
        }
    }
}
