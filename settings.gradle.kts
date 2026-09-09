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

    ":core:network",
    ":core:common",
    ":core:preferences",
    ":core:ui",

    ":feature:language",
    ":feature:home",
    ":feature:flowers",
    ":feature:settings",
    ":feature:about",
    ":feature:alertMaintenance",
    ":feature:alertWelcome",
    ":feature:forceUpdate",
)



include(":core:database")
