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
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.ui.composables.CustomSurface
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

// Shared pieces for the admin screen tabs

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

/** Shows a user's access, or their status when they aren't active */
@Composable
fun UserStateChip(user: User) {
    when (user.status) {
        UserStatus.ACTIVE -> AccessLevelChip(user.accessLevel)

        else -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (user.status == UserStatus.PENDING) CustomMaterialColors.inProgress else MaterialTheme.colorScheme.error
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                text = formatEnumName(user.status.name),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1
            )
        }
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
