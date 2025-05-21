package components.data

data class CustomerData(
    val info: CustomerInfo,
    val tickets: Map<Int, TicketInfo>,
    val notes: List<Note>
)