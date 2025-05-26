package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TableStatusItem(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    label: String,
    textStyle: TextStyle
) {
    // .border(1.5.dp, Color.Gray, RoundedCornerShape(10.dp))
   Surface(
       modifier = modifier,
       shape = RoundedCornerShape(4.dp),
       color = backgroundColor
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