package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TableCardTextItem(
    text: String
) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        trailingIcon()
    }
}