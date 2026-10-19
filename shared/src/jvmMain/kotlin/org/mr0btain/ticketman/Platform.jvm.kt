package org.mr0btain.ticketman

class JVMPlatform: Platform {
    // Windows sets COMPUTERNAME and most Linux shells HOSTNAME. Asking the network for the host name can hang on a
    // badly set up machine, so this doesn't
    override val name: String = listOf("COMPUTERNAME", "HOSTNAME")
        .firstNotNullOfOrNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        ?: "${System.getProperty("os.name")} computer"
}

actual fun getPlatform(): Platform = JVMPlatform()
