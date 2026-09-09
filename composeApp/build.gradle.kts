import org.gradle.kotlin.dsl.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
//    alias(libs.plugins.androidApplication)

    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
  //  alias(libs.plugins.composeHotReload)

    alias(libs.plugins.kotlin.serialization)

}

kotlin {
    /**
     * To allow
     * Koin pre-release version
     */
    sourceSets.all {
        // Must be explicitly enabled!
        languageSettings.enableLanguageFeature("ExplicitBackingFields")
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xskip-prerelease-check")
            }
        }
    }

//    androidTarget {
//        compilerOptions {
//            jvmTarget.set(JvmTarget.JVM_11)
//        }
//    }

    androidLibrary {
        namespace = "com.touhidapps.mmca"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.tooling.preview)
            implementation(libs.compose.tooling)
            implementation(libs.androidx.activity.compose)

            api(libs.bundles.koinAndroid)
            implementation(libs.ktor.client.okhttp)

        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
//            api(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            api(libs.bundles.koinCommon)

            implementation(libs.bundles.nav3)
            implementation(libs.androidx.material3.adaptive)

            implementation(libs.kotlinx.serialization.json)

            implementation(libs.bundles.ktor)

            // Business layer
          //  implementation(project(":domain"))
            implementation(project(":core:ui"))
            implementation(project(":core:common"))
//            implementation(projects.preferences)
            implementation(projects.core.network)

            implementation(project(":feature:home"))
            implementation(project(":feature:flowers"))
            implementation(project(":feature:language"))
            implementation(project(":feature:about"))
            implementation(project(":feature:forceUpdate"))

        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}


//android {
//    namespace = "com.touhidapps.mmca"
//    compileSdk = libs.versions.android.compileSdk.get().toInt()
//
//    defaultConfig {
//        applicationId = "com.touhidapps.mmca"
//        minSdk = libs.versions.android.minSdk.get().toInt()
//        targetSdk = libs.versions.android.targetSdk.get().toInt()
//        versionCode = 1
//        versionName = "1.0"
//    }
//    packaging {
//        resources {
//            excludes += "/META-INF/{AL2.0,LGPL2.1}"
//        }
//    }
//    buildTypes {
//        getByName("release") {
//            isMinifyEnabled = false
//        }
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_11
//        targetCompatibility = JavaVersion.VERSION_11
//    }
//}

dependencies {
    //debugImplementation(compose.uiTooling)
}

