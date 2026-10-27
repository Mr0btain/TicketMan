package org.mr0btain.ticketman

import androidx.compose.runtime.getValue
import org.mr0btain.ticketman.ui.screens.navigation.ShellLocations
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.mr0btain.ticketman.ui.kit.data.LocalCurrencySymbol
import org.mr0btain.ticketman.ui.kit.data.LocalStorageTerms
import org.mr0btain.ticketman.ui.kit.data.LocalTaxes
import org.mr0btain.ticketman.ui.kit.data.TmTaxes
import org.mr0btain.ticketman.ui.kit.feedback.LocalTicketStatuses
import org.mr0btain.ticketman.ui.kit.feedback.LocalLocations
import org.mr0btain.ticketman.ui.kit.feedback.TmLocations
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
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.update.UpdateRequiredScreen
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
import ticketman.composeapp.generated.resources.truck
import ticketman.composeapp.generated.resources.calendar

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

    // Said once each time it connects to a server a minor version away, and kept on Settings > Account
    val versionNotice = mainViewModel.session.versionNotice.collectAsState()
    LaunchedEffect(versionNotice.value) {
        versionNotice.value?.takeIf { !it.blocking }?.let { snackBarHostState.showSnackbar(it.message) }
    }

    // A server a major version away can't be used at all, so the update screen takes the whole app
    val updateRequired = mainViewModel.session.updateRequired.collectAsState()
    val loginState = mainViewModel.session.loginState.collectAsState()

    // Same for a server on plain HTTP, also said on the login popup and Settings > Account
    val insecureWarning = mainViewModel.session.insecureWarning.collectAsState()
    LaunchedEffect(insecureWarning.value) {
        insecureWarning.value?.let { snackBarHostState.showSnackbar(it) }
    }

    // Approved stations with kiosk mode show only the counter, signed into by PIN
    val kioskActive = mainViewModel.kiosk.active.collectAsState()

    val initialDestination = Destination.RECORDS

    // Admin screen is only listed for admins
    val isAdmin = mainViewModel.session.isAdmin.collectAsState()
    val currentUser = mainViewModel.session.currentUser.collectAsState()
    val clockedIn = mainViewModel.timeClock.current.collectAsState()

    val isDebug = remember { BuildConfigImpl().isDebug() }

    // Logging in happens from the bottom of the nav rail, so the old login screen isn't listed
    val navDestination = listOfNotNull(
        NavigationItem("Records", Res.drawable.home, Destination.RECORDS),
        NavigationItem("Counter", Res.drawable.monitor, Destination.COUNTER),
        NavigationItem("Purchasing", Res.drawable.truck, Destination.PURCHASING),
        NavigationItem("Calendar", Res.drawable.calendar, Destination.APPOINTMENTS),
        NavigationItem("Settings", Res.drawable.settings, Destination.SETTINGS),
        if (isAdmin.value) NavigationItem("Admin", Res.drawable.archive, Destination.ADMIN) else null,
        // Component gallery is for working on the ui kit, so only listed in debug builds
        if (isDebug) NavigationItem("Components", Res.drawable.layers, Destination.GALLERY) else null
    )

    // Photos load through the app's http client so they carry the login, and are cached in memory and on disk
    setSingletonImageLoaderFactory { context -> mainViewModel.imageClient.newImageLoader(context) }

    // Photos anywhere below can upload and load through the server.
    // Amounts anywhere below show the shop's currency, and ticket statuses the shop's own names
    val shop = mainViewModel.counter.shop.collectAsState().value
    // Which location records are at, named on them when it's not where this device works
    val allLocations = mainViewModel.locations.all.collectAsState().value
    val workingLocation = mainViewModel.locations.working.collectAsState().value
    CompositionLocalProvider(
        LocalLocations provides TmLocations(allLocations, workingLocation.identifier.takeIf { !workingLocation.all }),
        LocalImageClient provides mainViewModel.imageClient,
        LocalCurrencySymbol provides shop.currencySymbol,
        LocalTaxes provides TmTaxes(shop.taxBasisPoints, shop.taxRates),
        LocalStorageTerms provides shop.storage,
        LocalTicketStatuses provides shop.statuses
    ) {
        TmTheme(
            palette = mainViewModel.settings.palette.collectAsState().value,
            font = mainViewModel.settings.font.collectAsState().value,
            darkTheme = mainViewModel.settings.darkMode.collectAsState().value,
            reduceTransparency = mainViewModel.settings.reduceTransparency.collectAsState().value
        ) {
            updateRequired.value?.let { notice ->
                UpdateRequiredScreen(
                    notice = notice,
                    retrying = loginState.value == LoginState.LOADING,
                    onRetry = { mainViewModel.session.retryConnection() },
                    // A kiosk has no login popup to type another address into
                    onChangeServer = if (kioskActive.value) null else {
                        {
                            mainViewModel.session.dismissUpdateRequired()
                            mainViewModel.session.changeShowLogin(true)
                        }
                    }
                )
                return@TmTheme
            }

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

            // Where this device works, offered in the user menu once the shop has more than one location
            val locations = mainViewModel.locations
            val severalLocations by locations.several.collectAsState()
            val activeLocations by locations.active.collectAsState()
            val working by locations.working.collectAsState()
            val fixedLocation by locations.fixed.collectAsState()
            val isAdmin by mainViewModel.session.isAdmin.collectAsState()
            val shellLocations = if (!severalLocations) null else ShellLocations(
                options = activeLocations,
                working = working,
                canSeeAll = isAdmin,
                fixed = fixedLocation,
                label = if (working.all) "All locations" else activeLocations.firstOrNull { it.identifier == working.identifier }?.name.orEmpty(),
                onChoose = { locations.choose(it) }
            )

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
                    onOpenSettings = { navigator.navigateTo(Destination.SETTINGS.route) },
                    clockedInAt = clockedIn.value?.clockedInAt,
                    onPunch = { mainViewModel.timeClock.punch() },
                    locations = shellLocations
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