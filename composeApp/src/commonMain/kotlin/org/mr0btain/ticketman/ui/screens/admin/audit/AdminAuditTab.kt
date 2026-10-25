package org.mr0btain.ticketman.ui.screens.admin.audit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmColors
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.book_open

private val COLUMNS = listOf(
    TmColumn("When", width = 150.dp),
    TmColumn("Who", width = 130.dp),
    TmColumn("Action", width = 140.dp),
    TmColumn("What", width = 100.dp),
    TmColumn("Details")
)

/**
 * Log of who did what, newest first
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdminAuditTab(
    entries: List<AuditEntry>,
    endReached: Boolean,
    onLoadMore: () -> Unit
) {
    val colors = TmTheme.colors

    if (entries.isEmpty()) {
        TmEmptyState(icon = Res.drawable.book_open, title = "Nothing has been logged yet", message = "Changes and logins show up here as they happen.")
        return
    }

    TmSurface(modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
        LazyColumn(Modifier.padding(TmTheme.spacing.sm)) {
            stickyHeader {
                TmSurface(border = false) { Column { TmTableHeader(COLUMNS) } }
            }

            items(entries, key = { it.identifier }) { entry ->
                TmTableRow(COLUMNS) { column ->
                    when (column) {
                        0 -> Text(formatDateTime(entry.time), style = TmTheme.typography.caption, color = colors.textMuted)
                        1 -> Text(entry.actorName, style = TmTheme.typography.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        2 -> TmBadge(text = formatEnumName(entry.action.name), tone = colors.tone(entry.action), dot = false)
                        3 -> Text(formatEnumName(entry.entityType), style = TmTheme.typography.body, color = colors.text)
                        4 -> Text(
                            text = entry.details.ifBlank { entry.entityIdentifier ?: "" },
                            style = TmTheme.typography.caption,
                            color = colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(TmTheme.spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    if (endReached) {
                        Text("That's the whole log", style = TmTheme.typography.caption, color = colors.textFaint)
                    } else {
                        TmButton(text = "Load older entries", onClick = onLoadMore, variant = TmButtonVariant.SECONDARY)
                    }
                }
            }
        }
    }
}

private fun TmColors.tone(action: AuditAction): TmTone = when (action) {
    AuditAction.DELETE, AuditAction.LOGIN_FAILED, AuditAction.SESSION_REVOKE -> danger
    AuditAction.CREATE, AuditAction.LOGIN -> success
    AuditAction.UPDATE, AuditAction.PASSWORD_RESET, AuditAction.SETTINGS_UPDATE -> accent
    AuditAction.REFUND -> warning
    else -> neutral
}
