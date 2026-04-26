package org.mr0btain.ticketman

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.mr0btain.ticketman.ui.screens.login.LoginPopup
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.navigation.BottomNavigationDrawer
import org.mr0btain.ticketman.ui.screens.navigation.Destination
import org.mr0btain.ticketman.ui.screens.navigation.NavigationHost
import org.mr0btain.ticketman.ui.screens.navigation.NavigationItem
import org.mr0btain.ticketman.ui.screens.navigation.SideAppNav
import org.mr0btain.ticketman.ui.theme.AppTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.monitor
import ticketman.composeapp.generated.resources.settings
import ticketman.composeapp.generated.resources.user

// TODO: Work on UI for mobile size screens

// TODO: Make option to import database data from file?

// TODO: Administration screen
// TODO: Kiosk/POS Screen
// TODO: Setup way to set local as server, or ask upon new creation so we can setup correct access to user IF its a local setup.
// TODO: All main data classes have the ability to have images of their own. We should set that up

// TODO: Add a way to load database data after view model init.

// TODO: Make a better way to refresh data in pager when item is deleted.

// TODO: Setup so when we load a ticket into the seed we get the customer and users names somehow.
// TODO: Fix search bars in dialogs for ticket and others if needed

// TODO: When canceling a change in dialogs,
//  we need to re pull the correct data to clear any changes we made

// TODO: When we hit confirm on a dialog make sure we check and dont hit database

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    KoinContext {
        // View Model
        val mainViewModel = koinViewModel<MainViewModel>()

        // For snack bar messages
        val snackBarHostState = mainViewModel.snackBarHost

        // Navigation
        val navigator = rememberNavController()

        // Main scope to use with coroutines
        val mainScope = rememberCoroutineScope()

        // Generate some mock data and add it to the database
        //mainViewModel.fillDatabaseWithTestData()

        // Current employee info
        //val currentEmployee = mainViewModel.dataHandler.userDataHandler.current.collectAsState()

        // Should we show login popup or not
        val shouldShowLogin = mainViewModel.showLogin.collectAsState()

        val initialDestination = Destination.MAIN

        val navDestination = listOf(
            NavigationItem(
                title = "Admin",
                icon = painterResource(Res.drawable.archive),
                destination = Destination.ADMIN
            ),
            NavigationItem(
                title = "Dashboard",
                icon = painterResource(Res.drawable.monitor),
                destination = Destination.DASHBOARD
            ),
            NavigationItem(
                title = "Login",
                icon = painterResource(Res.drawable.user),
                destination = Destination.LOGIN
            ),
            NavigationItem(
                title = "Main",
                icon = painterResource(Res.drawable.home),
                destination = Destination.MAIN
            ),
            NavigationItem(
                title = "Settings",
                icon = painterResource(Res.drawable.settings),
                destination = Destination.SETTINGS
            )
        )

        AppTheme(
            darkTheme = mainViewModel.darkMode.collectAsState(false).value
        ) {
            println("Platform: ${getPlatform().name}")

            if (shouldShowLogin.value) {
                LoginPopup(
                    modifier = Modifier.padding(24.dp),
                    viewModel = mainViewModel,
                    onDismiss = { mainViewModel.changeShowLogin(!shouldShowLogin.value) },
                    coroutineScope = mainScope
                )
            }

            // Check if were on desktop, if so we can avoid using window size class entirely.
            if (getPlatform().name.contains("Java")) {
                SideAppNav(
                    navHostController = navigator,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            coroutineScope = mainScope,
                            mainViewModel = mainViewModel,
                            snackBarState = snackBarHostState,
                            initialDestination = initialDestination
                        )
                    },
                    topAppText = "Welcome: ", //${currentEmployee.value.name.firstName + " " + currentEmployee.value.name.lastName}
                    coroutineScope = mainScope,
                    destinations = navDestination,
                    initialDestination = initialDestination
                )
            } else {
                // Calculating screen size if were on mobile. This should only update when the screen is rotated.
                val windowSizeClass = calculateWindowSizeClass()

                // Change our view dependent on screen size. Mostly to differentiate from tablet or phone screen
                if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                    // TODO: Test on actual tablet
                    // Tablet screen just use desktop sidebar
                    SideAppNav(
                        navHostController = navigator,
                        content = { paddingValues ->
                            NavigationHost(
                                modifier = Modifier.padding(paddingValues),
                                navHostController = navigator,
                                coroutineScope = mainScope,
                                mainViewModel = mainViewModel,
                                snackBarState = snackBarHostState,
                                initialDestination = initialDestination
                            )
                        },
                        topAppText = "Welcome: ", //${currentEmployee.value.name.firstName + " " + currentEmployee.value.name.lastName}
                        coroutineScope = mainScope,
                        destinations = navDestination,
                        initialDestination = initialDestination
                    )
                } else {
                    // Phone screen
                    BottomNavigationDrawer(
                        navController = navigator,
                        coroutineScope = mainScope,
                        destinations = navDestination,
                        content = { paddingValues ->
                            NavigationHost(
                                modifier = Modifier.padding(paddingValues),
                                coroutineScope = mainScope,
                                navHostController = navigator,
                                mainViewModel = mainViewModel,
                                snackBarState = snackBarHostState
                            )
                        }
                    )
                }
            }
        }
    }
}

// **
// To remove an item using viewmodel.remove you have to give it an identifier, and it must match selected/seed item.
// You cannot remove and item unless it is also selected.