package org.mr0btain.ticketman.ui.kit.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.image.LocalImageClient
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * A person as a round picture: their profile photo, or their initials on the accent color when they have none or
 * there's no server to fetch it from (previews, render tests)
 * @param name Who it is, for the initials and screen readers
 */
@Composable
fun TmAvatar(
    name: String,
    photo: StoredImage?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val colors = TmTheme.colors
    val showPhoto = photo != null && LocalImageClient.current != null

    Box(
        modifier
            .size(size)
            .clip(TmTheme.shapes.pill)
            .background(colors.accent.soft),
        contentAlignment = Alignment.Center
    ) {
        if (showPhoto) {
            TmImage(
                image = photo,
                modifier = Modifier.fillMaxSize(),
                contentDescription = name,
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initialsOf(name),
                style = if (size >= 64.dp) TmTheme.typography.title else TmTheme.typography.label,
                color = colors.accent.color
            )
        }
    }
}

/** Up to two letters: first and last word, "Dana Whitfield" is "DW", "dana" is "D" */
fun initialsOf(name: String): String {
    val words = name.trim().split(' ').filter { it.isNotBlank() }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull())
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
}
