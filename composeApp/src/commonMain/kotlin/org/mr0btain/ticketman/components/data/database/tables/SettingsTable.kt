package org.mr0btain.ticketman.components.data.database.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.crypt.Algorithms
import org.jetbrains.exposed.v1.crypt.encryptedVarchar

object SettingsTable: IntIdTable("settings") {
    val darkMode = bool("darkMode")
    val verticalScrollRight = bool("scrollBarOnRight")
    val horizontalScrollTop = bool("scrollBarOnTop")
    val serverAddress = text("server")
        // encryptedVarchar("serverAddress", 1024, Algorithms.AES_256_PBE_GCM("password", "salt")).nullable()
}