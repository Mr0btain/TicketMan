package org.mr0btain.ticketman.components.database

/** What came of saving a record that may have changed since whoever's saving it opened it */
enum class RecordSave {
    DONE,
    NOT_FOUND,

    /** Someone saved it since, so this would have overwritten what they did */
    CHANGED_SINCE
}
