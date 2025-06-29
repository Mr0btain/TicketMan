package org.mr0btain.ticketman

import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.shared.AMPM
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.shared.Time
import org.mr0btain.ticketman.data.shared.getTime
import org.mr0btain.ticketman.data.shared.parseDateToLong
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.data.ticket.randomTicketPriority
import org.mr0btain.ticketman.data.ticket.randomTicketStatus
import org.mr0btain.ticketman.data.user.User
import java.text.DecimalFormat
import java.util.UUID
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi

/**
 * A bunch of half-assed clobbered together functions that spit out random test data. Its beautiful chaos
 */
fun generateMockData(num: Int): Triple<List<CustomerData>, List<TicketInfo>, List<User>> {
    // Generate some mock customer data
    val customerData = generateMockCustomerData(num)
    val users = generateMockUserData(6).toMutableList()

    var currentTicketId = Random.nextInt(0, 9999)

    // Hold the new data
    val newCustomerData = mutableListOf<CustomerData>()
    val newTicketData = mutableListOf<TicketInfo>()

    customerData.forEach { item ->
        val tickets = mutableListOf<TicketInfo>()
        val ticketIds = mutableListOf<String>()

        // Generate tickets
        (0 .. Random.nextInt(1, 10)).forEach { i ->
            tickets.add(generateRandomTicket(item.identifier, currentTicketId.toString()))

            currentTicketId++
        }

        // Make a list of ticket ids
        tickets.forEach { tick ->
            ticketIds.add(tick.identifier)
        }

        // Update the customer data
        newCustomerData.add(item.copy(tickets = ticketIds))

        // Add the tickets to the result
        newTicketData.addAll(tickets)
    }

    newTicketData.forEachIndexed { index, ticket ->
        // Get random employee
        val userId = Random.nextInt(0, 6)
        val user = users[userId]

        // Set ticket data assigned employee
        newTicketData[index] = ticket.copy(
            assignedUser = user.identifier
        )

        // add ticket to employee
        users[userId] = user.copy(tickets = user.tickets + ticket.identifier)
    }

    println("Generated Customer list: ${customerData.count()}")
    println("Generated ticket list: ${newTicketData.count()}")
    println("Generated user list: ${users.count()}")


    return Triple(newCustomerData, newTicketData, users)
}

fun generateRandomInventoryItems(count: Int): List<InventoryItem> {
    val itemNames = listOf(
        "iPhone 11 Screen", "Galaxy S10 Screen", "Charging Port", "Battery Pack", "IC Chip A12",
        "Logic Board Connector", "Touch Controller", "Screen Adhesive", "Camera Module", "Speaker Assembly",
        "Microphone", "Flex Cable", "Display IC", "Backlight Filter", "USB-C Port", "Glass Lens",
        "SIM Tray", "Housing Frame", "Home Button", "Power Button Flex", "Volume Button", "Thermal Paste"
    )

    val descriptions = listOf(
        "High quality replacement part", "OEM standard component", "Compatible with multiple models",
        "Includes adhesive backing", "Precision-engineered for easy installation",
        "Durable and long-lasting", "Heat-resistant and flexible", "Comes with warranty"
    )

    val formatter = DecimalFormat("#.00")
    val now = getFakeTime()

    return List(count) {
        val name = itemNames.random()
        val description = descriptions.random()
        val stock = Random.nextInt(1, 500)
        val costVal = Random.nextDouble(0.5, 50.0)
        val priceVal = costVal * Random.nextDouble(1.2, 2.5)
        val identifier = Random.nextInt(100000, 999999).toString()
        val timeOffset = Random.nextLong(0, 30L * 24 * 60 * 60 * 1000) // up to 30 days ago

        InventoryItem(
            identifier = identifier,
            name = name,
            description = description,
            stock = stock,
            cost = "$${formatter.format(costVal)}",
            price = "$${formatter.format(priceVal)}",
            notes = generateRandomInventoryNotes(Random.nextInt(0, 4)),
            time = now - timeOffset
        )
    }
}

fun generateMockCustomerData(num: Int): List<CustomerData> {
    val data = mutableListOf<CustomerData>()
    val ids = mutableListOf<String>()

    (0..num).forEach { i ->
        var id = Random.nextInt(0, 9999).toString()

        while (ids.contains(id)) {
            id = Random.nextInt(0, 9999).toString()
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
                identifier = id,
                info = CustomerInfo(
                    identifier = id,
                    name = name,
                    address = generateRandomAddress(),
                    contactInfo = generateRandomContactInfo(name.firstName, name.lastName),
                    time = getFakeTime()
                ),
                notes = notes
            )
        )
    }

    return data
}

