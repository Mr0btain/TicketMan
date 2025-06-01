package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.Note
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.components.TicketPriority
import org.mr0btain.ticketman.components.data.components.TicketStatus
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tag
import ticketman.composeapp.generated.resources.x

// This holds things used between both panes

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
                        text = item.text,
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
    firstValue: MutableState<String>,
    secondLabel: String,
    secondValue: MutableState<String>
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
            value = firstValue.value,
            onValueChange = { firstValue.value = it },
            singleLine = true
        )

        Spacer(Modifier.padding(8.dp))

        OutlinedTextField(
            modifier = Modifier.weight(0.5f),
            label = {
                Text(secondLabel)
            },
            value = secondValue.value,
            onValueChange = { secondValue.value = it },
            singleLine = true
        )
    }
}

@Composable
fun TableStatusItem(
    modifier: Modifier = Modifier,
    status: TicketStatus,
    textModifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary),
    darkMode: Boolean
) {
   Surface(
       modifier = modifier.padding(4.dp),
       shape = RoundedCornerShape(4.dp),
       color = when (status) {
           TicketStatus.IN_PROGRESS -> if (darkMode) extendedDark.inProgress.color else extendedLight.inProgress.color
           TicketStatus.ON_HOLD -> MaterialTheme.colorScheme.error
           TicketStatus.DONE -> if (darkMode) extendedDark.onSuccess.color else extendedLight.onSuccess.color
       }
   ) {
       Text(
           modifier = textModifier.padding(start = 6.dp, end = 6.dp),
           text = status.name,
           fontWeight = textStyle.fontWeight,
           fontSize = textStyle.fontSize,
           fontFamily = textStyle.fontFamily,
           textAlign = textStyle.textAlign,
           color = textStyle.color,
           maxLines = 3,
           overflow = TextOverflow.Ellipsis
       )
   }
}

@Composable
fun TablePriorityItem(
    modifier: Modifier = Modifier,
    priority: TicketPriority,
    textModifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary),
    darkMode: Boolean
) {
    Surface(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(4.dp),
        color = when (priority) {
            TicketPriority.LOW -> MaterialTheme.colorScheme.secondary
            TicketPriority.NORMAL -> MaterialTheme.colorScheme.primary
            TicketPriority.HIGH -> if (darkMode) extendedDark.inProgress.color else extendedLight.inProgress.color
            TicketPriority.URGENT -> MaterialTheme.colorScheme.error
        }
    ) {
        Text(
            modifier = textModifier.padding(start = 6.dp, end = 6.dp),
            text = priority.name,
            fontWeight = textStyle.fontWeight,
            fontSize = textStyle.fontSize,
            fontFamily = textStyle.fontFamily,
            textAlign = textStyle.textAlign,
            color = textStyle.color,
            maxLines = 3,
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
            maxLines = 3,
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

@Composable
fun IconText(
    modifier: Modifier = Modifier,
    label: String,
    textStyle: TextStyle = TextStyle.Default,
    leadingIcon: @Composable (RowScope.() -> Unit) = {},
    trailingIcon: @Composable (RowScope.() -> Unit) = {}
) {
    Row(
        modifier = modifier.padding(start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Our leading icon if any
        leadingIcon()

        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = label,
                fontWeight = textStyle.fontWeight,
                fontSize = textStyle.fontSize,
                fontStyle = textStyle.fontStyle,
                color = textStyle.color,
                textAlign = textStyle.textAlign,
                fontFamily = textStyle.fontFamily,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        trailingIcon()
    }
}

@Composable
fun SmallTicketList(
    modifier: Modifier = Modifier,
    tickets: List<TicketInfo>,
    isDarkMode: Boolean,
    onClick: (String) -> Unit
) {
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Main Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(Res.drawable.tag),
                    contentDescription = "Ticket List"
                )

                Spacer(Modifier.padding(4.dp))

                Text(
                    text = "Tickets",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .padding(4.dp)
            ) {
                items(tickets.reversed()) { ticket ->
                    Surface(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable(
                                onClick = { onClick(ticket.identifier) }
                            ),
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 4.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = ticket.identifier,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                TableStatusItem(
                                    status = ticket.status,
                                    darkMode = isDarkMode
                                )
                            }

                            HorizontalDivider()

                            // Device details
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ticket.device.manufacturer,
                                    fontSize = 14.sp
                                )

                                Spacer(Modifier.padding(2.dp))

                                Text(
                                    text = ticket.device.model,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(Modifier.padding(4.dp))

                            // Notes section
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ticket.updates.take(3).forEach { note ->
                                    Row(
                                        modifier = Modifier.padding(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = note.author + ": ",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        Text(
                                            text = note.text,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}