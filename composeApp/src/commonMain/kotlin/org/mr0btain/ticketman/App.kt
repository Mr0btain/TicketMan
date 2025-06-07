package org.mr0btain.ticketman

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.navigation.DrawerNavigator
import org.mr0btain.ticketman.ui.screens.navigation.NavigationHost
import org.mr0btain.ticketman.ui.screens.navigation.SideAppNav
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel
import org.mr0btain.ticketman.ui.theme.AppTheme

// TODO: Work on UI for mobile size screens

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
        mainViewModel.updateDataFromDb()

        // Get our stored settings
        settingsViewModel.getUserSettings()

        // Current employee info
        val currentEmployee = mainViewModel.employee.collectAsState()

        // TODO: I don't think using window size class is the best way to go for differentiating between desktop and mobile as it
        // causes us to end up hitting the database everytime screen size class changes in desktop. We should move the window size
        // class state down the tree to avoid it recomposing the entire app when we resize the screen on desktop

        AppTheme(
            darkTheme = settingsViewModel.isDarkMode.collectAsState().value
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
                    coroutineScope = mainScope
                )
            } else {
                // TODO: Test this
                // Calculating screen size if were on mobile. This should only update when the screen is rotated.
                val windowSizeClass = calculateWindowSizeClass()

                // Change our view dependent on screen size. Mostly to differentiate from tablet or phone screen
                if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                    // TODO: Test and adjust or make custom ui for this
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
                        coroutineScope = mainScope
                    )
                } else {
                    // TODO: Make ui specifically for this
                    // Phone screen
                    DrawerNavigator(
                        navHostController = navigator,
                        content = { paddingValues ->
                            NavigationHost(
                                modifier = Modifier.padding(paddingValues),
                                coroutineScope = mainScope,
                                navHostController = navigator,
                                mainViewModel = mainViewModel,
                                settingsViewModel = settingsViewModel,
                                snackBarState = snackBarHostState
                            )
                        },
                        coroutineScope = mainScope,
                        topAppText = "Welcome: ${currentEmployee.value.name.firstName + " " + currentEmployee.value.name.lastName}",
                        snackBarHostState = snackBarHostState
                    )
                }
            }
        }
    }
}