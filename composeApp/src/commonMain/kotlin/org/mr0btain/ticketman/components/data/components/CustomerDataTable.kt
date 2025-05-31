package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.crypt.Algorithms
import org.jetbrains.exposed.v1.crypt.encryptedVarchar
import org.jetbrains.exposed.v1.json.json

// TODO: Find way to encrypt sensitive data. maybe convert data class to json string then store as varchar encrypted and decode to class manually

object CustomerDataTable : IntIdTable("customer") {
    val idNumber = integer("number")

    val firstName = text("firstname")
    val lastName = text("lastname")
    val street = text("street")
    val city = text("city")
    val state = text("state")
    val zip = text("zip")
    val tickets = json<TicketList>("tickets", Json)
    val notes = json<Notes>("notes", Json)
    val contactInfo = json<ContactInfo>("contact", Json)
}



/*
val columEncrypted = encryptedVarchar(
        name = "colum_encrypted",
        cipherTextLength = 1024,
        encryptor = Algorithms.AES_256_PBE_GCM("password", "salt"
        )
    ).nullable()

object ATable : Table() {
    val id = integer("id").autoIncrement()
    val columEncrypted = encryptedVarchar(
        name = "colum_encrypted",
        cipherTextLength = 1024,
        Algorithms.AES_256_PBE_GCM("password", "salt")
    ).nullable()

    override val primaryKey = PrimaryKey(id)
}
 */