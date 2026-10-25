package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.inbox
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.shopping_cart
import ticketman.composeapp.generated.resources.tag
import ticketman.composeapp.generated.resources.user_plus
import kotlin.uuid.Uuid

/** Narrower than this, the queue moves into a dialog and the top bar's buttons lose their labels */
private val SIDE_QUEUE_WIDTH = 900.dp

/**
 * How the counter is laid out: the search box and buttons along the top, [content] under them and the queue beside
 * it. On narrow windows the buttons are icons, and the queue opens in a dialog from a button of its own. [content]
 * stays put when the window crosses that width, so a half done check-in isn't lost
 * @param searchField The search box, given the modifier that sizes it
 * @param canEdit False drops the check-in, new customer and sale buttons and shows a "View only" badge
 */
@Composable
fun CounterLayout(
    searchField: @Composable (Modifier) -> Unit,
    canEdit: Boolean,
    queue: CounterQueueResponse?,
    onCheckIn: () -> Unit,
    onNewCustomer: () -> Unit,
    onSell: () -> Unit,
    onPriceCheck: () -> Unit,
    onQueueTicketClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val spacing = TmTheme.spacing
    var showQueue by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < SIDE_QUEUE_WIDTH
        val stacked = maxWidth < STACKED_SEARCH_WIDTH

        if (compact && showQueue) {
            QueueDialog(
                queue = queue,
                onTicketClick = {
                    showQueue = false
                    onQueueTicketClick(it)
                },
                onDismissRequest = { showQueue = false }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            // ------------------------------------------ Top bar ------------------------------------------
            // Icon buttons beside the search when the queue can't sit beside the screen
            val iconButtons: @Composable RowScope.() -> Unit = {
                if (canEdit) {
                    TmIconButton(
                        icon = Res.drawable.plus,
                        contentDescription = "Check in",
                        onClick = onCheckIn,
                        variant = TmButtonVariant.PRIMARY,
                        size = TmControlSize.LARGE
                    )
                    TmIconButton(
                        icon = Res.drawable.user_plus,
                        contentDescription = "New customer",
                        onClick = onNewCustomer,
                        variant = TmButtonVariant.SECONDARY,
                        size = TmControlSize.LARGE
                    )
                    TmIconButton(
                        icon = Res.drawable.shopping_cart,
                        contentDescription = "Sell",
                        onClick = onSell,
                        variant = TmButtonVariant.SECONDARY,
                        size = TmControlSize.LARGE
                    )
                }

                TmIconButton(
                    icon = Res.drawable.tag,
                    contentDescription = "Price check",
                    onClick = onPriceCheck,
                    variant = TmButtonVariant.SECONDARY,
                    size = TmControlSize.LARGE
                )
                TmIconButton(
                    icon = Res.drawable.inbox,
                    contentDescription = "Queue",
                    onClick = { showQueue = true },
                    variant = TmButtonVariant.SECONDARY,
                    size = TmControlSize.LARGE
                )
            }

            // On a phone the search gets its own line, so there's room to type
            if (stacked) {
                searchField(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                    content = iconButtons
                )
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                searchField(Modifier.weight(1f))

                if (compact) {
                    iconButtons()
                } else {
                    if (canEdit) {
                        TmButton(text = "Check in${keyHint("F3")}", onClick = onCheckIn, size = TmControlSize.LARGE, leadingIcon = Res.drawable.plus)
                        TmButton(
                            text = "New customer",
                            onClick = onNewCustomer,
                            variant = TmButtonVariant.SECONDARY,
                            size = TmControlSize.LARGE,
                            leadingIcon = Res.drawable.user_plus
                        )
                        TmButton(
                            text = "Sell${keyHint("F6")}",
                            onClick = onSell,
                            variant = TmButtonVariant.SECONDARY,
                            size = TmControlSize.LARGE,
                            leadingIcon = Res.drawable.shopping_cart
                        )
                    }

                    TmButton(
                        text = "Price check${keyHint("F4")}",
                        onClick = onPriceCheck,
                        variant = TmButtonVariant.SECONDARY,
                        size = TmControlSize.LARGE,
                        leadingIcon = Res.drawable.tag
                    )
                }

                if (!canEdit) TmBadge(text = "View only", tone = TmTheme.colors.neutral)
            }

            // ------------------------------------------ Main area and queue ------------------------------------------
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    content()
                }

                if (!compact) {
                    QueuePanel(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        queue = queue,
                        onTicketClick = onQueueTicketClick
                    )
                }
            }
        }
    }
}

/** Narrower than this, the search goes above the buttons rather than beside them */
private val STACKED_SEARCH_WIDTH = 560.dp
