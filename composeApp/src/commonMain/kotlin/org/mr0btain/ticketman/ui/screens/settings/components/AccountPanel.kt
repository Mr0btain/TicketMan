package org.mr0btain.ticketman.ui.screens.settings.components

import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.network.VersionNotice
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.kit.images.TmAvatar
import org.mr0btain.ticketman.ui.kit.images.TmPhotoEditor
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.log_in
import ticketman.composeapp.generated.resources.log_out
import ticketman.composeapp.generated.resources.user

/**
 * Who is signed in, with a button to log in or out. The phone layout has no user menu, so this is where phones do
 * it; on wide screens it's the same as the menu at the foot of the nav rail
 * @param user Whoever is signed in, null when nobody is
 * @param versionNotice How this app and the server differ in version, null when they're the same. One that blocks
 * connecting is already said by [connectionProblem]
 * @param connectionProblem Why the server address didn't connect, null when it did
 * @param insecureWarning Why the connection isn't safe, null unless it's plain HTTP
 * @param onPhotoChange Sets their own profile photo, null for Restricted users, who can't
 */
@Composable
fun AccountPanel(
    user: User?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    versionNotice: VersionNotice? = null,
    connectionProblem: String? = null,
    insecureWarning: String? = null,
    onPhotoChange: ((StoredImage?) -> Unit)? = null
) {
    TmPanel(modifier = modifier.fillMaxWidth(), title = "Account", icon = Res.drawable.user) {
        connectionProblem?.let { TmNotice(kind = TmNoticeKind.ERROR, title = "Can't connect", text = it) }
        versionNotice?.takeIf { !it.blocking }?.let { notice ->
            TmNotice(kind = TmNoticeKind.WARNING, title = if (notice.updateServer) "Update the server" else "Update the app", text = notice.message)
        }
        insecureWarning?.let { TmNotice(kind = TmNoticeKind.WARNING, title = "Not secure", text = it) }

        if (user == null) {
            TmListRow(
                title = "Not logged in",
                subtitle = "Log in to load records and use the counter",
                trailing = { TmButton(text = "Log in", onClick = onLogin, leadingIcon = Res.drawable.log_in) }
            )
        } else {
            val name = user.name.trim().ifBlank { user.username }

            TmListRow(
                title = name,
                subtitle = "${user.username}, ${formatEnumName(user.accessLevel.name)} access",
                leading = { TmAvatar(name = name, photo = user.photo) },
                trailing = {
                    TmButton(text = "Log out", onClick = onLogout, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.log_out)
                }
            )

            // Shown on the admin screen, the user menu and their ID card
            onPhotoChange?.let { change ->
                TmPhotoEditor(
                    images = listOfNotNull(user.photo),
                    onAdd = { change(it) },
                    onRemove = { change(null) },
                    buttonText = if (user.photo == null) "Add your photo" else "Change your photo"
                )
            }
        }
    }
}
