package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.appointment.AppointmentStatus

/** Cancels an appointment, marks it a no show, or books it again. Only check in marks one checked in */
@Serializable
data class AppointmentStatusRequest(val status: AppointmentStatus)
