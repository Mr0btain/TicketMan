package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Which of the theme's colors a status shows in, so it keeps its meaning across themes */
@Serializable
enum class StatusTone {
    ACCENT,
    SUCCESS,
    WARNING,
    DANGER,
    NEUTRAL
}

/**
 * A ticket status the shop named itself, like "Awaiting parts". Kept in the shop profile
 * @param actsAs The built-in status it counts as, which decides where the ticket shows (queue, ready for pickup).
 * Never [TicketStatusType.PICKED_UP], which only pickup sets
 * @param tone Its color, null for [actsAs]'s
 * @param showCustomer Whether customers see its name; when they don't they see [actsAs]'s
 * @param retired No longer picked, but kept so tickets and history that used it still show its name
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class CustomStatus(
    val identifier: Uuid = Uuid.generateV7(),
    val name: String = "",
    val actsAs: TicketStatusType = TicketStatusType.IN_PROGRESS,
    val tone: StatusTone? = null,
    val showCustomer: Boolean = true,
    val retired: Boolean = false
) {
    val choice: StatusChoice
        get() = StatusChoice(actsAs, identifier)
}

/**
 * What the shop's statuses can be, and how a ticket's status reads with them. Checked by both the client and the
 * server
 */
@OptIn(ExperimentalUuidApi::class)
object CustomStatusRules {
    const val MAX_NAME_LENGTH = 40
    const val MAX_STATUSES = 40

    /** Problem with the shop's statuses, or null if they're fine */
    fun problem(statuses: List<CustomStatus>): String? {
        val inUse = statuses.filterNot { it.retired }
        val builtIn = TicketStatusType.entries.map { it.label.lowercase() }.toSet()

        return when {
            statuses.size > MAX_STATUSES -> "A shop can have up to $MAX_STATUSES statuses"
            statuses.distinctBy { it.identifier }.size != statuses.size -> "Two statuses have the same id"
            statuses.any { it.name.isBlank() } -> "Every status needs a name"
            statuses.any { it.name.trim().length > MAX_NAME_LENGTH } -> "Status names can be up to $MAX_NAME_LENGTH characters"
            statuses.any { it.actsAs == TicketStatusType.PICKED_UP } -> "A status can't act as picked up, only pickup closes a ticket"
            inUse.any { it.name.trim().lowercase() in builtIn } -> "That name is already a built-in status"
            inUse.distinctBy { it.name.trim().lowercase() }.size != inUse.size -> "Two statuses have the same name"
            else -> null
        }
    }

    /**
     * Problem with changing [previous] to [updated], or null. What a status acts as can't change, since tickets
     * already in it were filed by the old one; a new status does that instead
     */
    fun changeProblem(previous: List<CustomStatus>, updated: List<CustomStatus>): String? =
        updated.firstOrNull { status -> previous.byId(status.identifier)?.let { it.actsAs != status.actsAs } == true }
            ?.let { "${it.name.trim()} can't change what it counts as. Retire it and add a new status instead" }

    /**
     * The statuses after a save of [updated], with any of [previous] it left out kept as retired, since tickets and
     * their history may still point at them
     */
    fun keepingRemoved(previous: List<CustomStatus>, updated: List<CustomStatus>): List<CustomStatus> {
        val ids = updated.map { it.identifier }.toSet()
        return updated.map { it.copy(name = it.name.trim()) } +
            previous.filter { it.identifier !in ids }.map { it.copy(retired = true) }
    }

    /**
     * Checks [wanted] against the shop's [statuses] and fills in the built-in status its custom one acts as
     * @param current The ticket's status now, whose custom status may stay even if it's since been retired
     * @return The status to save, or the problem with it
     */
    fun resolve(wanted: TicketStatus, statuses: List<CustomStatus>, current: TicketStatus? = null): Resolved {
        val id = wanted.custom ?: return Resolved.Ok(wanted)
        val custom = statuses.byId(id) ?: return Resolved.Problem("That status doesn't exist")

        if (custom.retired && current?.custom != id) return Resolved.Problem("The status ${custom.name} isn't used any more")

        return Resolved.Ok(wanted.copy(status = custom.actsAs))
    }

    sealed interface Resolved {
        data class Ok(val status: TicketStatus) : Resolved
        data class Problem(val message: String) : Resolved
    }
}

@OptIn(ExperimentalUuidApi::class)
fun List<CustomStatus>.byId(identifier: Uuid?): CustomStatus? =
    identifier?.let { id -> firstOrNull { it.identifier == id } }

/** The custom status [choice] names, if it names one that still acts as its built-in status */
private fun List<CustomStatus>.customFor(choice: StatusChoice): CustomStatus? =
    byId(choice.custom)?.takeIf { it.actsAs == choice.status }

/** What staff call [choice] */
fun List<CustomStatus>.label(choice: StatusChoice): String =
    customFor(choice)?.name ?: choice.status.label

/** What customers are shown for [choice]: its name if the shop shares it, otherwise the built-in status it acts as */
fun List<CustomStatus>.customerLabel(choice: StatusChoice): String =
    customFor(choice)?.takeIf { it.showCustomer }?.name ?: choice.status.label

fun List<CustomStatus>.tone(choice: StatusChoice): StatusTone =
    customFor(choice)?.tone ?: choice.status.tone

/**
 * Statuses that can be picked by hand, built-in ones each followed by the shop's that act as them
 * @param current Kept in the list even if it's been retired, so a ticket in it still shows what it's in
 */
fun List<CustomStatus>.selectable(current: StatusChoice? = null): List<StatusChoice> =
    TicketStatusType.selectable.flatMap { type ->
        listOf(StatusChoice(type)) +
            filter { it.actsAs == type && (!it.retired || it.choice == current) }.map { it.choice }
    }
