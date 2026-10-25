#!/bin/sh
# Starts the desktop jar on the JRE the openjdk21 extension installed into /app/jre.
# Skiko unpacks its native library into ~/.skiko unless told otherwise, and the sandbox can't write the home folder
exec /app/jre/bin/java \
    -Dskiko.data.path="${XDG_CACHE_HOME:-$HOME/.cache}/skiko" \
    -jar /app/lib/ticketman/TicketMan.jar "$@"
