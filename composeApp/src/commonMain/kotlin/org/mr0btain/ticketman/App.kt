package org.mr0btain.ticketman

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinContext
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.mr0btain.ticketman.components.network.DatabaseClient
import org.mr0btain.ticketman.components.network.createHttpClient
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.navigation.BottomNavigationDrawer
import org.mr0btain.ticketman.ui.screens.navigation.DrawerNavigator
import org.mr0btain.ticketman.ui.screens.navigation.NavigationHost
import org.mr0btain.ticketman.ui.screens.navigation.NavigationItem
import org.mr0btain.ticketman.ui.screens.navigation.Screen
import org.mr0btain.ticketman.ui.screens.navigation.SideAppNav
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel
import org.mr0btain.ticketman.ui.theme.AppTheme
import org.mr0btain.ticketman.util.onError
import org.mr0btain.ticketman.util.onSuccess
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.settings
import ticketman.composeapp.generated.resources.user
import java.net.http.HttpClient

// TODO: Work on UI for mobile size screens
// TODO: Make option to import database data from file?
// TODO: Add remote database stuff
// TODO: Administration screen
// TODO: Inventory screen

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    KoinContext {
        // View Models
        val mainViewModel = koinViewModel<MainViewModel>()
        val settingsViewModel = koinViewModel<SettingsViewModel>()

        // Navigation
        val navigator = rememberNavController()

        // Main scope to use with coroutines
        val mainScope = rememberCoroutineScope()

        // For snack bar messages
        val snackBarHostState = mainViewModel.snackBarHost

        // Check and setup databases if they don't exist
        mainViewModel.setupDatabase()

        // Generate some mock data and add it to the database in memory
        //mainViewModel.generateMockDbData()

        // Pull data from local database
        //mainViewModel.updateDataFromDb()

        // Pull settings from datastore
        settingsViewModel.getSettingsFromPreferences()

        // TODO: Make this into something that can be used in production
        //mainViewModel.changeServerAddress(settingsViewModel.serverAddress.value)

        // Current employee info
        val currentEmployee = mainViewModel.employee.collectAsState()

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
            darkTheme = settingsViewModel.darkMode.collectAsState(false).value
        ) {
            println("Platform: ${mainViewModel.platform}")

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
                            settingsViewModel = settingsViewModel,
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
                                settingsViewModel = settingsViewModel,
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
                                settingsViewModel = settingsViewModel,
                                snackBarState = snackBarHostState
                            )
                        }
                    )
                }
            }
        }
    }
}