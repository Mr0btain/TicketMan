package org.mr0btain.ticketman.components.data.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.components.data.components.ContactInfo
import org.mr0btain.ticketman.components.data.components.Notes
import org.mr0btain.ticketman.components.data.components.TicketList

// TODO: Maybe encrypt whole data and just leave first last and identifier unencrypted

object CustomerDataTable : IntIdTable("customer") {
    val identifier = integer("number")

    val firstName = text("firstname")
    val lastName = text("lastname")
    val street = text("street")
    val city = text("city")
    val state = text("state")
    val zip = text("zip")
    val tickets = json<TicketList>("tickets", Json.Default)
    val notes = json<Notes>("notes", Json.Default)
    val contactInfo = json<ContactInfo>("contact", Json.Default)
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