package org.mr0btain.ticketman.components.helpers

import org.mr0btain.ticketman.components.data.components.CustomerData

fun CustomerData.check(new: CustomerData): Boolean {
    return when {
        this.info.id != new.info.id -> {
            println("Info Not Match!")
            println(this.info.id)
            println(new.info.id)
            false
        }

        this.info.contactInfo.email != new.info.contactInfo.email -> {
            println("Email Not Match!")
            println(this.info.contactInfo.email)
            println(new.info.contactInfo.email)
            false
        }

        this.info.contactInfo.phone != new.info.contactInfo.phone -> {
            println("Phone Not Match!")
            println(this.info.contactInfo.phone)
            println(new.info.contactInfo.phone)
            false
        }

        this.info.address != new.info.address -> {
            println("Address Not Match!")
            println(this.info.address)
            println(new.info.address)
            false
        }

        this.info.name != new.info.name -> {
            println("Name Not Match!")
            println(this.info.name)
            println(new.info.name)
            false
        }

        this.notes != new.notes -> {
            println("Notes Not Match!")
            println(this.notes)
            println(new.notes)
            false
        }

        else -> true
    }
}