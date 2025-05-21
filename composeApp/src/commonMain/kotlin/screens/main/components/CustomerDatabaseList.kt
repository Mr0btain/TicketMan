package screens.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import components.data.CustomerInfo

@Composable
fun CustomerDatabaseList(
    modifier: Modifier = Modifier,
    customerInfoList: List<CustomerInfo>
) {
    Surface(
        modifier = modifier
    ) {
        LazyColumn{
            items(customerInfoList) { data ->
                Card {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(data.name)

                        Spacer(Modifier.padding(4.dp))

                        Text(data.address)

                        Spacer(Modifier.padding(4.dp))

                        Text(data.mostRecentTicket)
                    }
                }
            }
        }
    }
}