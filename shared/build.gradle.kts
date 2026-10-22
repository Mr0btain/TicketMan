import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.serialize)
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
    
    //iosX64()
    //iosArm64()
    //iosSimulatorArm64()
    
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
