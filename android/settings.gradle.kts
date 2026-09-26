pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android, the drivers for USB serial adapters.
        maven("https://jitpack.io") { content { includeGroup("com.github.mik3y") } }
    }
}
rootProject.name = "radial-steno"
include(":app")
