package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.components.data.components.Address
import org.mr0btain.ticketman.components.data.components.ContactInfo
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.CustomerInfo
import org.mr0btain.ticketman.components.data.components.CustomerName
import org.mr0btain.ticketman.components.data.components.Device
import org.mr0btain.ticketman.components.data.components.Note
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.components.randomTicketPriority
import org.mr0btain.ticketman.components.data.components.randomTicketStatus
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun generateMockCustomerData(num: Int): List<CustomerData> {
    val data = mutableListOf<CustomerData>()
    val ids = mutableListOf<Int>()

    (0..num).forEach { i ->
        var id = Random.nextInt(100, 99999)

        while (ids.contains(id)) {
            id = Random.nextInt(100, 99999)
        }

        ids.add(id)

        val name = generateRandomName()
        val notes = mutableListOf<Note>()

        // Generate notes
        (0..Random.nextInt(0, 4)).forEach { _ ->
            notes.add(generateCustomerNote())
        }

        data.add(
            CustomerData(
                info = CustomerInfo(
                    id = id.toString(),
                    name = name,
                    address = generateRandomAddress(),
                    contactInfo = generateRandomContactInfo(name.firstName, name.lastName)
                ),
                notes = notes
            )
        )
    }

    return data
}

fun generateMockData(num: Int): Pair<List<CustomerData>, List<TicketInfo>> {
    // Generate some mock customer data
    val customerData = generateMockCustomerData(num)

    // Hold the new data
    val newCustomerData = mutableListOf<CustomerData>()
    val result = mutableListOf<TicketInfo>()

    customerData.forEach { item ->
        val tickets = mutableListOf<TicketInfo>()
        val ticketIds = mutableListOf<String>()

        // Generate tickets
        (0 .. Random.nextInt(1, 10)).forEach { i ->
            tickets.add(generateRandomTicket(item.info.id))
        }

        // Make a list of ticket ids
        tickets.forEach { tick ->
            ticketIds.add(tick.idNum)
        }

        // Update the customer data
        newCustomerData.add(item.copy(tickets = ticketIds))

        // Add the tickets to the result
        result.addAll(tickets)
    }

    return Pair(newCustomerData, result)
}

private fun generateRandomAddress(): Address {
    val streets = listOf(
        "Main", "Oak Ave", "Maple", "Pine", "Elm", "Cedar", "Willow", "Dad", "Mom", "Peach", "Apple",
        "Johnson", "Poplar", "Cherry", "Dodson", "Gold", "Slop", "Beekeeper", "Papa"
    )
    val cities = listOf(
        "Springfield", "Riverside", "Greenville", "Fairview", "Madison", "Georgetown",
        "Arlington", "Bedford", "Altoona", "Saxton", "Cherryville", "Bradford", "Lexington",
        )
    val streetEnd = listOf("Rd", "Ct", "St", "Ln", "Ave", "Way")
    val states = listOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
        "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
        "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
        "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
        "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"
    )

    val streetNumber = Random.nextInt(100, 9999)
    val zip = Random.nextInt(10000, 99999).toString().padStart(5, '0')

    return Address(
        street = "$streetNumber ${streets.random()} ${streetEnd.random()}",
        city = cities.random(),
        state = states.random(),
        zip = zip
    )
}

private fun generateRandomName(): CustomerName {
    val firstNames = listOf(
        "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
        "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
        "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Nancy", "Daniel", "Lisa",
        "Matthew", "Betty", "Anthony", "Margaret", "Mark", "Sandra", "Donald", "Ashley",
        "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna", "Joshua", "Michelle",
        "Kenneth", "Carol", "Kevin", "Amanda", "Brian", "Dorothy", "George", "Melissa",
        "Edward", "Deborah", "Ronald", "Stephanie", "Timothy", "Rebecca"
    )

    val lastNames = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
        "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
        "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young",
        "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
        "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell",
        "Carter", "Roberts"
    )

    return CustomerName(firstNames.random(), lastNames.random())
}

