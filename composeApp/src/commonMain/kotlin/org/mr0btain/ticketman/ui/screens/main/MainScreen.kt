package org.mr0btain.ticketman.ui.screens.main

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.getPlatform
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.expanded.ExpandedMainScreen
import org.mr0btain.ticketman.ui.screens.main.mobile.MobileMainScreen

/**
 * The main landing screen
 *
 */

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    scrollBarsOnBottom: Boolean,
    scrollBarsOnRight: Boolean,
    snackBarState: SnackbarHostState
) {
    val platform = getPlatform().name

    // Check if were on Desktop or mobile
    if (platform.contains("Java")) {
        ExpandedMainScreen(
            viewModel = viewModel,
            coroutineScope = coroutineScope,
            snackBarState = snackBarState
        )
    } else {
        // On Mobile well go off screen size class and adjust content dependent on that
        val windowSizeClass = calculateWindowSizeClass()

        // We only really care if were expanded or on a tablet essentially.
        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
            ExpandedMainScreen(
                viewModel = viewModel,
                coroutineScope = coroutineScope,
                snackBarState = snackBarState
            )
        } else {
            MobileMainScreen(
                viewModel = viewModel,
                coroutineScope = coroutineScope,
                scrollBarsOnBottom = scrollBarsOnBottom,
                scrollBarsOnRight = scrollBarsOnRight,
                snackBarState = snackBarState
            )
        }
    }
}