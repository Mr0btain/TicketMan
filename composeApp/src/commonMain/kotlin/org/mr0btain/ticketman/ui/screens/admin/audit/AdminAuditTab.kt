package org.mr0btain.ticketman.ui.screens.admin.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

/**
 * Log of who did what, newest first
 */
@Composable
fun AdminAuditTab(
    entries: List<AuditEntry>,
    endReached: Boolean,
    onLoadMore: () -> Unit
) {
    if (entries.isEmpty()) {
        EmptyMessage("Nothing has been logged yet")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("When", Modifier.width(150.dp), fontWeight = FontWeight.SemiBold)
                Text("Who", Modifier.width(130.dp), fontWeight = FontWeight.SemiBold)
                Text("Action", Modifier.width(140.dp), fontWeight = FontWeight.SemiBold)
                Text("What", Modifier.width(110.dp), fontWeight = FontWeight.SemiBold)
                Text("Details", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider(Modifier.widthIn(max = 1000.dp))
        }

        items(entries, key = { it.identifier }) { entry ->
            Row(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatDateTime(entry.time), Modifier.width(150.dp), fontSize = 12.sp)
                Text(entry.actorName, Modifier.width(130.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.width(140.dp)) { ActionChip(entry.action) }
                Text(formatEnumName(entry.entityType), Modifier.width(110.dp), fontSize = 13.sp)
                Text(
                    text = entry.details.ifBlank { entry.entityIdentifier ?: "" },
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (endReached) {
                    Text("End of the log", fontSize = 12.sp)
                } else {
                    OutlinedButton(onClick = onLoadMore) { Text("Load More") }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(action: AuditAction) {
    val color = when (action) {
        AuditAction.DELETE, AuditAction.LOGIN_FAILED, AuditAction.SESSION_REVOKE -> MaterialTheme.colorScheme.error
        AuditAction.CREATE, AuditAction.LOGIN -> CustomMaterialColors.success
        AuditAction.UPDATE, AuditAction.PASSWORD_RESET, AuditAction.SETTINGS_UPDATE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            text = formatEnumName(action.name),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1
        )
    }
}
