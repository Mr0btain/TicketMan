package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.foundation.clickable
import org.mr0btain.ticketman.ui.kit.images.TmAvatar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.feedback.TmSnackbarHost
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.glassSource
import org.mr0btain.ticketman.ui.kit.navigation.TmBottomNav
import org.mr0btain.ticketman.ui.kit.selection.TmMenu
import org.mr0btain.ticketman.ui.kit.selection.TmMenuItem
import org.mr0btain.ticketman.ui.kit.navigation.TmNavItem
import org.mr0btain.ticketman.ui.kit.navigation.TmNavRail
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.log_in
import ticketman.composeapp.generated.resources.log_out
import ticketman.composeapp.generated.resources.settings
import ticketman.composeapp.generated.resources.sidebar
import ticketman.composeapp.generated.resources.user

/** Height of the phone bottom bar plus its margin, screens pad their bottom by this */
private val BOTTOM_NAV_SPACE = 80.dp

/**
 * Wide screen layout: glass nav rail on the left, the current screen beside it
 * @param user Signed in user shown at the bottom of the rail, null shows a log in button
 */
@Composable
fun WideAppShell(
    navHostController: NavHostController,
    destinations: List<NavigationItem>,
    snackBarState: SnackbarHostState,
    user: User?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val spacing = TmTheme.spacing
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedRoute = navHostController.currentBackStackEntryAsState().value?.destination?.route
    val items = destinations.sortedBy { it.destination.index }.map { it.toNavItem() }

    TmBackdrop(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            TmNavRail(
                items = items,
                selectedKey = selectedRoute,
                onSelect = { navHostController.navigateTo(it.key) },
                expanded = expanded,
                header = {
                    TmIconButton(
                        icon = Res.drawable.sidebar,
                        contentDescription = if (expanded) "Collapse the sidebar" else "Expand the sidebar",
                        onClick = { expanded = !expanded }
                    )
                },
                footer = {
                    if (user == null) {
                        if (expanded) {
                            TmButton(text = "Log in", onClick = onLogin, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.log_in)
                        } else {
                            TmIconButton(icon = Res.drawable.log_in, contentDescription = "Log in", onClick = onLogin)
                        }
                    } else {
                        UserMenu(user = user, expanded = expanded, onOpenSettings = onOpenSettings, onLogout = onLogout)
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .glassSource(),
                content = content
            )
        }

        TmSnackbarHost(state = snackBarState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Phone layout: the current screen with a glass bar of places floating at the bottom */
@Composable
fun PhoneAppShell(
    navHostController: NavHostController,
    destinations: List<NavigationItem>,
    snackBarState: SnackbarHostState,
    content: @Composable BoxScope.() -> Unit
) {
    val selectedRoute = navHostController.currentBackStackEntryAsState().value?.destination?.route
    val items = destinations.sortedBy { it.destination.index }.map { it.toNavItem() }

    TmBackdrop(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassSource()
                .padding(bottom = BOTTOM_NAV_SPACE),
            content = content
        )

        TmSnackbarHost(
            state = snackBarState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BOTTOM_NAV_SPACE)
        )

        TmBottomNav(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(TmTheme.spacing.sm),
            items = items,
            selectedKey = selectedRoute,
            onSelect = { navHostController.navigateTo(it.key) }
        )
    }
}

/** The signed in user at the bottom of the rail, clicking it offers settings and logging out */
@Composable
private fun UserMenu(user: User, expanded: Boolean, onOpenSettings: () -> Unit, onLogout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = user.name.trim().ifBlank { user.username }

    Box {
        if (expanded) {
            TmListRow(title = name, subtitle = user.username, leading = { TmAvatar(name = name, photo = user.photo, size = 28.dp) }, onClick = { open = true })
        } else {
            TmAvatar(
                name = name,
                photo = user.photo,
                modifier = Modifier.clickable(onClickLabel = "Signed in as $name, open the menu") { open = true },
                size = 36.dp
            )
        }

        TmMenu(expanded = open, onDismissRequest = { open = false }) {
            TmMenuItem(text = "Settings", leadingIcon = Res.drawable.settings, onClick = {
                open = false
                onOpenSettings()
            })
            TmMenuItem(text = "Log out", leadingIcon = Res.drawable.log_out, destructive = true, onClick = {
                open = false
                onLogout()
            })
        }
    }
}

private fun NavigationItem.toNavItem() = TmNavItem(key = destination.route, label = title, icon = icon)

/** Goes to [route] without stacking copies of a screen that's already open */
fun NavHostController.navigateTo(route: String) {
    navigate(route) { launchSingleTop = true }
}
