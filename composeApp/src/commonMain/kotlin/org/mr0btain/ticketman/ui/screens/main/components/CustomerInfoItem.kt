package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.CustomerData
import org.mr0btain.ticketman.components.data.CustomerInfo
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.mail
import ticketman.composeapp.generated.resources.navigation_2
import ticketman.composeapp.generated.resources.phone
import ticketman.composeapp.generated.resources.user
import ticketman.composeapp.generated.resources.users

@Composable
fun CustomerInfoItem(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    selectedCustomerData: CustomerData
) {
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(2.dp)
        ) {
            Surface(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(36.dp),
                        painter = painterResource(Res.drawable.user),
                        contentDescription = "User Information"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = selectedCustomerData.info.name.firstName + " " + selectedCustomerData.info.name.lastName,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider()

            Surface(
                modifier = Modifier.padding(8.dp),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Info",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    AddressBlock(selectedCustomerData.info)

                    ContactBlock(selectedCustomerData.info)
                }
            }
        }
    }
}

// Address information block
@Composable
private fun AddressBlock(data: CustomerInfo) {
    val labelFont = 12.sp
    val textFont = 16.sp

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        Text(
            modifier = Modifier.padding(2.dp),
            text = "Address",
            fontWeight = FontWeight.SemiBold,
            fontSize = labelFont
        )

        Column(
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.Start
        ) {
            // Street
            Text(
                modifier = Modifier,
                text = data.address.street,
                fontSize = textFont
            )

            // City and state
            Text(
                modifier = Modifier,
                text = data.address.city + " " + data.address.state,
                fontSize = textFont
            )

            // Zip
            Text(
                modifier = Modifier,
                text = data.address.zip,
                fontSize = textFont
            )
        }
    }
}

// Contact intimation block
@Composable
private fun ContactBlock(data: CustomerInfo) {
    val labelFont = 12.sp
    val textFont = 16.sp
    val iconSize = 18.dp

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        Text(
            modifier = Modifier.padding(2.dp),
            text = "Contact",
            fontWeight = FontWeight.SemiBold,
            fontSize = labelFont
        )

        Column(
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.Start
        ) {
            // Email section
            if (data.contactInfo.email.isNotBlank()) {
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
                        text = data.contactInfo.email,
                        fontSize = textFont
                    )
                }
            }

            Spacer(Modifier.padding(2.dp))

            // Phone number section
            data.contactInfo.phone.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                         modifier = Modifier.size(iconSize),
                        painter = painterResource(Res.drawable.phone),
                        contentDescription = "Phone Number"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier,
                        text = item,
                        fontSize = textFont
                    )
                }
            }
        }
    }
}
