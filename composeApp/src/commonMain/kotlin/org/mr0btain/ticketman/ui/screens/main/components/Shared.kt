package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.time.formatTime
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight
import ticketman.composeapp.generated.resources.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// This holds things used between all panes

@Composable
fun ConfirmOrCancelButtonRow(
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
    textStyle: TextStyle = TextStyle.Default.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary),
    darkMode: Boolean
) {
   Surface(
       modifier = modifier.padding(4.dp),
       shape = RoundedCornerShape(4.dp),
       color = when (status.status) {
           TicketStatusType.IN_PROGRESS -> if (darkMode) extendedDark.inProgress.color else extendedLight.inProgress.color
           TicketStatusType.ON_HOLD -> MaterialTheme.colorScheme.error
           TicketStatusType.DONE -> if (darkMode) extendedDark.onSuccess.color else extendedLight.onSuccess.color
       }
   ) {
       Text(
           modifier = textModifier.padding(start = 6.dp, end = 6.dp),
           text = status.status.name,
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
        color = when (priority.priority) {
            TicketPriorityType.LOW -> MaterialTheme.colorScheme.secondary
            TicketPriorityType.NORMAL -> MaterialTheme.colorScheme.primary
            TicketPriorityType.HIGH -> if (darkMode) extendedDark.inProgress.color else extendedLight.inProgress.color
            TicketPriorityType.URGENT -> MaterialTheme.colorScheme.error
        }
    ) {
        Text(
            modifier = textModifier.padding(start = 6.dp, end = 6.dp),
            text = priority.priority.name,
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

// TODO: Add time to this
@OptIn(ExperimentalUuidApi::class)
@Composable
fun SmallTicketList(
    modifier: Modifier = Modifier,
    ticketData: List<TicketData>?,
    scrollBarsOnRight: Boolean,
    isDarkMode: Boolean,
    onClick: (Uuid) -> Unit
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
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
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

            ticketData?.let {
                PlatformColumnScrollable(
                    modifier = Modifier.padding(4.dp),
                    scrollBarOnRight = scrollBarsOnRight,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    isVisible = ticketData.isNotEmpty()
                ) {
                    ticketData.reversed().forEach { data ->
                        Surface(
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable(
                                    onClick = { onClick(data.ticket.identifier) }
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
                                        text = data.ticket.device.toNameString(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    TableStatusItem(
                                        status = data.ticket.status,
                                        darkMode = isDarkMode
                                    )
                                }

                                Spacer(Modifier.padding(4.dp))

                                // Notes section
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // We only want a tidbit of notes test from this data, we can get the
                                    // images and such when we click on it for more info
                                    data.notes.take(3).forEach { (note, _) ->
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
                                                text = note.message,
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
}

// ----------------------------- Contacts for customer ----------------------------------
@Composable
fun ContactsBlock(
    customer: Customer,
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
            // Email section
            if (customer.contactInfo.email.isNotEmpty()) {
                customer.contactInfo.email.forEach { mail ->
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
            }

            Spacer(Modifier.padding(2.dp))

            // Phone number section
            customer.contactInfo.phone.forEach { item ->
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
fun NotesBlock(
    modifier: Modifier = Modifier,
    label: String = "",
    noteData: List<NoteData>,
    onImageClick: (List<ImageData>) -> Unit = {},
    editable: Boolean = false,
    labelSize: TextUnit = 12.sp,
    textSize: TextUnit = 16.sp,
    maxImageSize: DpSize = DpSize(300.dp, 200.dp)
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        if (label.isNotBlank()) {
            Text(
                modifier = Modifier
                    .padding(2.dp),
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = labelSize
            )

            Spacer(Modifier.padding(2.dp))
        }

        noteData.sortedByDescending { it.note.time }.forEach { data ->
            data.note.let { note ->
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Author
                        Text(
                            text = note.author + ": ",
                            fontSize = textSize,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Time
                        Text(
                            text = formatTime(note.time).toString(),
                            fontSize = (textSize.value - 4).sp,
                            maxLines = 1
                        )
                    }

                    // Text
                    Text(
                        modifier = Modifier,
                        text = note.message,
                        fontSize = textSize
                    )

                    if (data.images.isNotEmpty()) {
                        Spacer(Modifier.padding(4.dp))

                        ImagePager(
                            modifier = Modifier
                                .fillMaxWidth(),
                            imageData = data.images,
                            onImageClick = { onImageClick(data.images) },
                            imageMaxWidth = maxImageSize.width,
                            imageMaxHeight = maxImageSize.height
                        )

                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}