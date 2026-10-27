package org.mr0btain.ticketman.ui.screens.navigation

import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import ticketman.composeapp.generated.resources.map
import ticketman.composeapp.generated.resources.map_pin
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.ui.screens.locations.components.WorkingLocation
import org.mr0btain.ticketman.data.location.Location
import androidx.compose.foundation.clickable
import org.mr0btain.ticketman.ui.kit.images.TmAvatar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.ui.kit.navigation.TmNavItem
import org.mr0btain.ticketman.ui.kit.navigation.TmNavRail
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.clock
import ticketman.composeapp.generated.resources.log_in
import ticketman.composeapp.generated.resources.log_out
import ticketman.composeapp.generated.resources.settings
import ticketman.composeapp.generated.resources.sidebar
import ticketman.composeapp.generated.resources.user

/** Height of the phone bottom bar plus its margin, screens pad their bottom by this */
private val BOTTOM_NAV_SPACE = 80.dp

/**
 * The status and navigation bars and any camera cutout. The backdrop runs under them, everything else stays out.
 * Only Android has any; the keyboard isn't included, so the bottom bar stays behind it
 */
val shellInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * Wide screen layout: glass nav rail on the left, the current screen beside it
 * @param user Signed in user shown at the bottom of the rail, null shows a log in button
 * @param clockedInAt When they clocked in, null when they're off the clock
 * @param onPunch Clocks them in or out, from the user menu
 * @param locations Where the device works, picked in the user menu. Null with only one location
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
    clockedInAt: Long? = null,
    onPunch: (() -> Unit)? = null,
    locations: ShellLocations? = null,
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
                .windowInsetsPadding(shellInsets)
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
                        UserMenu(user = user, expanded = expanded, clockedInAt = clockedInAt, onPunch = onPunch, onOpenSettings = onOpenSettings, onLogout = onLogout, locations = locations)
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

        TmSnackbarHost(state = snackBarState, modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(shellInsets))
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
                .windowInsetsPadding(shellInsets)
                .padding(bottom = BOTTOM_NAV_SPACE),
            content = content
        )

        TmSnackbarHost(
            state = snackBarState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(shellInsets)
                .padding(bottom = BOTTOM_NAV_SPACE)
        )

        TmBottomNav(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(shellInsets)
                .padding(TmTheme.spacing.sm),
            items = items,
            selectedKey = selectedRoute,
            onSelect = { navHostController.navigateTo(it.key) }
        )
    }
}

/**
 * Where the device works, for the user menu
 * @param options The locations that can be picked
 * @param canSeeAll Offers every location at once, for admins
 * @param fixed A station, which stays at its own location
 * @param label The location being worked at, as shown
 * @param onChoose Picks a location, null for every one
 */
data class ShellLocations(
    val options: List<Location>,
    val working: WorkingLocation,
    val canSeeAll: Boolean,
    val fixed: Boolean,
    val label: String,
    val onChoose: (Uuid?) -> Unit
)

/** The signed in user at the bottom of the rail, clicking it offers where to work, settings and logging out */
@Composable
private fun UserMenu(
    user: User,
    expanded: Boolean,
    clockedInAt: Long?,
    onPunch: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    locations: ShellLocations? = null
) {
    var open by remember { mutableStateOf(false) }
    val name = user.name.trim().ifBlank { user.username }

    Box {
        if (expanded) {
            TmListRow(
                title = name,
                subtitle = locations?.label?.ifBlank { null } ?: user.username,
                leading = { TmAvatar(name = name, photo = user.photo, size = 28.dp) },
                onClick = { open = true }
            )
        } else {
            TmAvatar(
                name = name,
                photo = user.photo,
                modifier = Modifier.clickable(onClickLabel = "Signed in as $name, open the menu") { open = true },
                size = 36.dp
            )
        }

        TmMenu(expanded = open, onDismissRequest = { open = false }) {
            // Where this device works. A station stays where it is
            locations?.let { shell ->
                if (shell.fixed) {
                    TmMenuItem(text = "At ${shell.label}", leadingIcon = Res.drawable.map_pin, enabled = false, onClick = {})
                } else {
                    shell.options.forEach { location ->
                        TmMenuItem(
                            text = location.name,
                            leadingIcon = Res.drawable.map_pin,
                            trailingText = location.code.ifBlank { null },
                            selected = !shell.working.all && shell.working.identifier == location.identifier,
                            onClick = {
                                open = false
                                shell.onChoose(location.identifier)
                            }
                        )
                    }
                    if (shell.canSeeAll) {
                        TmMenuItem(text = "All locations", leadingIcon = Res.drawable.map, selected = shell.working.all, onClick = {
                            open = false
                            shell.onChoose(null)
                        })
                    }
                }
                TmDivider()
            }
            onPunch?.let { punch ->
                TmMenuItem(
                    text = if (clockedInAt != null) "Clock out" else "Clock in",
                    leadingIcon = Res.drawable.clock,
                    trailingText = clockedInAt?.let { "since ${formatTime(it)}" },
                    onClick = {
                        open = false
                        punch()
                    }
                )
            }
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
