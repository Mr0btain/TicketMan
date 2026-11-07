rootProject.name = "TicketMan"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// ---------------------------------------- Shared libraries ----------------------------------------
// Tm-AppLogger and Tm-UI-Kit are built from source, from the first of:
//   1. <sharedLibsDir>/<name>, with sharedLibsDir set in ~/.gradle/gradle.properties (where you keep the checkouts)
//   2. ../<name>, checked out next to this project
//   3. .deps/<name>, a copy of its public GitHub mirror at the tag below, cloned on the first build
// In 1 and 2 a change to the library shows up here straight away. Moving to a new release is changing its tag here
// (and the matching version in gradle/libs.versions.toml)
val sharedLibs = linkedMapOf(
    "Tm-AppLogger" to "v0.1.0",
    "Tm-UI-Kit" to "v0.1.0"
)

fun git(vararg args: String, dir: File? = null) {
    providers.exec {
        commandLine("git", *args)
        if (dir != null) workingDir = dir
    }.result.get()
}

fun gitOutput(vararg args: String, dir: File): String? = providers.exec {
    commandLine("git", *args)
    workingDir = dir
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { null }

val sharedLibsDir = providers.gradleProperty("sharedLibsDir").orNull?.let(::file)

sharedLibs.forEach { (name, tag) ->
    val local = listOfNotNull(sharedLibsDir?.resolve(name), file("../$name")).firstOrNull { it.isDirectory }
    val dir = local ?: file(".deps/$name").also { copy ->
        val url = "https://github.com/Mr0btain/$name.git"
        if (!copy.isDirectory) {
            logger.lifecycle("Getting $name $tag from $url")
            git("clone", "--quiet", "--depth", "1", "--branch", tag, url, copy.path)
        } else if (gitOutput("describe", "--tags", "--exact-match", dir = copy) != tag) {
            logger.lifecycle("Moving $name to $tag")
            git("fetch", "--quiet", "--depth", "1", "origin", "tag", tag, dir = copy)
            git("checkout", "--quiet", tag, dir = copy)
        }
    }
    includeBuild(dir)
}

include(":composeApp")
include(":androidApp")
include(":server")
include(":shared")