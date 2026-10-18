import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
    androidLibrary {
        namespace = "org.mr0btain.ticketman"
        compileSdk = libs.versions.android.compileSdk.get().toInt()

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
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
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
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
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

        nativeMain.dependencies {
            // ktor
            implementation(libs.ktor.client.darwin)
        }

        val desktopTest by getting {
            dependencies {
                // Reads back the QR codes we make
                implementation(libs.zxing.core)

                // Self-signed certificates for a real HTTPS server to connect to
                implementation(libs.ktor.network.tls.certificates)
            }
        }

        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // ktor
            implementation(libs.ktor.client.okhttp)

            // open pdf
            //implementation(libs.openpdf)
        }
    }
}

dependencies {
    androidRuntimeClasspath(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.mr0btain.ticketman.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.mr0btain.ticketman"
            packageVersion = "1.0.0"
            modules("jdk.unsupported", "java.management")
            modules("jdk.unsupported.desktop")
        }

        tasks.withType<JavaExec>().configureEach {
            systemProperty("ticketmanDebug", System.getProperty("ticketmanDebug"))
        }

    }
}
