package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.ui.screens.login.components.LoginField
import org.mr0btain.ticketman.ui.screens.login.components.TwoFactorField
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

/**
 * Login screen composable made for dialog but can be used either way
 */

// TODO: Implement logic for login

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onConfirm: (user:String, pass: String) -> Boolean,
    onDismiss: () -> Unit,
    isDarkMode: Boolean,
    snackBarState: SnackbarHostState
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Surface {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = modifier.fillMaxWidth().wrapContentHeight(),
                horizontalArrangement = Arrangement.Center
            ) {
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .wrapContentWidth(),
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.wrapContentWidth(),
                        text = {
                            Text(
                                text = "Login",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )

                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.wrapContentWidth(),
                        text = {
                            Text(
                                text = "Two Factor OTP",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.padding(12.dp))

            Button(
                onClick = {
                    viewModel.viewModelScope.launch {
                        //iewModel.dataHandler.syncData()
                    }
                }
            ) {
                Text("Sync Server")
            }

            if (selectedTab == 0) {
                LoginField(
                    onConfirm = { user, pass -> onConfirm(user, pass) },
                    onDismiss = {},
                    error = false
                )
            } else {
                TwoFactorField(isDarkMode = isDarkMode)
            }
        }
    }

}