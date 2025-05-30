package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.data.components.SubItemType
import org.mr0btain.ticketman.components.data.components.getLabelTextForType
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight

// TODO: Make this look way better right now its pretty ugly
@Composable
fun ConfirmDeleteTwoStepDialog(
    modifier: Modifier = Modifier,
    itemName: String,
    itemType: SubItemType,
    onDismissRequest: (Boolean) -> Unit,
    isDarkMode: Boolean
) {
    val confirmed = remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = { onDismissRequest(false) },
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getLabelTextForType(itemType),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.padding(4.dp))

                Text(
                    text = itemName,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.padding(4.dp))

                Text(
                    text = "If this is just a ticket assigned to an employee " +
                            "or a customer it will NOT delete the ticket only remove " +
                            "it from this customer. To delete the ticket or customer you'll " +
                            "have to go to the actual ticket or customer and delete it that way",
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(modifier = Modifier.width(400.dp))

                if (confirmed.intValue == 0) {
                    FirstScreen(
                        isDarkMode = isDarkMode,
                        onConfirm = { confirmed.intValue++ },
                        onDeny = { onDismissRequest(false) }
                    )
                } else if (confirmed.intValue == 1) {
                    SecondScreen(
                        isDarkMode = isDarkMode,
                        onConfirm = { onDismissRequest(true) },
                        onDeny = { onDismissRequest(false) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstScreen(
    isDarkMode: Boolean,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        Modifier.padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Are you sure you want to delete this?",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.padding(8.dp))

        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onDeny,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkMode) extendedDark.onSuccess.color else extendedLight.onSuccess.color
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("NO GO BACK")
            }

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm")
            }
        }
    }
}

@Composable
private fun SecondScreen(
    isDarkMode: Boolean,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        Modifier.padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "THIS IS WILL BE GONE FOREVER!!!",
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.padding(8.dp))

        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onDeny,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkMode) extendedDark.onSuccess.color else extendedLight.onSuccess.color
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("NO GO BACK")
            }

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm")
            }
        }
    }
}