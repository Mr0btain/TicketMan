package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.crypt.Algorithms
import org.jetbrains.exposed.v1.crypt.encryptedVarchar

object SettingsTable: IntIdTable("settings") {
    val identifier = text("identifier")
    val isDarkMode = bool("darkMode")
    val serverUrl = text("serverUrl")
    val autoSync = bool("autoSync")
    val scrollBarsOnBottom = bool("scrollBarsOnBottom")
    val scrollBarsOnRight = bool("scrollBarsOnRight")
    val userName = varchar("username", 100)
    val password = encryptedVarchar(
        name = "password",
        cipherTextLength = 1024,
        encryptor = Algorithms.AES_256_PBE_CBC(
            password = "testPassWord",
            salt = "123456"
        )
    )
    val accessToken = encryptedVarchar(
        name = "accessToken",
        cipherTextLength = 2048,
        encryptor = Algorithms.AES_256_PBE_CBC(
            password = "testPassWord",
            salt = "123456"
        )
    )
    val refreshToken = encryptedVarchar(
        name = "refreshToken",
        cipherTextLength = 2048,
        encryptor = Algorithms.AES_256_PBE_CBC(
            password ="testPassWord",
            salt = "123456"
        )
    )
}