package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.customer.TicketList
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.database.tables.CustomerDataTable
import org.mr0btain.ticketman.database.tables.DataTable
import org.mr0btain.ticketman.database.tables.InventoryTable
import org.mr0btain.ticketman.database.tables.TicketDataTable
import org.mr0btain.ticketman.database.tables.UserTable

