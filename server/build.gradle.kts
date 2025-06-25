plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    alias(libs.plugins.serialize)
}

group = "org.mr0btain.ticketman"
version = "1.0.0"
application {
    mainClass.set("org.mr0btain.ticketman.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Shared project stuff
    implementation(projects.shared)

    // Regular server bundle
    implementation(libs.bundles.ktorServer)

    // Testing bundle
    implementation(libs.bundles.ktorServerTesting)

    implementation(libs.bundles.exposed)
}