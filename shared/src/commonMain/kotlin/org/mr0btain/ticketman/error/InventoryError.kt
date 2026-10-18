package org.mr0btain.ticketman.error

enum class InventoryError(override val message: String): Error {
    NOT_FOUND("Inventory item not found!")
}