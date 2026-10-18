package org.mr0btain.ticketman.ui.screens.admin.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.ui.kit.feedback.TmAccessBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmUserStatusBadge
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown

// Shared pieces for the admin screen tabs

val AccessLevel.label: String
    get() = if (this == AccessLevel.NONE) "Deactivated" else formatEnumName(name)

/** Shows a user's access, or their status when they aren't active */
@Composable
fun UserStateChip(user: User) {
    when (user.status) {
        UserStatus.ACTIVE -> TmAccessBadge(user.accessLevel)
        else -> TmUserStatusBadge(user.status)
    }
}

/** Picks an access level, NONE is left out since deactivating has its own button */
@Composable
fun AccessLevelDropDown(
    modifier: Modifier = Modifier,
    accessLevel: AccessLevel,
    enabled: Boolean = true,
    onSelect: (AccessLevel) -> Unit
) {
    TmDropdown(
        modifier = modifier,
        label = "Access",
        options = listOf(AccessLevel.RESTRICTED, AccessLevel.NORMAL, AccessLevel.ADMIN),
        selected = accessLevel,
        onSelect = onSelect,
        optionLabel = { it.label },
        enabled = enabled
    )
}