// Function to generate random employee data
fun generateMockUserData(num: Int): List<User> {
    val users = mutableListOf<User>()

    for (i in 0..num) {
        // Random AccessLevel
        val accessLevel = AccessLevel.entries.random()

        val name = generateRandomName()

        // Random Notes
        val notes = (1..Random.nextInt(1, 3)).map { generateEmployeeNote() }

        // Random ContactInfo (emails and phone numbers)
        val email = (1..Random.nextInt(1, 3)).map { "user${Random.nextInt(1000, 9999)}@example.com" }
        val phone = (1..Random.nextInt(1, 4)).map { generateRandomPhoneNumber() }
        val contactInfo = ContactInfo(email, phone)

        // Create and return User object
        users.add(
            User(
                identifier = UUID.randomUUID().toString(),
                username = "${name.firstName}_${name.lastName}",
                password = name.lastName,
                name = name,
                notes = notes,
                contactInfo = contactInfo,
                tickets = listOf(),
                accessLevel = accessLevel,
                time = getFakeTime()
            )
        )
    }

    if (!users.map { it.accessLevel }.contains(AccessLevel.ADMIN)) {
        // Random AccessLevel
        val accessLevel = AccessLevel.ADMIN

        // Random identifier
        val identifier = Random.nextInt(0, 9999)

        val name = generateRandomName()

        // Random Notes
        val notes = (1..Random.nextInt(1, 3)).map { generateEmployeeNote() }

        // Random ContactInfo (emails and phone numbers)
        val email = (1..Random.nextInt(1, 3)).map { "user${Random.nextInt(1000, 9999)}@example.com" }
        val phone = (1..Random.nextInt(1, 4)).map { generateRandomPhoneNumber() }
        val contactInfo = ContactInfo(email, phone)

        users.add(
            User(
                identifier = UUID.randomUUID().toString(),
                username = "${name.firstName}_${name.lastName}",
                password = name.lastName,
                name = name,
                notes = notes,
                contactInfo = contactInfo,
                tickets = listOf(),
                accessLevel = accessLevel,
                time = getFakeTime()
            )
        )
    }

    return users
}

private fun getFakeTime(): Long {
    return parseDateToLong(
        Time(
            year = Random.nextInt(2000, 2025),
            month = Random.nextInt(1, 12),
            day = Random.nextInt(1, 21),
            hour = Random.nextInt(1, 12),
            minute = Random.nextInt(0, 59),
            second = Random.nextInt(0, 59),
            ampm = AMPM.entries.random()
        )
    )
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

private fun generateRandomName(): PersonName {
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

    return PersonName(firstNames.random(), lastNames.random())
}

@OptIn(ExperimentalUuidApi::class)
fun generateRandomTicket(customerId: String, ticketId: String): TicketInfo {
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
        Note(author, message, getTime())
    }

    return TicketInfo(
        identifier = ticketId.toString(),
        customerId = customerId,
        assignedUser = "",
        device = Device(manufacturer, model),
        status = randomTicketStatus(),
        priority = randomTicketPriority(),
        updates = notes,
        time = getFakeTime()
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

    return Note(author, message, getTime())
}

// Function to generate a random employee note
fun generateEmployeeNote(): Note {
    // List of potential authors for the note
    val authors = listOf("Manager", "HR", "Supervisor", "Team Lead", "Peer", "Self")

// List of potential note topics related to performance, behavior, etc.
    val noteTopics = listOf(
        "has shown excellent performance on recent projects.",
        "needs to improve communication with team members.",
        "has demonstrated great leadership qualities during the last quarter.",
        "is consistently meeting expectations.",
        "requires additional training on the new software tools.",
        "has been taking initiative in meetings and brainstorming sessions.",
        "needs to be more proactive with deadlines.",
        "shows great teamwork skills and collaborates effectively.",
        "has exceeded sales targets this month.",
        "has been a key contributor to the success of the latest project.",
        "could benefit from more time management training.",
        "has a positive attitude and motivates others in the workplace."
    )

    return Note(authors.random(), noteTopics.random(), getTime())
}

fun generateRandomInventoryNotes(count: Int): List<Note> {
    val authors = listOf("Alice", "Bob", "Charlie", "Dana", "Eli", "Frank")
    val components = listOf("resistor", "capacitor", "transistor", "diode", "IC", "inductor")
    val conditions = listOf("working", "damaged", "needs testing", "low stock", "new", "reordered")
    val locations = listOf("Bin A1", "Shelf B2", "Cabinet C3", "Box D4", "Rack E5")

    return List(count) {
        val author = authors.random()
        val component = components.random()
        val condition = conditions.random()
        val location = locations.random()
        val quantity = Random.nextInt(1, 100)

        val text = "Found $quantity $component(s) in $location - Status: $condition."
        Note(author, text)
    }
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
        phone.add(generateRandomPhoneNumber())
    }

    return ContactInfo(listOf(email), phone)
}

private fun generateRandomPhoneNumber(): String {
    // Random US-style phone number (XXX-XXX-XXXX)
    return buildString {
        append(Random.nextInt(200, 999))  // Area code
        append("-")
        append(Random.nextInt(100, 999))  // Prefix
        append("-")
        append(Random.nextInt(1000, 9999)) // Line number
    }
}