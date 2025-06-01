package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
fun DrawerNav(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
    scope: CoroutineScope,
    navHostController: NavHostController,
    topAppTextState: MutableState<String>
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
                            scope.launch {
                                // Navigate to screen
                                navHostController.navigate(NavigationItem.Main.route)

                                // Set top app bar text
                                topAppTextState.value = "Main"

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
                            scope.launch {
                                navHostController.navigate(NavigationItem.Admin.route)

                                // Set top app bar text
                                topAppTextState.value = "Admin"

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
                            scope.launch {
                                // Navigate to screen
                                navHostController.navigate(NavigationItem.Settings.route)

                                // Set top app bar text
                                topAppTextState.value = "Settings"

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
                            scope.launch {
                                navHostController.navigate(NavigationItem.Login.route)

                                // Set top app bar text
                                topAppTextState.value = "Login"

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
                    title = { Text(topAppTextState.value) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
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