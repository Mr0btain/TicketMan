package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight

@Composable
fun SimpleConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    itemName: String,
    onDismissRequest: (Boolean) -> Unit,
    isDarkMode: Boolean
) {
    val fontSize = 18.sp

    Dialog(
        onDismissRequest =  { onDismissRequest(false) }
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Are you sure you want to DELETE: ",
                        fontSize = fontSize
                    )

                    Spacer(Modifier.padding(2.dp))

                    Text(
                        text = itemName,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.padding(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { onDismissRequest(false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onDismissRequest(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkMode) extendedDark.onSuccess.color else extendedLight.onSuccess.color
                        )
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}