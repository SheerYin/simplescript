pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.canvasmc.io/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "simplescript"

include("canvas")
include("velocity")
