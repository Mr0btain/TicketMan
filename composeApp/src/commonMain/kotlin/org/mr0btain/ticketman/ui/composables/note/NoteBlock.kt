package org.mr0btain.ticketman.ui.composables.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.ui.composables.image.ImagePager

// TODO: Setup editable option
@Composable
fun NoteBlock(
    modifier: Modifier = Modifier,
    label: String = "",
    noteData: List<NoteData>,
    onImageClick: (List<ImageData>) -> Unit = {},
    editable: Boolean = false,
    labelSize: TextUnit = 12.sp,
    textSize: TextUnit = 16.sp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        if (label.isNotBlank()) {
            Text(
                modifier = Modifier
                    .padding(2.dp),
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = labelSize
            )

            Spacer(Modifier.padding(2.dp))
        }

        noteData.sortedByDescending { it.note.time().toLong() }.forEach { data ->
            data.note.let { note ->
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

                    if (data.images.isNotEmpty()) {
                        Spacer(Modifier.padding(4.dp))

                        ImagePager(
                            modifier = Modifier.fillMaxWidth(),
                            imageData = data.images,
                            onImageClick = { onImageClick(data.images) },
                        )

                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}