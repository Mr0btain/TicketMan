package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone

/**
 * Something on a calendar that runs from one day or time to another, like a shift or time off: a solid block in
 * [tone] filling the width it's given. Its ends are squared off where it carries on past the edge being drawn, like a week
 * row in a month
 * @param subtitle A second line, like its hours, when there's room
 * @param continuesBefore It started before what's showing
 * @param continuesAfter It goes on after what's showing
 */
@Composable
fun TmScheduleBlock(
    title: String,
    tone: TmTone,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    continuesBefore: Boolean = false,
    continuesAfter: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val corner = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (continuesBefore) 0.dp else corner,
        bottomStart = if (continuesBefore) 0.dp else corner,
        topEnd = if (continuesAfter) 0.dp else corner,
        bottomEnd = if (continuesAfter) 0.dp else corner
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(tone.color)
            .then(if (onClick != null) Modifier.tmClickable(onClick = onClick, shape = shape, overlay = tone.onColor) else Modifier)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = if (subtitle == null) 2.dp else 4.dp)) {
            Text(
                text = (if (continuesBefore) "‹ " else "") + title + (if (continuesAfter) " ›" else ""),
                style = TmTheme.typography.label,
                color = tone.onColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(text = it, style = TmTheme.typography.caption, color = tone.onColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
