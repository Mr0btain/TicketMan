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
import org.mr0btain.ticketman.ui.screens.navigation.NavigationHost
import org.mr0btain.ticketman.ui.screens.navigation.NavigationItem
import org.mr0btain.ticketman.ui.screens.navigation.Screen
import org.mr0btain.ticketman.ui.screens.navigation.SideAppNav
import org.mr0btain.ticketman.ui.theme.AppTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.settings
import ticketman.composeapp.generated.resources.user

// TODO: Work on UI for mobile size screens
// TODO: Make option to import database data from file?
// TODO: Add remote database stuff
// TODO: Administration screen

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    KoinContext {
        // TODO: Maybe combine these? don't really need both of them
        // View Models
        val mainViewModel = koinViewModel<MainViewModel>()

        // For snack bar messages
        val snackBarHostState = mainViewModel.snackBarHost

        // Navigation
        val navigator = rememberNavController()

        // Main scope to use with coroutines
        val mainScope = rememberCoroutineScope()

        // Generate some mock data and add it to the database in memory
        //mainViewModel.generateMockDbData()

        // Current employee info
        val currentEmployee = mainViewModel.dataHandler.userDataHandler.current.collectAsState()

        // Should we show login popup or not
        val shouldShowLogin = mainViewModel.showLogin.collectAsState()

        val navDestinations = listOf(
            NavigationItem(
                title = "Home",
                icon = painterResource(Res.drawable.home),
                route = Screen.Main.route
            ),
            NavigationItem(
                title = "Settings",
                icon = painterResource(Res.drawable.settings),
                route = Screen.Settings.route
            ),
            NavigationItem(
                title = "Admin",
                icon = painterResource(Res.drawable.archive),
                route = Screen.Admin.route
            ),
            NavigationItem(
                title = "Login",
                icon = painterResource(Res.drawable.user),
                route = Screen.Login.route
            )
        )

        // TODO: I don't think using window size class is the best way to go for differentiating between desktop and mobile as it
        // causes us to end up hitting the database everytime screen size class changes in desktop. We should move the window size
        // class state down the tree to avoid it recomposing the entire app when we resize the screen on desktop

        AppTheme(
            darkTheme = mainViewModel.darkMode.collectAsState(false).value
        ) {
            println("Platform: ${mainViewModel.platform}")

            if (shouldShowLogin.value) {
                LoginPopup(
                    modifier = Modifier.padding(24.dp),
                    viewModel = mainViewModel,
                    onDismiss = { mainViewModel.updateShowLogin(!shouldShowLogin.value) },
                    coroutineScope = mainScope
                )
            }

            // Check if were on desktop
            if (mainViewModel.platform.contains("Java")) {
                SideAppNav(
                    navHostController = navigator,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            coroutineScope = mainScope,
                            mainViewModel = mainViewModel,
                            snackBarState = snackBarHostState
                        )
                    },
                    topAppText = "Welcome: ${currentEmployee.value.name.firstName + " " + currentEmployee.value.name.lastName}",
                    coroutineScope = mainScope,
                    destinations = navDestinations
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
                                snackBarState = snackBarHostState
                            )
                        },
                        topAppText = "Welcome: ${currentEmployee.value.name.firstName + " " + currentEmployee.value.name.lastName}",
                        coroutineScope = mainScope,
                        destinations = navDestinations
                    )
                } else {
                    // Phone screen
                    // TODO: Test on actual phone screen
                    BottomNavigationDrawer(
                        navController = navigator,
                        coroutineScope = mainScope,
                        destinations = navDestinations,
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