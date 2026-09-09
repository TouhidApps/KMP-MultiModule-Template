plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
//    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false

    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
//    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.androidLint) apply false
}

//subprojects {
//    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
//        compilerOptions {
//            freeCompilerArgs.add("-Xskip-prerelease-check")
//        }
//    }
//}


subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            // For some pre-release version library use
            freeCompilerArgs.add("-Xskip-prerelease-check")

        }
//        kotlinOptions {
//            freeCompilerArgs += "-Xskip-prerelease-check"
//        }
    }
}
