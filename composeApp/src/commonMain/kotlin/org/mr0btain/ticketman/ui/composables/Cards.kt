package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Cards and placeholders shared by the admin and counter screens

/** Card with a title for grouping things on a screen */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    CustomSurface(
        modifier = modifier.padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically, content = action)
            }

            Spacer(Modifier.padding(4.dp))
            HorizontalDivider()
            Spacer(Modifier.padding(4.dp))

            content()
        }
    }
}

/** Shown in place of a list when there's nothing in it */
@Composable
fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 14.sp)
    }
}
