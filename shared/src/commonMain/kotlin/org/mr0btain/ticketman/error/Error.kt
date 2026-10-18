package org.mr0btain.ticketman.error

import org.mr0btain.ticketman.logging.LogMessage

/** Error half of a [Result]. [message] is readable enough to show the user */
interface Error : LogMessage
