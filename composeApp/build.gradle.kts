import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialize)
}

kotlin {
    android {
        namespace = "org.mr0btain.ticketman"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }
    }
    

    /*
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
     */
    
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }

            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }
        binaries.executable()
    }

    // Desktop and Android both use OkHttp, so the code that checks a server's certificate is shared between them
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withCompilations { it.platformType == KotlinPlatformType.jvm || it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        val desktopMain = getByName("desktopMain")

        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.components.resources)
            implementation(libs.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)

            // For Navigation
            implementation(libs.compose.navigation)

            // For Koin Implementation
            api(libs.bundles.koin)

            // Window Size class
            implementation(libs.window.size.multiplatform)

            // ktor http stuff
            implementation(libs.bundles.ktorClient)

            // Exposed database stuff
            //implementation(libs.bundles.exposed)

            // FileKit for file operations
            implementation(libs.bundles.fileKit)

            // Photo loading with memory and disk caches, through our authenticated Ktor client
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // Streaming big downloads to a file
            implementation(libs.kotlinx.io)

            // Encrypted key-value storage
            implementation(libs.ksafe)

            // Local dates and times for display
            implementation(libs.kotlinx.datetime)

            // Frosted glass blur for the ui kit
            implementation(libs.haze)
            implementation(libs.haze.blur)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        /*
        nativeMain.dependencies {
            // ktor
            implementation(libs.ktor.client.darwin)
        }
         */

        val desktopTest = getByName("desktopTest") {
            dependencies {
                // Reads back the QR codes we make
                implementation(libs.zxing.core)

                // Self-signed certificates for a real HTTPS server to connect to
                implementation(libs.ktor.network.tls.certificates)
            }
        }

        val jvmSharedMain = getByName("jvmSharedMain") {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        desktopMain.dependencies {
            // -PallPlatforms takes every desktop system's Skia, so packageUberJarForCurrentOS makes one jar that runs
            // on any of them (with Java 21 installed). Otherwise only this machine's, which the installers bundle
            if (project.hasProperty("allPlatforms")) {
                implementation(compose.desktop.linux_x64)
                implementation(compose.desktop.linux_arm64)
                implementation(compose.desktop.windows_x64)
                implementation(compose.desktop.windows_arm64)
                implementation(compose.desktop.macos_x64)
                implementation(compose.desktop.macos_arm64)
            } else {
                implementation(compose.desktop.currentOs)
            }
            implementation(libs.kotlinx.coroutinesSwing)

            // ktor
            implementation(libs.ktor.client.okhttp)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.ui.tooling)
}

// Release file names say what they're for: TicketMan-<OS>-<version>-<arch>.<ext>, like TicketMan-Windows-0.8.0-x64.msi,
// for the machine that builds them, since jpackage only packages for its own system
val releaseVersion = providers.gradleProperty("ticketman.version").get()
val hostOs = System.getProperty("os.name").let {
    when {
        it.startsWith("Windows") -> "Windows"
        it.startsWith("Mac") -> "macOS"
        else -> "Linux"
    }
}
val hostArch = when (val arch = System.getProperty("os.arch")) {
    "amd64", "x86_64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> arch
}
fun releaseName(extension: String) = "TicketMan-$hostOs-$releaseVersion-$hostArch.$extension"

compose.desktop {
    application {
        mainClass = "org.mr0btain.ticketman.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TicketMan"
            description = "Ticket tracking for repair shops"
            vendor = "Mr0btain"
            // Installers only take numbers, so a 1.0.0-rc1 is packaged as 1.0.0
            val appVersion = providers.gradleProperty("ticketman.version").get().substringBefore('-')
            packageVersion = appVersion
            modules("jdk.unsupported", "java.management")
            modules("jdk.unsupported.desktop")

            // The icons come from scripts/AppIcon.java, which also makes the window, web and Android ones
            windows {
                iconFile.set(project.file("icons/ticketman.ico"))
                // Never change this: it's how a newer MSI knows to replace the installed one
                upgradeUuid = "58a5706e-674f-40a1-bec1-861c46c444e9"
                menu = true
                shortcut = true
                dirChooser = true
            }
            macOS {
                iconFile.set(project.file("icons/ticketman.icns"))
                bundleID = "org.mr0btain.ticketman"
                // macOS refuses a version starting with 0, so 0.x builds say 1.0.0 inside the app bundle. The DMG's
                // file name still carries the real version (renamed below)
                packageVersion = if (appVersion.startsWith("0.")) "1.0.0" else appVersion
            }
            linux {
                iconFile.set(project.file("icons/ticketman.png"))
                menuGroup = "Office"
            }
        }

        tasks.withType<JavaExec>().configureEach {
            systemProperty("ticketmanDebug", System.getProperty("ticketmanDebug"))

            // -PdevTrustStore=<file>: trust the CA in that PKCS12 file (scripts/dev_proxy.sh makes one from
            // Caddy's local CA) in place of the system's, to try a proxy with a "public" certificate
            (project.findProperty("devTrustStore") as String?)?.let { store ->
                systemProperty("javax.net.ssl.trustStore", rootProject.file(store).absolutePath)
                systemProperty("javax.net.ssl.trustStoreType", "PKCS12")
                systemProperty("javax.net.ssl.trustStorePassword", "changeit")
            }
        }
    }
}

// jpackage names the installers after packageName and packageVersion (ticketman_0.8.0_amd64.deb), so they're renamed
// once made. The macOS one gets the real version this way too, not the 1.0.0 inside its app bundle
tasks.withType<AbstractJPackageTask>().configureEach {
    val installerDir = destinationDir
    doLast {
        installerDir.get().asFile.listFiles { file -> file.extension in setOf("msi", "dmg", "deb", "pkg", "exe", "rpm") }
            ?.filter { it.name != releaseName(it.extension) }
            ?.forEach { file ->
                val named = File(file.parentFile, releaseName(file.extension))
                named.delete()
                check(file.renameTo(named)) { "Couldn't rename ${file.name} to ${named.name}" }
            }
    }
}

// With -PallPlatforms the jar runs on any desktop system, so it carries no OS or architecture. Compose's uber jar
// tasks are the plain org.gradle.jvm.tasks.Jar, not the Jar this script imports
tasks.withType<org.gradle.jvm.tasks.Jar>().matching { it.name.endsWith("UberJarForCurrentOS") }.configureEach {
    archiveFileName.set(
        if (project.hasProperty("allPlatforms")) "TicketMan-Desktop-$releaseVersion.jar" else releaseName("jar")
    )
}

// The app unpacked (bin/TicketMan starts it), for Linux systems without dpkg: compose/binaries/main/tar/
tasks.register<Tar>("packageTarball") {
    group = "compose desktop"
    description = "Packs the app made by createDistributable into a .tar.gz"
    from(tasks.named("createDistributable"))
    compression = Compression.GZIP
    archiveFileName.set(releaseName("tar.gz"))
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/tar"))
}
