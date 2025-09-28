package org.mr0btain.ticketman.ui.composables.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.x

@Composable
fun CustomInputChip(
    label: String,
    onClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    InputChip(
        label = { Text(label) },
        onClick = { onClick(label)},
        selected = false,
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize + 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(
                        onClick = { onDeleteClick(label) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    painter = painterResource(Res.drawable.x),
                    contentDescription = "Remove Item",
                )
            }
        }
    )
}