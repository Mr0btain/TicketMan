# Compose Multiplatform Template arm64

A Template that should build and run on linux and windows arm64 in intellij idea without the kotlin-natives prebuilts
error that you get with the new templates.

## Running on Desktop
  ** DOES NOT WORK ON WINDOWS ARM64 IDEA ATM. only linux **
   There is no build for org.jetbrains.compose.desktop:desktop-jvm-windows-arm64 yet so the true arm64 version of Intellij IDEA will not work.
   for now the only way to get it working is to use the x64 version of IDEA which doesnt seem to run too terribly emulated but you will notice it.

   In Linux arm64 you may need to use this evironment variable to avoid crashes. Mine crashes anytime i try to use animations without this.
   
    export MESA_EXTENSION_OVERRIDE="-GL_ARB_invalidate_subdata"
    
   For example on Ubuntu to make this permanent you can edit ~/.profile and add it to the bottom.
  
  To run on desktop youll need to add a config. at the top beside run hit add config

  ![alt text](https://github.com/Mr0btain/Compose-Multiplatform-arm64-Template/blob/main/readmeimages/editconfig.png?raw=true)

  in the top right of the Run/Debug Config screen that pops up hit the plus symbol.
  Select gradle config
  in the name you can name it something like desktopApp
  Under run you want to put composeApp:run

  ![alt text](https://github.com/Mr0btain/Compose-Multiplatform-arm64-Template/blob/main/readmeimages/runsettings.png?raw=true)

  After saving this config and hitting run you should end up with something like this

  ![alt text](https://github.com/Mr0btain/Compose-Multiplatform-arm64-Template/blob/main/readmeimages/example.png?raw=true)

## IOS
  I havent tested on IOS yet but it shouldnt have any problems.


## From original ReadMe

  This is a Kotlin Multiplatform project targeting Android, iOS, Desktop.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.
