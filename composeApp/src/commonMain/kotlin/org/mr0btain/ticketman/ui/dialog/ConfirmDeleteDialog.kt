package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.ui.composables.ConfirmOrCancelButtonRow

@Composable
fun ConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    dataType: DataType,
    isTwoStep: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val confirmCount = remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = { onCancel() },
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                Modifier.padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DELETE",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.padding(4.dp))

                Text(
                    text = "Are you sure you want to delete this ${dataType.stringName}",
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(modifier = Modifier.width(400.dp))

                if (confirmCount.intValue == 0) {
                    ConfirmOrCancelButtonRow(
                        onConfirmClick = {
                            if (isTwoStep) confirmCount.intValue == 1
                        },
                        onCancelClick = onCancel
                    )
                } else if (confirmCount.intValue == 1) {
                    Text(
                        text = "ARE YOU REALLY REALLY FOR SURE??!!",
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    ConfirmOrCancelButtonRow(
                        onConfirmClick = onConfirm,
                        onCancelClick = onCancel
                    )
                }
            }
        }
    }
}