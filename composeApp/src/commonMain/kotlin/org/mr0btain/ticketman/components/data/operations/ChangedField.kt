package org.mr0btain.ticketman.components.data.operations

// I am not a very big fan of doing this all in one single enum. I'd like to have separate specific
// enums for each variation but I cant think of a solid wy to do this yet
enum class ChangedField {
    NOTE,
    TICKET,
    NAME,
    DESCRIPTION,
    STOCK,
    PRICE,
    COST,
    IMAGE,
    CUSTOMER,
    USER,
    STREET,
    CITY,
    STATE,
    ZIP,
    EMAIL,
    PHONE,
    TIME,
    IDENTIFIER,
    DEVICE,
    STATUS,
    PRIORITY,
    AUTHOR,
    MESSAGE,
    USERNAME,
    PASSWORD,
    ACCESS_LEVEL
}