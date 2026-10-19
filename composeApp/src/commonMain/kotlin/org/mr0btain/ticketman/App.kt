package org.mr0btain.ticketman

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.mr0btain.ticketman.ui.kit.data.LocalCurrencySymbol
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import org.mr0btain.ticketman.components.debug.BuildConfigImpl
import coil3.compose.setSingletonImageLoaderFactory
import org.koin.compose.viewmodel.koinViewModel
import org.mr0btain.ticketman.components.image.LocalImageClient
import org.mr0btain.ticketman.ui.screens.kiosk.KioskShell
import org.mr0btain.ticketman.ui.screens.login.AccountSetupDialog
import org.mr0btain.ticketman.ui.screens.login.LoginPopup
import org.mr0btain.ticketman.ui.screens.login.ServerTrustDialog
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.screens.navigation.PhoneAppShell
import org.mr0btain.ticketman.ui.screens.navigation.WideAppShell
import org.mr0btain.ticketman.ui.screens.navigation.navigateTo
import org.mr0btain.ticketman.ui.screens.navigation.Destination
import org.mr0btain.ticketman.ui.screens.navigation.NavigationHost
import org.mr0btain.ticketman.ui.screens.navigation.NavigationItem
import org.mr0btain.ticketman.ui.screens.navigation.alwaysWide
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.layers
import ticketman.composeapp.generated.resources.monitor
import ticketman.composeapp.generated.resources.settings

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    // View Model
    val mainViewModel = koinViewModel<MainViewModel>()

    // For snack bar messages
    val snackBarHostState = mainViewModel.snackBarHost

    // Navigation
    val navigator = rememberNavController()

    // Should we show login popup or not
    val shouldShowLogin = mainViewModel.session.showLogin.collectAsState()

    // First login setup that has to be finished
    val accountSetup = mainViewModel.session.accountSetup.collectAsState()

    // A server with its own certificate has to be checked before this device talks to it
    val untrustedCertificate = mainViewModel.session.untrustedCertificate.collectAsState()

    // Said once each time it connects to a server from another version, and kept on Settings > Account
    val apiMismatch = mainViewModel.session.apiMismatch.collectAsState()
    LaunchedEffect(apiMismatch.value) {
        apiMismatch.value?.let { snackBarHostState.showSnackbar(it) }
    }

    // Approved stations with kiosk mode show only the counter, signed into by PIN
    val kioskActive = mainViewModel.kiosk.active.collectAsState()

    val initialDestination = Destination.RECORDS

    // Admin screen is only listed for admins
    val isAdmin = mainViewModel.session.isAdmin.collectAsState()
    val currentUser = mainViewModel.session.currentUser.collectAsState()

    val isDebug = remember { BuildConfigImpl().isDebug() }

    // Logging in happens from the bottom of the nav rail, so the old login screen isn't listed
    val navDestination = listOfNotNull(
        NavigationItem("Records", Res.drawable.home, Destination.RECORDS),
        NavigationItem("Counter", Res.drawable.monitor, Destination.COUNTER),
        NavigationItem("Settings", Res.drawable.settings, Destination.SETTINGS),
        if (isAdmin.value) NavigationItem("Admin", Res.drawable.archive, Destination.ADMIN) else null,
        // Component gallery is for working on the ui kit, so only listed in debug builds
        if (isDebug) NavigationItem("Components", Res.drawable.layers, Destination.GALLERY) else null
    )

    // Photos load through the app's http client so they carry the login, and are cached in memory and on disk
    setSingletonImageLoaderFactory { context -> mainViewModel.imageClient.newImageLoader(context) }

    // Photos anywhere below can upload and load through the server.
    // Amounts anywhere below show the shop's currency
    CompositionLocalProvider(
        LocalImageClient provides mainViewModel.imageClient,
        LocalCurrencySymbol provides mainViewModel.counter.shop.collectAsState().value.currencySymbol
    ) {
        TmTheme(
            palette = mainViewModel.settings.palette.collectAsState().value,
            font = mainViewModel.settings.font.collectAsState().value,
            darkTheme = mainViewModel.settings.darkMode.collectAsState().value,
            reduceTransparency = mainViewModel.settings.reduceTransparency.collectAsState().value
        ) {
            if (kioskActive.value) {
                KioskShell(mainViewModel)
                return@TmTheme
            }

            accountSetup.value?.let { setup ->
                AccountSetupDialog(
                    setup = setup,
                    onFinish = { name, email, phone, password, pin, onResult ->
                        mainViewModel.session.completeAccountSetup(name, email, phone, password, pin, onResult)
                    },
                    onCancel = { mainViewModel.session.cancelAccountSetup() }
                )
            }

            if (shouldShowLogin.value) {
                LoginPopup(
                    viewModel = mainViewModel,
                    onDismiss = { mainViewModel.session.changeShowLogin(false) }
                )
            }

            // Over the login popup, which can't get anywhere until the server is trusted
            untrustedCertificate.value?.let { certificate ->
                ServerTrustDialog(
                    certificate = certificate,
                    onTrust = { mainViewModel.session.trustServerCertificate() },
                    onDismiss = { mainViewModel.session.dismissServerCertificate() }
                )
            }

            val host: @Composable () -> Unit = {
                NavigationHost(
                    navHostController = navigator,
                    mainViewModel = mainViewModel,
                    initialDestination = initialDestination
                )
            }

            // Desktop always gets the rail, phones and tablets go by window width
            val wide = alwaysWide ||
                    calculateWindowSizeClass().widthSizeClass == WindowWidthSizeClass.Expanded

            if (wide) {
                WideAppShell(
                    navHostController = navigator,
                    destinations = navDestination,
                    snackBarState = snackBarHostState,
                    user = currentUser.value,
                    onLogin = { mainViewModel.session.changeShowLogin(true) },
                    onLogout = { mainViewModel.session.logout() },
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
}

// **
// To remove an item using viewmodel.remove you have to give it an identifier, and it must match selected/seed item.
// You cannot remove and item unless it is also selected.