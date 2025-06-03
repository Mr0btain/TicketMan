package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerNavigator(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
    coroutineScope: CoroutineScope,
    navHostController: NavHostController,
    topAppText: String
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text("Main", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)

                    // Main Screen
                    NavigationDrawerItem(
                        label = { Text("Main") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                // Navigate to screen
                                navHostController.navigate(NavigationItem.Main.route)

                                // Close drawer
                                drawerState.close()
                            }
                        }
                    )

                    // Admin Screen
                    NavigationDrawerItem(
                        label = { Text("Admin") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                navHostController.navigate(NavigationItem.Admin.route)

                                // Close drawer
                                drawerState.close()
                            }
                        }
                    )

                    HorizontalDivider()

                    Text("Options", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)

                    // Settings screen
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                // Navigate to screen
                                navHostController.navigate(NavigationItem.Settings.route)

                                // Close drawer
                                drawerState.close()
                            }
                        }
                    )

                    HorizontalDivider()

                    Text("Testing", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)

                    // TODO: THis is a temp build of it
                    // Login Screen
                    NavigationDrawerItem(
                        label = { Text("Login") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                navHostController.navigate(NavigationItem.Login.route)

                                // Close drawer
                                drawerState.close()
                            }
                        }
                    )
                }
            }
        },
        drawerState = drawerState
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(topAppText) },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                if (drawerState.isClosed) {
                                    drawerState.open()
                                } else {
                                    drawerState.close()
                                }
                            }
                        }) {
                            Icon(
                                modifier = Modifier.size(26.dp),
                                painter = painterResource(Res.drawable.menu),
                                contentDescription = "Menu"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}