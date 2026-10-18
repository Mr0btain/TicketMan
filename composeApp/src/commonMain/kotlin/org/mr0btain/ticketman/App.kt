package org.mr0btain.ticketman

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import org.mr0btain.ticketman.components.debug.BuildConfigImpl
import org.koin.compose.viewmodel.koinViewModel
import org.mr0btain.ticketman.ui.screens.kiosk.KioskShell
import org.mr0btain.ticketman.ui.screens.login.AccountSetupDialog
import org.mr0btain.ticketman.ui.screens.login.LoginPopup
import org.mr0btain.ticketman.ui.screens.dashboard.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.navigation.PhoneAppShell
import org.mr0btain.ticketman.ui.screens.navigation.WideAppShell
import org.mr0btain.ticketman.ui.screens.navigation.navigateTo
import org.mr0btain.ticketman.ui.screens.navigation.Destination
import org.mr0btain.ticketman.ui.screens.navigation.NavigationHost
import org.mr0btain.ticketman.ui.screens.navigation.NavigationItem
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.layers
import ticketman.composeapp.generated.resources.monitor
import ticketman.composeapp.generated.resources.settings

// TODO: Make option to import database data from file?
// TODO: All main data classes have the ability to have images of their own. We should set that up

// the focus upon opening dialog.
// TODO: Update name in dialogs and such to handle middle names

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    // View Model
    val mainViewModel = koinViewModel<MainViewModel>()

    // For snack bar messages
    val snackBarHostState = mainViewModel.snackBarHost

    // Navigation
    val navigator = rememberNavController()

    // Main scope to use with coroutines
    val mainScope = rememberCoroutineScope()

    // Should we show login popup or not
    val shouldShowLogin = mainViewModel.showLogin.collectAsState()

    // First login setup that has to be finished
    val accountSetup = mainViewModel.accountSetup.collectAsState()

    // Approved stations with kiosk mode show only the counter, signed into by PIN
    val kioskActive = mainViewModel.kiosk.active.collectAsState()

    val initialDestination = Destination.DASHBOARD

    // Admin screen is only listed for admins
    val isAdmin = mainViewModel.isAdmin.collectAsState()
    val currentUser = mainViewModel.currentUser.collectAsState()

    val isDebug = remember { BuildConfigImpl().isDebug() }

    // Logging in happens from the bottom of the nav rail, so the old login screen isn't listed
    val navDestination = listOfNotNull(
        NavigationItem("Dashboard", Res.drawable.home, Destination.DASHBOARD),
        NavigationItem("Counter", Res.drawable.monitor, Destination.COUNTER),
        NavigationItem("Settings", Res.drawable.settings, Destination.SETTINGS),
        if (isAdmin.value) NavigationItem("Admin", Res.drawable.archive, Destination.ADMIN) else null,
        // Component gallery is for working on the ui kit, so only listed in debug builds
        if (isDebug) NavigationItem("Components", Res.drawable.layers, Destination.GALLERY) else null
    )

    TmTheme(
        palette = mainViewModel.palette.collectAsState().value,
        font = mainViewModel.font.collectAsState().value,
        darkTheme = mainViewModel.darkMode.collectAsState().value,
        reduceTransparency = mainViewModel.reduceTransparency.collectAsState().value
    ) {
        println("Platform: ${getPlatform().name}")

        if (kioskActive.value) {
            KioskShell(mainViewModel)
            return@TmTheme
        }

        accountSetup.value?.let { setup ->
            AccountSetupDialog(
                setup = setup,
                onFinish = { name, email, phone, password, pin, onResult ->
                    mainViewModel.completeAccountSetup(name, email, phone, password, pin, onResult)
                },
                onCancel = { mainViewModel.cancelAccountSetup() }
            )
        }

        if (shouldShowLogin.value) {
            LoginPopup(
                viewModel = mainViewModel,
                onDismiss = { mainViewModel.changeShowLogin(false) }
            )
        }

        val host: @Composable () -> Unit = {
            NavigationHost(
                navHostController = navigator,
                coroutineScope = mainScope,
                mainViewModel = mainViewModel,
                snackBarState = snackBarHostState,
                initialDestination = initialDestination
            )
        }

        // Desktop always gets the rail, phones and tablets go by window width
        val wide = getPlatform().name.contains("Java") ||
                calculateWindowSizeClass().widthSizeClass == WindowWidthSizeClass.Expanded

        if (wide) {
            WideAppShell(
                navHostController = navigator,
                destinations = navDestination,
                snackBarState = snackBarHostState,
                user = currentUser.value,
                onLogin = { mainViewModel.changeShowLogin(true) },
                onLogout = { mainViewModel.logout() },
                onOpenSettings = { navigator.navigateTo(Destination.SETTINGS.route) }
            ) { host() }
        } else {
            PhoneAppShell(
                navHostController = navigator,
                destinations = navDestination,
                snackBarState = snackBarHostState
            ) { host() }
        }
    }
}

// **
// To remove an item using viewmodel.remove you have to give it an identifier, and it must match selected/seed item.
// You cannot remove and item unless it is also selected.