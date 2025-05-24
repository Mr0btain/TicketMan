package org.mr0btain.ticketman

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.mr0btain.ticketman.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.navigation.DrawerNav
import org.mr0btain.ticketman.ui.screens.navigation.NavigationHost
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel
import ui.theme.AppTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    KoinContext {
        // View Models
        val mainViewModel = koinViewModel<MainViewModel>()
        val settingsViewModel = koinViewModel<SettingsViewModel>()

        // Navigation
        val navigator = rememberNavController()

        // Calculating screen size stuff
        val windowSizeClass = calculateWindowSizeClass()

        // Main scope to use with coroutines
        val mainScope = rememberCoroutineScope()

        //

        AppTheme(
            darkTheme = settingsViewModel.isDarkMode.collectAsState().value
        ) {
            if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                DrawerNav(
                    navHostController = navigator,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    },
                    scope = mainScope,
                    topAppTextState = mainViewModel.topAppBarText
                )
            } else {
                DrawerNav(
                    navHostController = navigator,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    },
                    scope = mainScope,
                    topAppTextState = mainViewModel.topAppBarText
                )
            }
        }
    }
}

/*
val screenSize = remember { mutableStateOf(Pair(-1, -1)) }
    Layout(
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                Text("Screen size: ${screenSize.value.first}x${screenSize.value.second}px", modifier = Modifier.align(Alignment.Center))
            }
        },
        measurePolicy = { measurables, constraints ->
            // Use the max width and height from the constraints
            val width = constraints.maxWidth
            val height = constraints.maxHeight

            screenSize.value = Pair(width, height)
            println("Width: $width, height: $height")

            // Measure and place children composables
            val placeables = measurables.map { measurable ->
                measurable.measure(constraints)
            }

            layout(width, height) {
                var yPosition = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x = 0, y = yPosition)
                    yPosition += placeable.height
                }
            }
        }
    )
 */