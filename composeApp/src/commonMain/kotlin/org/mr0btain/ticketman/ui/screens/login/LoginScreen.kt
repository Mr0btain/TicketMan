package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.login_button_text
import ticketman.composeapp.generated.resources.login_text

/**
 * Login screen composable made for dialog but can be used either way
 */

// TODO: Implement logic for login

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onConfirm: (user:String, pass: String) -> Boolean,
    onDismiss: () -> Unit,
    isDarkMode: Boolean
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
                    // My Tickets
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

                    // All Tickets
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

            if (selectedTab == 0) {
                LoginField(
                    onConfirm = {},
                    onDismiss = {}
                )
            } else {
                TwoFactorField(isDarkMode = isDarkMode)
            }
        }
    }

}