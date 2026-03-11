# KMP Multi-Module Template Guide

This project is a production-ready Kotlin Multiplatform (KMP) template designed with a feature-focused, multi-module architecture. It follows Clean Architecture principles to ensure scalability, testability, and maintainability.

---

## 🏗 Project Structure

The project is organized into logical layers to separate concerns:

### 1. **App Modules**
- **`composeApp`**: The shared entry point for UI. It contains the main `App.kt`, global DI configuration, and navigation graph.
- **`androidApp`**: The Android-specific host application.
- **`iosApp`**: The Xcode project for the iOS application.

### 2. **Core Modules (`/core`)**
Core modules are internal libraries used by features. They should be independent of business logic.
- **`:common`**: Shared primitives, models, and localization strings.
- **`:network`**: Ktor client configuration and network-related utilities.
- **`:preferences`**: Key-value storage (Multiplatform Settings).
- **`:ui`**: Shared Compose UI components, themes, and `LocalizationManager`.

### 3. **Feature Modules (`/feature`)**
Features are "vertical slices" of the app. Each feature module typically contains:
- **`data`**: API implementations, DTOs, and Mappers.
- **`domain`**: Business logic, UseCases, and Repository interfaces.
- **`presentation`**: UI (Compose), ViewModels, and UI Contracts.
- **`di`**: Feature-specific Koin modules.

---

## 📏 Architecture Rules (The Golden Rules)

To keep the project clean, follow these dependency rules:

1.  **Feature Isolation**: Feature modules **must NOT** depend on other feature modules.
2.  **Unidirectional Flow**:
    - `Presentation` -> `Domain`
    - `Data` -> `Domain`
    - `Feature` -> `Core-UI` & `Core-Common`
3.  **The "Big Glue"**: `composeApp` is the only module that should know about all feature modules to assemble the navigation graph and DI.

---

## 🚀 How to Use This Template

### 1. Adding a New Feature
1.  Create a new module in the `feature/` directory (e.g., `feature/profile`).
2.  Register the module in `settings.gradle.kts`:
    ```kotlin
    include(":profile")
    project(":profile").projectDir = file("feature/profile")
    ```
3.  Add the module dependency in `composeApp/build.gradle.kts`.
4.  Implement your `domain`, `data`, and `presentation` layers. (Can copy from existing modules)
5.  Create a `ProfileEntry.kt` to expose the feature's screens to the navigation.

### 2. Navigation (Navigation3)
Routes are defined as `sealed class Route` in `:ui`.
To add a new destination:
1.  Add a new `data object` or `data class` to `Route` in `:ui/nav/Route.kt`.
2.  In your feature's `Entry.kt` file, define the `entry`:
    ```kotlin
    fun EntryProviderScope<NavKey>.profileEntry() {
        entry<Route.Profile> { 
            val viewModel: ProfileViewModel = getKoin().get()
            ProfileScreen(viewModel) 
        }
    }
    ```
3.  Register the entry in `MainNavigation.kt`.

### 3. Dependency Injection (Koin)
1.  Define a `val featureModule = module { ... }` in your feature.
2.  Add it to the `appModule` list in `composeApp/src/commonMain/kotlin/.../di/KoinModule.kt`.

### 4. Localization
1.  Add your keys to the `Strings` interface in `:core:common`.
2.  Implement them in `StringsEn.kt` and `StringsBn.kt`.
3.  Usage in UI:
    ```kotlin
    val strings = LocalizationManager.strings
    Text(strings.your_key)
    ```

---

## 🛠 Tech Stack
- **UI**: [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- **DI**: [Koin](https://insert-koin.io/)
- **Network**: [Ktor](https://ktor.io/)
- **Navigation**: [Navigation3](https://developer.android.com/jetpack/compose/navigation)
- **Settings**: [Multiplatform Settings](https://github.com/russhwolf/multiplatform-settings)
- **Serialization**: [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Images**: [Coil3](https://coil-kt.github.io/coil/)
