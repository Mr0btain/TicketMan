import java.util.zip.GZIPOutputStream

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    alias(libs.plugins.serialize)
}

group = "org.mr0btain.ticketman"
version = providers.gradleProperty("ticketman.version").get()

application {
    mainClass.set("org.mr0btain.ticketman.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// BuildInfo.VERSION, so the server reports the version it was built as
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputDir = layout.buildDirectory.dir("generated/buildInfo")
    val appVersion = version.toString()
    inputs.property("version", appVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("org/mr0btain/ticketman/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package org.mr0btain.ticketman\n\nobject BuildInfo {\n    const val VERSION = \"$appVersion\"\n}\n")
    }
}

kotlin.sourceSets.main { kotlin.srcDir(generateBuildInfo) }

// Release file names: TicketMan-Server-Web-<version> with the web client built in (-PwithWeb), TicketMan-Server-<version>
// without. The zip and jar run on any system with Java 21, so they carry no OS or architecture
val releaseName = if (project.hasProperty("withWeb")) "TicketMan-Server-Web-$version" else "TicketMan-Server-$version"
tasks.distZip { archiveFileName.set("$releaseName.zip") }
tasks.distTar { archiveFileName.set("$releaseName.tar") }
ktor { fatJar { archiveFileName.set("$releaseName.jar") } }

dependencies {
    // Shared project stuff
    implementation(projects.shared)

    // Regular server bundle
    implementation(libs.bundles.ktorServer)

    // Testing bundle
    implementation(libs.bundles.ktorServerTesting)

    // Exposed for database
    implementation(libs.bundles.exposed)

    // Photo resizing
    implementation(libs.thumbnailator)

    // Invoice PDFs: the print layout drawn through a Graphics2D that writes PDF
    implementation(libs.pdfbox)
    implementation(libs.pdfbox.graphics2d)

    // Customer emails over the shop's SMTP server
    implementation(libs.angus.mail)
}
// -PwithWeb builds the web client into the server, which then serves it at /. Off by default because the Wasm
// build is slow. The big files get a gzipped copy next to them, which the server sends to browsers that accept it
if (project.hasProperty("withWeb")) {
    val webClient = tasks.register<Sync>("webClient") {
        dependsOn(":composeApp:wasmJsBrowserDistribution")
        from(project(":composeApp").layout.buildDirectory.dir("dist/wasmJs/productionExecutable")) {
            // Only for debugging the client, and big
            exclude("**/*.map")
        }
        into(layout.buildDirectory.dir("webClient"))

        doLast {
            destinationDir.walkTopDown()
                .filter { it.isFile && it.extension in setOf("wasm", "js", "mjs", "html", "css", "json") }
                .forEach { file ->
                    GZIPOutputStream(File(file.path + ".gz").outputStream()).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
        }
    }

    tasks.processResources {
        from(webClient) { into("web") }
    }
}
