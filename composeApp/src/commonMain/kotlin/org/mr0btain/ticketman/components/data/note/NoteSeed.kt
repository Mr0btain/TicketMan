package org.mr0btain.ticketman.components.data.note

import org.mr0btain.ticketman.components.data.DataSeed

class NoteSeed: DataSeed() {
    private var _author: String = ""
    private var _message: String = ""

    val author: String get() = _author
    val message: String get() = _message


    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun markClean() {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        TODO("Not yet implemented")
    }
}