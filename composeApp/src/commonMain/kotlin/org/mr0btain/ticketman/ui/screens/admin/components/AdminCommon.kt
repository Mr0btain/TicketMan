package org.mr0btain.ticketman.ui.screens.admin.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.ui.composables.CustomSurface
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import kotlin.time.Clock
import kotlin.time.Instant

// Shared pieces for the admin screen tabs

// ------------------------------------------ Formatting ------------------------------------------

/** Epoch millis as "2026-10-17 14:03 UTC" */
fun formatDateTime(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toString().take(16).replace('T', ' ') + " UTC"

/** How long ago, like "5m ago" */
fun formatTimeAgo(millis: Long, now: Long = Clock.System.now().toEpochMilliseconds()): String {
    val seconds = (now - millis) / 1000

    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

/** Length of time, like "3d 4h" */
fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0

    return when {
        mb >= 1 -> "${(mb * 10).toLong() / 10.0} MB"
        kb >= 1 -> "${(kb * 10).toLong() / 10.0} KB"
        else -> "$bytes B"
    }
}

/** Enum names like IN_PROGRESS as "In progress" */
fun formatEnumName(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

val AccessLevel.label: String
    get() = if (this == AccessLevel.NONE) "Deactivated" else formatEnumName(name)

// ------------------------------------------ Composables ------------------------------------------

/**
 * Big number with a label, for the overview
 * @param highlight Draws the number in the error color, for things that need attention
 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: DrawableResource,
    caption: String? = null,
    highlight: Boolean = false
) {
    CustomSurface(
        modifier = modifier
            .padding(8.dp)
            .width(200.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(icon),
                    contentDescription = label
                )

                Spacer(Modifier.padding(4.dp))

                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.padding(4.dp))

            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.error else Color.Unspecified
            )

            if (caption != null) {
                Text(
                    text = caption,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Card with a title for grouping things on a tab */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    CustomSurface(
        modifier = modifier.padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically, content = action)
            }

            Spacer(Modifier.padding(4.dp))
            HorizontalDivider()
            Spacer(Modifier.padding(4.dp))

            content()
        }
    }
}

/** Labelled bar, the bar's length is [count] out of [total] */
@Composable
fun CountBar(
    label: String,
    count: Long,
    total: Long,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.width(110.dp),
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        LinearProgressIndicator(
            progress = { if (total > 0) count.toFloat() / total else 0f },
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {}
        )

        Text(
            modifier = Modifier.width(56.dp),
            text = count.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun AccessLevelChip(accessLevel: AccessLevel) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = when (accessLevel) {
            AccessLevel.ADMIN -> MaterialTheme.colorScheme.primary
            AccessLevel.NORMAL -> CustomMaterialColors.success
            AccessLevel.RESTRICTED -> MaterialTheme.colorScheme.secondary
            AccessLevel.NONE -> MaterialTheme.colorScheme.error
        }
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            text = accessLevel.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1
        )
    }
}

/** Picks an access level, NONE is left out since deactivating has its own button */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessLevelDropDown(
    modifier: Modifier = Modifier,
    accessLevel: AccessLevel,
    enabled: Boolean = true,
    onSelect: (AccessLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            enabled = enabled,
            value = accessLevel.label,
            onValueChange = {},
            label = { Text("Access") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            listOf(AccessLevel.RESTRICTED, AccessLevel.NORMAL, AccessLevel.ADMIN).forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label, fontSize = 16.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Shown in place of a list when there's nothing in it */
@Composable
fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 14.sp)
    }
}
