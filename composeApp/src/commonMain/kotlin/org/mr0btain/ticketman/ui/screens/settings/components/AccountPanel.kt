package org.mr0btain.ticketman.ui.screens.settings.components

import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.log_in
import ticketman.composeapp.generated.resources.log_out
import ticketman.composeapp.generated.resources.user

/**
 * Who is signed in, with a button to log in or out. The phone layout has no user menu, so this is where phones do
 * it; on wide screens it's the same as the menu at the foot of the nav rail
 * @param user Whoever is signed in, null when nobody is
 * @param apiMismatch Why this app and the server may not work together, null when they're the same version
 * @param connectionProblem Why the server address didn't connect, null when it did
 * @param insecureWarning Why the connection isn't safe, null unless it's plain HTTP
 */
@Composable
fun AccountPanel(
    user: User?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    apiMismatch: String? = null,
    connectionProblem: String? = null,
    insecureWarning: String? = null
) {
    TmPanel(modifier = modifier.fillMaxWidth(), title = "Account", icon = Res.drawable.user) {
        connectionProblem?.let { TmNotice(kind = TmNoticeKind.ERROR, title = "Can't connect", text = it) }
        apiMismatch?.let { TmNotice(kind = TmNoticeKind.WARNING, title = "Different versions", text = it) }
        insecureWarning?.let { TmNotice(kind = TmNoticeKind.WARNING, title = "Not secure", text = it) }

        if (user == null) {
            TmListRow(
                title = "Not logged in",
                subtitle = "Log in to load records and use the counter",
                trailing = { TmButton(text = "Log in", onClick = onLogin, leadingIcon = Res.drawable.log_in) }
            )
        } else {
            TmListRow(
                title = user.name.trim().ifBlank { user.username },
                subtitle = "${user.username}, ${formatEnumName(user.accessLevel.name)} access",
                trailing = {
                    TmButton(text = "Log out", onClick = onLogout, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.log_out)
                }
            )
        }
    }
}
