This is a Kotlin Multiplatform project targeting Android, Desktop (JVM), iOS, Web, Server, built with the [Kotlin Toolchain](https://kotlin-toolchain.org/dev/).

## KMP MultiModule Template
### Start your project with KMP, CMP, (Android, iOS, Web, Desktop), Kotlin SpringBoot Server, Ampher


- [/app/androidApp](./app/androidApp) contains the Android application.
- [/app/desktopApp](./app/desktopApp) contains the desktop (JVM) application.
- [/app/iosApp](./app/iosApp) contains the iOS application.
- [/app/shared](./app/shared) holds the code shared across your applications — Compose UI, business logic, and platform-specific implementations. [src](./app/shared/src) is for common code; the sibling `src@<platform>` folders (for example `src@android`) hold code compiled only for the platform named in the folder.
- [/app/webApp](./app/webApp) contains the web application, compiled to WebAssembly with Kotlin/Wasm.
- [/core](./core) is a namespace (not a module) holding the shared building blocks. Modules depend downward only; nothing in `core` may depend on `app`.
  - [/core/common](./core/common) — base of the graph: shared types, error wrappers, `BaseViewModel`, localization (`Strings`/`StringsEn`/`StringsBn`), the `Analytics` interface + a `NoOpAnalytics` fallback. Depends on nothing else in `core`.
  - [/core/network](./core/network) — HTTP/remote access: `createHttpClient()` (retry, logging, timeouts, response validation), `ResultNet`/`NetworkError`, `safeApiCall`. Ktor client plus the per-platform engines (okhttp on android/jvm, darwin on iOS, js on wasmJs).
  - [/core/database](./core/database) — local persistence via **Room KMP + KSP** (see below). `AppDatabase`/`DbFactory` plus a `NoteEntity`/`NoteDao` placeholder pair to copy from. **Not built for wasmJs** — neither Room nor the bundled SQLite driver target Kotlin/Wasm.
  - [/core/preferences](./core/preferences) — observable key/value user settings (`AppSettings`, backed by `multiplatform-settings`).
  - [/core/firebase](./core/firebase) — Firebase Analytics + Auth via the [GitLive KMP SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk) (`FirebaseAnalytics`, `AuthService`, `FirebaseConfig`). **Android only** — GitLive's iOS binding needs the native Firebase iOS frameworks linked through CocoaPods/SPM, which Amper has no integration for; wiring that up is on you if a real iOS app needs Firebase. Not on jvm/wasmJs either (no desktop/web Firebase SDK story worth the setup here). Every other platform binds `NoOpAnalytics` (from `core/common`) instead — see `app/*/src/*KoinModules.kt`.
  - [/core/ui](./core/ui) — the design system: **Navigation3** router (`MainNavigation`, `Route`, adaptive two-pane scenes), theme (colors, fonts, scaffold, nav drawer), localization manager, reusable components (`MyAlertDialog`, `MyAsyncImage`, `ErrorMessageWithIcon`, `ProgressUi`). The only `core` module that may see Compose.
- [/feature](./feature) is a namespace (not a module) holding one module per user-facing feature, each with its own MVI stack (`Contract`/`State`/`Events`/`ViewModel`). A feature may depend on `core`; it may **not** depend on `app` or on another feature.
  - [/feature/home](./feature/home) — the landing screen.
  - [/feature/flowers](./feature/flowers) — full data/domain/presentation stack: a remote list + details screen backed by `core/network`. The clearest example to copy for a new backend-driven feature.
  - [/feature/notification](./feature/notification) — another `core/network`-backed list, same shape as `flowers` but simpler (no details screen); read/unread state is local-only, documented where a real app would persist or sync it.
  - [/feature/more](./feature/more) — a menu hub screen (Language/About/Notifications) that only issues navigation intents.
  - [/feature/language](./feature/language) — in-app language picker; the choice is persisted in `core/preferences`.
  - [/feature/about](./feature/about) — static product information plus license/disclosure/tools/contact sub-pages.
  - [/feature/forceUpdate](./feature/forceUpdate) — version-gate screen with a Navigation3 entry but no update-check logic wired up yet (`ForceUpdateScreen(contract)` currently renders nothing) — wire a real version-check service to it.
  - [/feature/settings](./feature/settings) — user settings (dark mode over `core/preferences`; Language/About rows just navigate).
  - `home`, `flowers`, `notification`, `more`, `language`, `about`, `forceUpdate`, `settings` each expose a `fooEntry()` function wired into `app/shared`'s `AppEntryProvider` (reachable via `Route`/`MainNavigation`), and a `fooModule` Koin module loaded from `app/shared`'s `appModule`.
  - [/feature/alertWelcome](./feature/alertWelcome), [/feature/alertMaintenance](./feature/alertMaintenance) — not navigable screens; they're overlay gates composed directly around `MainNavigation()` in `app/shared`'s `App.kt`. `WelcomeAlert` shows once per install (state in `core/preferences`); `MaintenanceAlert` wraps the rest of the app and can block it entirely, backed by a `core/network` check.
- [/server](./server) contains the Spring Boot server application (Amper's native `settings.springBoot` — no Gradle plugin needed; starters are declared as full Maven coordinates, e.g. `org.springframework.boot:spring-boot-starter-web`, version-managed by the Spring BOM). JSON goes through kotlinx.serialization, not Jackson — `SerializationConfig` registers `KotlinSerializationJsonHttpMessageConverter` ahead of Spring's default. That converter needs `kotlin-reflect` on the classpath or it 500s at first request; easy to lose track of since nothing fails until you actually hit an endpoint.

### Room + KSP (core/database)

Amper supports [KSP](https://kotlin-toolchain.org/dev/user-guide/advanced/ksp/) natively via `settings.kotlin.ksp` (see `core/database/module.yaml`) — no Gradle plugin needed. Two things that are easy to get wrong if you extend this:

- **Don't write the `actual` for `DbFactory` yourself.** Room's KSP processor generates it from the `@ConstructedBy(DbFactory::class)` on `AppDatabase`; only the platform *builder* functions (`androidDatabaseBuilder`, `iosDatabaseBuilder`, `jvmDatabaseBuilder`) are hand-written, one per platform, each supplying the file path/`Context` Room needs.
- **Don't call `.setQueryCoroutineContext(Dispatchers.IO)` explicitly.** `Dispatchers.IO` has a real kotlinx.coroutines visibility quirk on Kotlin/Native (it can resolve as `internal`); Room already defaults to `Dispatchers.IO` when you don't set it, so `buildDatabase()` in `AppDatabase.kt` just omits the call.

To add your own table: add an `@Entity`/`@Dao` pair (copy `NoteEntity.kt`), list the entity in `AppDatabase`'s `entities = [...]`, bump `version`, and supply a migration.

### Module graph

```
   app/androidApp   app/desktopApp   app/iosApp   app/webApp
          \                |              |            /
           \_______________|______________|___________/
                                 |
                            app/shared                                server
                                 |                                       |
                                 |                                       |
     home · flowers · notification · more · language · about ·          |
     forceUpdate · settings   (alertWelcome, alertMaintenance: overlay   |
                                gates around MainNavigation, not routes) |
                                 |                                       |
              ___________________|___________________                   |
             |             |                |          |                 |
         core/ui     core/network    core/preferences core/common       |
             |             |                |          |                 |
             |_____________|________________|__________|                 |
                                 |                                       |
                            core/common                                 |
                                                                          |
   core/database (android/ios/jvm) and core/firebase (android only)     |
   are NOT reachable from app/shared (it targets wasmJs) — wired into   |
   androidApp/iosApp/desktopApp directly, see their *KoinModules.kt <----
```

Rules that keep this from rotting:

1. **Dependencies point one way only** — `app` → `feature` → `core`, and inside `core` everything points at `core/common`. A `core` module never imports from `feature` or `app`; a `feature` never imports from `app`.
2. **Siblings do not talk to each other.** `core/network` must not depend on `core/database`, and `feature/settings` must not depend on `feature/language`. If two `core` modules need to share a type, it goes in `core/common` (or a new `core/model`). If one feature needs to reach another, it raises a callback — `SettingsScreen(onOpenLanguage = ...)` — and `app/shared` wires the two together.
3. **Features own their screens, the app owns navigation.** A feature exposes composables and a `ViewModel`/`Contract`, never a platform API. Most features additionally expose a `fooEntry()` wired into `core/ui`'s `Route`/`MainNavigation` from `app/shared`'s `AppEntryProvider`.
4. **Only `core/ui` and `feature/*` enable Compose.** Keeping Compose out of `network`/`database`/`preferences` is what makes them testable and reusable on the server.
5. **Expose interfaces, hide SDKs.** `core/firebase` exports `Analytics`, not `FirebaseAnalytics` directly; `core/database` exports `AppDatabase`'s DAOs, not Room internals, to feature code. This is what lets a platform that can't support the SDK swap in a no-op — see `NoOpAnalytics` bound in `app/desktopApp`/`app/webApp`/`app/iosApp`'s `*KoinModules.kt`.
6. **Platform lists are a real constraint.** A module can only be consumed by a module whose platform list is a subset of its own. `app/shared` targets wasmJs, so it cannot depend on `core/database` or `core/firebase` — those get wired into `androidApp`/`iosApp`/`desktopApp` directly (see each app's `*KoinModules.kt`), gated behind the shared `Analytics` interface where a platform has no real implementation.

### Adding a feature module

1. `mkdir -p feature/<name>/src` and write `feature/<name>/module.yaml` (copy `feature/notification/module.yaml` for a `core/network`-backed feature with a Navigation3 entry, or `feature/about/module.yaml` for the smallest MVI shape). Give it a **unique** `compose.resources.packageName`.
2. Keep the platform list identical to `core/ui`'s, or `app/shared` will not be able to depend on it.
3. Add `feature/<name>` to the `modules:` list in [project.yaml](./project.yaml), and `//feature/<name>` to `app/shared`'s dependencies.
4. If it needs a screen reachable from navigation: add a case to `core/ui`'s `Route` (and to `MainNavigation`'s `serializersModule` polymorphic registration), write a `fooEntry()` function, and call it from `app/shared`'s `AppEntryProvider`. Load its Koin module from `app/shared`'s `appModule`.

### Adding another core module

1. `mkdir -p core/<name>/src` and write `core/<name>/module.yaml` (copy `core/preferences/module.yaml` as the starting point).
2. Add `core/<name>` to the `modules:` list in [project.yaml](./project.yaml) — Amper does not auto-discover modules.
3. Declare the dependency from whoever consumes it, using the `//core/<name>` path form.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for the Kotlin Toolchain: they download the pinned toolchain version on first use, so no separate installation is required. Build the whole project with `./kotlin build`.

### Running

Use the run configurations provided by the run widget in your IDE's toolbar. You can also run each module from the command line:

- Android app: `./kotlin run -m androidApp`
- Desktop app: `./kotlin run -m desktopApp`
- iOS app: `./kotlin run -m iosApp`
- Web app: `./kotlin run -m webApp`
- Server: `./kotlin run -m server`

### Using with Claude Code

This template ships with [Claude Code](https://claude.com/claude-code) project skills in [`.claude/skills/`](./.claude/skills/) and project rules in [CLAUDE.md](./CLAUDE.md). Open the project with Claude Code and the skills load automatically; Claude picks the matching one for each task, or you can call one directly with `/<skill-name>`.

| Area | Skills |
| --- | --- |
| Architecture and code | `kmp-clean-architecture`, `kmp-feature-scaffold`, `kotlin-readable-code`, `compose-multiplatform-ui` |
| Data and backend | `kmp-networking-ktor`, `kmp-supabase`, `web-admin-landing` |
| App setup | `kmp-environments`, `kmp-security`, `kmp-analytics-logging` |
| App features | `kmp-push-notifications`, `kmp-update-maintenance-alerts`, `kmp-legal-consent` |
| Graphics | `app-graphics-generator`, `store-assets-generator` |
| Distribution | `firebase-app-distribution`, `play-console-release`, `app-store-connect-release` |

The rules in CLAUDE.md apply to every task: TDD, MVI, the core/feature module layout and UAT/PROD environments. Claude never uploads to Firebase or the stores without your confirmation. Personal Claude settings go in `.claude/settings.local.json`, which git ignores.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/dev/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/), [Kotlin/Wasm](https://kotl.in/wasm/), [Ktor](https://ktor.io/), [Room KMP](https://developer.android.com/kotlin/multiplatform/room), [Firebase Kotlin SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk).
