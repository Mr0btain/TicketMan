import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.serialize)
}

// BuildInfo.VERSION (ticketman.version), so the server and every client know the version they were built as and can
// tell when they're too far apart to work together (server/AppVersion.kt)
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputDir = layout.buildDirectory.dir("generated/buildInfo")
    val appVersion = providers.gradleProperty("ticketman.version").get()
    inputs.property("version", appVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("org/mr0btain/ticketman/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package org.mr0btain.ticketman\n\nobject BuildInfo {\n    const val VERSION = \"$appVersion\"\n}\n")
    }
}

kotlin {
    android {
        namespace = "org.mr0btain.ticketman.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    // iOS only builds on a Mac (it needs Xcode), so elsewhere the targets are left out rather than skipped with a
    // warning on every build
    if (System.getProperty("os.name").startsWith("Mac")) {
        iosArm64()
        iosSimulatorArm64()
    }
    
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)
        }

        commonMain.dependencies {
            // put your Multiplatform dependencies here
            implementation(libs.kotlin.coroutines)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Times on printouts and the screen (components/utils/Formatting.kt)
            implementation(libs.kotlinx.datetime)

            // FileOps
            //implementation(libs.bundles.fileOps)
            implementation(libs.kotlinx.io)
        }

        androidMain.dependencies {
        }


        jvmMain.dependencies {
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        wasmJsMain.dependencies {
        }
    }
}
