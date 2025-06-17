package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BottomNavigationDrawer(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
    coroutineScope: CoroutineScope,
    destinations: List<NavigationItem>,
    navController: NavHostController
) {
    val selectedNavigationIndex = rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedNavigationIndex.intValue == index,
                        onClick = {
                            coroutineScope.launch {
                                selectedNavigationIndex.intValue = index
                                navController.navigate(item.route)
                            }
                        },
                        icon = {
                            Icon(
                                modifier = Modifier.size(36.dp),
                                painter = item.icon,
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(item.title)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.surface,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}