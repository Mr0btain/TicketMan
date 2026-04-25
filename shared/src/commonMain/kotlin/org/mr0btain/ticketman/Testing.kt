package org.mr0btain.ticketman

import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.randomTicketPriority
import org.mr0btain.ticketman.data.ticket.randomTicketStatus
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.DecimalFormat
import kotlin.random.Random

/**
 * Generates a random customer for testing
 */
fun generateMockCustomer(): Pair<Customer, List<Note>> = Pair(
    Customer(
        name = generateRandomName(),
        address = generateRandomAddress(),
        email = generateEmail(),
        phone = generatePhoneNumber()
    ),
    // Generate notes
    buildList {
        (0..Random.nextInt(0, 4)).forEach { _ ->
            add(generateCustomerNote())
        }
    }
)

/**
 * Generates a random inventory for testing
 */
fun generateRandomInventoryItem(): Pair<Inventory, List<Note>> {
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
    val costVal = Random.nextDouble(0.5, 50.0)
    val priceVal = costVal * Random.nextDouble(1.2, 2.5)
    val timeOffset = Random.nextLong(0, 30L * 24 * 60 * 60 * 1000) // up to 30 days ago
    val notes = mutableListOf<Note>()

    // Generate notes
    (0..Random.nextInt(0, 12)).forEach { _ ->
        notes.add(generateCustomerNote())
    }

    return Pair(
        Inventory(
            name = itemNames.random(),
            description = descriptions.random(),
            stock = Random.nextInt(1, 500),
            cost = "$${formatter.format(costVal)}",
            price = "$${formatter.format(priceVal)}"
        ),
        notes
    )
}

/**
 * Generates a random user for testing
 */
fun generateMockUser(name: PersonName = generateRandomName()): Pair<User, List<Note>> = Pair(
    User(
        username = "${name.firstName}_${name.lastName}",
        password = name.lastName,
        name = name,
        phone = generatePhoneNumber(),
        email = generateEmail(),
        accessLevel = AccessLevel.entries.random()
    ),
    (1..Random.nextInt(1, 3)).map { generateEmployeeNote() }
)

/**
 * Generates a fake time for testing
 */
private fun getFakeTime(): Long {
    return Time(
        year = Random.nextInt(2000, 2025),
        month = Random.nextInt(1, 12),
        day = Random.nextInt(1, 21),
        hour = Random.nextInt(1, 12),
        minute = Random.nextInt(0, 59),
        second = Random.nextInt(0, 59),
        ampm = Time.AMPM.entries.random()
    ).toLong()
}

/**
 * Generates a random address for testing
 */
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

/**
 * Generates a random name for testing
 */
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

/**
 * Generates a random ticket for testing
 */
fun generateRandomTicket(): Pair<Ticket, List<Note>> {
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
    val notes = mutableListOf<Note>()

    for (i in 0..Random.nextInt(1, 4)) {
        val author = "${firstNames.random()} ${lastNames.random()}"
        val message = noteMessages.random()

        notes.add(
            Note(UUIDv7.randomUUID(), author, message)
        )
    }

    return Pair(
        Ticket(
            device = Device(manufacturer, model),
            status = randomTicketStatus(),
            priority = randomTicketPriority()
        ),
        notes
    )
}

/**
 * Generates a random customer note for testing
 */
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

    return Note(author = author, message = message)
}

/**
 * Generates a random employee note for testing
 */
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

    return Note(author = authors.random(), message = noteTopics.random(),)
}

/**
 * Generates random inventory notes for testing
 */
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
        Note(author =  author, message = text)
    }
}

/**
 * Generates a random contact info for testing
 */

private fun generateEmail(): String {
    val domains = listOf("gmail.com", "yahoo.com", "outlook.com", "icloud.com", "protonmail.com")

    // Optionally reuse known first/last names to generate a realistic email
    val fName = listOf("james", "emily", "michael", "sarah", "david", "laura").random()
    val lName = listOf("smith", "johnson", "williams", "brown", "jones", "garcia").random()

    val numberPart = Random.nextInt(10, 99)
    return "$fName.$lName$numberPart@${domains.random()}".lowercase()
}

/**
 * Generates a random phone number for testing
 */
private fun generatePhoneNumber(): String = buildString {
    append(Random.nextInt(200, 999))  // Area code
    append("-")
    append(Random.nextInt(100, 999))  // Prefix
    append("-")
    append(Random.nextInt(1000, 9999)) // Line number
}

/**
 * Generates a random file for testing
 */
fun generateRandomFile(path: String, sizeInBytes: Long) {
    val bufferSize = 1024 * 1024 // 1 MB buffer
    val buffer = ByteArray(bufferSize)
    val random = SecureRandom()
    val file = File(path)

    FileOutputStream(file).use { output ->
        var bytesWritten: Long = 0
        while (bytesWritten < sizeInBytes) {
            val remaining = sizeInBytes - bytesWritten
            val toWrite = if (remaining < bufferSize) remaining.toInt() else bufferSize

            random.nextBytes(buffer)
            output.write(buffer, 0, toWrite)
            bytesWritten += toWrite

            // Optional: Print progress
            val percent = (bytesWritten * 100) / sizeInBytes
            println("Generated $bytesWritten / $sizeInBytes bytes ($percent%)")
        }
    }
}