This is a Kotlin Multiplatform project targeting Android, iOS.

### Feature focus multi-module architecture with clean architecture

app -> nav & app

## Uni direction library structure:
- in [domain] gradle add [common] module
- in [data] gradle add [domain] module

[data] -> [domain] -> [common] - (from data module can access of common and domain)

Data depends on Domain
Domain must NEVER depend on Data

✅ Golden rule for FEATURE modules

### A feature module depends on:
core-common (shared primitives)
core-ui (Compose UI building blocks)

### A feature module must NOT depend on:
data
core-network
other features

3️⃣ What feature-home-detail must NOT see

❌ FlowerRepositoryImpl
❌ DTOs
❌ API responses
❌ Room / SQLDelight
❌ Ktor

### All of that belongs to data.
core-data
↑
core-ui
↑
feature-*
↑
composeApp

### Quick Rules (Memorize These)
Data → depends on domain
UI → depends on domain
Feature → depends on UI + domain
composeApp → depends on everything

Layer -	Include unused extra param?
-----   ----------------------------
Data	✅ Always

UI / Route	❌ Only if screen needs it


## SharedPref/Settings:
- feature/language module

## API Call
- KTOR api call: feature/flowers (list package) module

## Pass related data to other screen: 
- feature/flowers (details package) module

## Common reusable UI 
- core/ui -> customUI

## Language and Localization
- Add new text and translation in : core/common -> localization
- Use like: 
```kotlin
  val env = rememberScreenEnv()
  Text(env.strings.confirm)
```
- Important files: UiHelper.kt & LocalizationManager.kt

