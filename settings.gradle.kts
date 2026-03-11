rootProject.name = "KMP-MultiModule-Template" // TODO - Change your project name
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// TODO - change module path if you add new module
include(
    ":composeApp",
    ":androidApp",

    ":network",
    ":common",
    ":preferences",
    ":ui",

    ":language",
    ":home",
    ":flowers",
    ":settings",
    ":about",
    ":alertMaintenance",
    ":alertWelcome",
    ":forceUpdate",
)

project(":common").projectDir = file("core/common")
project(":network").projectDir = file("core/network")
project(":preferences").projectDir = file("core/preferences")
project(":ui").projectDir = file("core/ui")

project(":language").projectDir = file("feature/language")
project(":home").projectDir = file("feature/home")
project(":flowers").projectDir = file("feature/flowers")
project(":settings").projectDir = file("feature/settings")
project(":about").projectDir = file("feature/about")
project(":alertMaintenance").projectDir = file("feature/alertMaintenance")
project(":alertWelcome").projectDir = file("feature/alertWelcome")
project(":forceUpdate").projectDir = file("feature/forceUpdate")



