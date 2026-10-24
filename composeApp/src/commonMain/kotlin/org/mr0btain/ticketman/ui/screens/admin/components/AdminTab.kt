package org.mr0btain.ticketman.ui.screens.admin.components

/** The admin screen's tabs. Each loads its own data when it's opened, see [AdminHandler.refresh] */
enum class AdminTab(val label: String) {
    OVERVIEW("Overview"),
    USERS("Users"),
    SESSIONS("Sessions"),
    STATIONS("Stations"),
    LOW_STOCK("Low stock"),
    INVOICES("Invoices"),
    MESSAGES("Messages"),
    SERVER("Server and data"),
    AUDIT("Audit log")
}
