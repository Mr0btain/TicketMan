package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.data.shared.MainItemType
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight

@Composable
fun ConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    dataType: DataType,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    isDarkMode: Boolean = false,
    fontSize: TextUnit = 18.sp
) {
    Dialog(
        onDismissRequest =  { onDismiss() }
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
                        text = "Are you sure you want to remove: ",
                        fontSize = fontSize
                    )

                    Spacer(Modifier.padding(2.dp))

                    Text(
                        text = "This ${dataType.stringName}",
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider()

                Spacer(Modifier.padding(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { onDismiss() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onContinue() },
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