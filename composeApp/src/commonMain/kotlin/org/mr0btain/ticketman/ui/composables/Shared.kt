package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.mail
import ticketman.composeapp.generated.resources.phone
import ticketman.composeapp.generated.resources.x

// This holds things used frequently

@Composable
fun CustomSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    color: Color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.secondaryContainer
else MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 4.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content
    )
}

@Composable
fun ConfirmCancelButtons(
    modifier: Modifier = Modifier,
    onCancelClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onCancelClick,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text("Cancel")
        }

        Spacer(Modifier.padding(8.dp))

        Button(
            onClick = onConfirmClick,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Confirm")
        }
    }
}

@Composable
fun AddOrDeleteButtonRow(
    modifier: Modifier = Modifier,
    label: String = "Item",
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Delete Item Button
        Button(
            onClick = onDeleteClick,
            //shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors().copy(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(text = "Delete $label")
        }

        Spacer(Modifier.padding(16.dp))

        // Add Item Button
        Button(
            onClick = onAddClick,
            //shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Add $label")
        }
    }
}

@Composable
fun DialogNoteList(
    modifier: Modifier = Modifier,
    notes: List<Note>,
    onDeleteClick: (Int) -> Unit
) {
    Column {
        notes.forEachIndexed { index, item ->
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${item.author}:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.padding(2.dp))

                    Text(
                        text = item.message,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.padding(2.dp))

                Box(
                    modifier = Modifier
                        .size(InputChipDefaults.AvatarSize + 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .clickable(
                            onClick = { onDeleteClick(index) }
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
        }
    }
}

@Composable
fun DialogTextRow(
    modifier: Modifier = Modifier,
    firstLabel: String,
    firstValue: String,
    onFirstValueChange: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    onSecondValueChange: (String) -> Unit
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(0.5f),
            label = {
                Text(firstLabel)
            },
            value = firstValue,
            onValueChange = onFirstValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )

        Spacer(Modifier.padding(8.dp))

        OutlinedTextField(
            modifier = Modifier.weight(0.5f),
            label = {
                Text(secondLabel)
            },
            value = secondValue,
            onValueChange = onSecondValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )
    }
}

@Composable
fun TableStatusItem(
    modifier: Modifier = Modifier,
    status: TicketStatus,
    textModifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
) {
    Surface(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(4.dp),
        color = when (status.status) {
            TicketStatusType.IN_PROGRESS -> CustomMaterialColors.inProgress
            TicketStatusType.ON_HOLD -> MaterialTheme.colorScheme.error
            TicketStatusType.DONE -> CustomMaterialColors.success
        }
    ) {
        Text(
            modifier = textModifier.padding(start = 6.dp, end = 6.dp),
            text = status.status.name.replace("_", " "),
            fontWeight = textStyle.fontWeight,
            fontSize = textStyle.fontSize,
            fontFamily = textStyle.fontFamily,
            textAlign = textStyle.textAlign,
            color = textStyle.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TablePriorityItem(
    modifier: Modifier = Modifier,
    priority: TicketPriority,
    textModifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
) {
    Surface(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(4.dp),
        color = when (priority.priority) {
            TicketPriorityType.LOW -> MaterialTheme.colorScheme.secondary
            TicketPriorityType.NORMAL -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        }
    ) {
        Text(
            modifier = textModifier.padding(start = 6.dp, end = 6.dp),
            text = priority.priority.name.replace("_", " "),
            fontWeight = textStyle.fontWeight,
            fontSize = textStyle.fontSize,
            fontFamily = textStyle.fontFamily,
            textAlign = textStyle.textAlign,
            color = textStyle.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TableTicketIdItem(
    modifier: Modifier = Modifier,
    label: String,
    textStyle: TextStyle = TextStyle.Default
) {
    Surface(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp),
            text = label,
            fontWeight = textStyle.fontWeight,
            fontSize = textStyle.fontSize,
            fontFamily = textStyle.fontFamily,
            textAlign = textStyle.textAlign,
            color = textStyle.color,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TableCardTextItem(
    text: String
) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        overflow = TextOverflow.Ellipsis,
        maxLines = 3
    )
}

// ----------------------------- Contacts for customer ----------------------------------
@Composable
fun ContactsBlock(
    phoneList: List<String>,
    emailList: List<String>,
    labelSize: TextUnit = 12.sp,
    textSize: TextUnit = 16.sp,
    iconSize: Dp = 18.dp
) {
    // ----------- Contact info: Email/Phone ---------------
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        Text(
            modifier = Modifier
                .padding(2.dp),
            text = "Contact",
            fontWeight = FontWeight.SemiBold,
            fontSize = labelSize
        )

        Column(
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.Start
        ) {
            // Email list
            emailList.forEach { mail ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(iconSize),
                        painter = painterResource(Res.drawable.mail),
                        contentDescription = "Email"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier,
                        text = mail,
                        fontSize = textSize
                    )
                }
            }

            Spacer(Modifier.padding(2.dp))

            // Phone number section
            phoneList.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .size(iconSize),
                        painter = painterResource(Res.drawable.phone),
                        contentDescription = "Phone Number"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier,
                        text = item,
                        fontSize = textSize
                    )
                }
            }
        }
    }
}

// Simple Text Item
@Composable
fun SimpleTextItem(
    label: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.padding(4.dp))

        Text(
            text = text,
            fontSize = 16.sp
        )
    }
}

@Composable
fun TableDivider() {
    Row(
        modifier = Modifier.width(22.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        VerticalDivider(
            modifier = Modifier.height(40.dp),
            color = MaterialTheme.colorScheme.primary,
            thickness = 2.dp
        )
    }
}

@Composable
fun PaneHeader(
    text: String,
    icon: DrawableResource,
    contentDescription: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(icon),
            contentDescription = contentDescription
        )

        Spacer(Modifier.padding(4.dp))

        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    HorizontalDivider()
}