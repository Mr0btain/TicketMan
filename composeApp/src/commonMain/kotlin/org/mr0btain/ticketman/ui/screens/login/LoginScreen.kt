package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.composables.loading.CircularLoading
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.login.components.LoginField
import org.mr0btain.ticketman.ui.screens.login.components.TwoFactorField
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

/**
 * Login screen composable made for dialog but can be used either way
 */

// Debug screen for viewing the login form outside the popup, the popup is the real login

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onConfirm: (user:String, pass: String) -> Unit,
    snackBarState: SnackbarHostState
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val loginState by viewModel.loginState.collectAsState()

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

            Text(
                text = "State: ${loginState.name}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (selectedTab == 0) {
                if (loginState == LoginState.LOADING) {
                    CircularLoading(Modifier.padding(48.dp))
                } else {
                    LoginField(
                        onConfirm = { user, pass -> onConfirm(user, pass) },
                        initialUsername = viewModel.savedUsername,
                        error = loginState == LoginState.FAILED
                    )
                }
            } else {
                TwoFactorField()
            }
        }
    }

}