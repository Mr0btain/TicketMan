package org.mr0btain.ticketman.components.data.components

import java.util.Calendar

fun getTime(): Long {
    return Calendar.getInstance().timeInMillis
}