@OptIn(ExperimentalUuidApi::class)
fun generateRandomTicket(customerId: String): TicketInfo {
    val manufacturersAndModels = mapOf(
        "Apple" to listOf("iPhone 13", "iPhone 14 Pro", "iPad Pro", "MacBook Air", "MacBook Pro"),
        "Samsung" to listOf("Galaxy S21", "Galaxy Tab S8", "Galaxy Note 20", "Galaxy Book3"),
        "Dell" to listOf("XPS 13", "XPS 15", "Inspiron 15", "Latitude 7420"),
        "HP" to listOf("Spectre x360", "Envy 13", "Pavilion 15", "EliteBook 840"),
        "Lenovo" to listOf("ThinkPad X1", "Yoga 9i", "Legion 5", "IdeaPad 3"),
        "Google" to listOf("Pixel 6", "Pixel 7 Pro", "Pixel Slate")
    )

    val noteMessages = listOf(
        "Screen is cracked.",
        "Battery drains too quickly.",
        "Device won't turn on.",
        "Keyboard is unresponsive.",
        "Software update failed.",
        "Overheating during use.",
        "Wi-Fi not connecting.",
        "Audio is distorted.",
        "Camera not working.",
        "Charging port is loose.",
        "Device randomly restarts.",
        "System is slow after update.",
        "Display flickering occasionally.",
        "Touchscreen not responsive.",
        "Customer reports boot loop."
    )

    val firstNames = listOf("James", "Emily", "Michael", "Sarah", "David", "Laura", "Chris", "Amanda", "Daniel", "Jessica")
    val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis")

    // Pick a random manufacturer and model
    val manufacturer = manufacturersAndModels.keys.random()
    val model = manufacturersAndModels[manufacturer]!!.random()

    // Generate random notes
    val notes = List(Random.nextInt(1, 4)) {
        val author = "${firstNames.random()} ${lastNames.random()}"
        val message = noteMessages.random()
        Note(author, message)
    }

    val uuid = Uuid.random().toString()

    return TicketInfo(
        idNum = Random.nextInt(0, 9999).toString(),
        customerId = customerId,
        assignedEmployee = if (customerId.toInt() % 12 == 0) "201" else "",
        device = Device(manufacturer, model),
        status = randomTicketStatus(),
        priority = randomTicketPriority(),
        updates = notes
    )
}

private fun generateCustomerNote(): Note {
    val firstNames = listOf("James", "Emily", "Michael", "Sarah", "David", "Laura", "Chris", "Amanda", "Daniel", "Jessica")
    val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis")

    val customerNotes = listOf(
        "Customer prefers email over phone calls.",
        "Be patient; customer is hard of hearing.",
        "Very particular about cleanliness.",
        "Customer asked for technician with experience in Apple products.",
        "Previously disputed a charge — explain repairs clearly.",
        "Customer may be late to pickups — allow grace period.",
        "Very friendly, but asks many detailed questions.",
        "Requires all communication to go through their assistant.",
        "Lives in a gated community — ask for access code before visit.",
        "Customer brought in device multiple times — be thorough this time.",
        "Requested expedited service for urgent work needs.",
        "Has a history of leaving positive reviews — provide top-notch service.",
        "Prefers repairs not be discussed in front of others (privacy request)."
    )

    val author = "${firstNames.random()} ${lastNames.random()}"
    val message = customerNotes.random()

    return Note(author, message)
}

private fun generateRandomContactInfo(firstName: String? = null, lastName: String? = null): ContactInfo {
    val domains = listOf("gmail.com", "yahoo.com", "outlook.com", "icloud.com", "protonmail.com")

    // Optionally reuse known first/last names to generate a realistic email
    val fName = firstName ?: listOf("james", "emily", "michael", "sarah", "david", "laura").random()
    val lName = lastName ?: listOf("smith", "johnson", "williams", "brown", "jones", "garcia").random()

    val numberPart = Random.nextInt(10, 99)
    val email = "$fName.$lName$numberPart@${domains.random()}".lowercase()

    val phone = mutableListOf<String>()

    // Random US-style phone number (XXX-XXX-XXXX)
    (0..Random.nextInt(0, 4)).forEach { _ ->
        phone.add(
            buildString {
                append(Random.nextInt(200, 999))  // Area code
                append("-")
                append(Random.nextInt(100, 999))  // Prefix
                append("-")
                append(Random.nextInt(1000, 9999)) // Line number
            }
        )
    }

    return ContactInfo(listOf(email), phone)
}