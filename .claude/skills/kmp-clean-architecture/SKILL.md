---
name: kmp-clean-architecture
description: Apply and enforce Clean Architecture in a multi-module Kotlin Multiplatform + Compose Multiplatform app (KMP-MultiModule-Template layout). Use when designing a feature's layers, adding use cases/repositories/mappers, reviewing architecture, or refactoring code that breaks the dependency rule.
---

# Clean Architecture for KMP / CMP

Keep business logic independent of UI, frameworks, platforms and data sources, so it
is testable in `commonTest`, shared across Android, iOS, Desktop and Web, and easy to
change.

Reference layout:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
Its best example is `feature/flowers`. Companion skills: `kmp-feature-scaffold`
(builds a feature end to end, TDD, screenshots, file report) and
`compose-multiplatform-ui` (theme and UI).

## 1. The dependency rule

Source code dependencies point **inward only**. Inner layers know nothing about
outer ones.

```
          ┌─────────────────────────────────────────┐
          │  presentation   (Screen, Contract, VM)   │
          │   ┌─────────────────────────────────┐    │
          │   │  domain  (Model, UseCase,        │    │
          │   │          Repository interface)   │◄───┼── data (Dto, Entity, Api,
          │   └─────────────────────────────────┘    │         Source, Mapper,
          └─────────────────────────────────────────┘         RepositoryImpl)
   di/ (Koin) is the composition root: the only place that sees every layer
```

| Layer | May depend on | Must NOT depend on |
|---|---|---|
| **domain** | Kotlin stdlib, `kotlinx.coroutines` (Flow), `core/common`, and `ResultNet`/`NetworkError` types from `core/network` | Ktor, Room, Compose, Koin, `androidx.*`, platform APIs, `data`, `presentation` |
| **data** | domain, `core/network`, `core/supabase`, `core/database`, `core/preferences`, `core/security`, `core/ai`, serialization | presentation, Compose, `core/firebase` |
| **presentation** | domain, `core/ui`, `core/common`, `core/ads` (ad slots only), Compose, lifecycle ViewModel | data (`RepositoryImpl`, `Api`, DTO, DAO, `HttpClient`, `SupabaseClient`), `core/supabase` (except `feature/auth`'s native sign-in launcher), `core/firebase` |
| **di** | everything in the feature | (nothing depends on di except `app/shared`) |

**Quick check (run it during review):**
```bash
# domain must be framework-free
grep -rnE --include=*.kt "^import (io\.ktor|androidx\.|org\.koin|.*\.data\.|.*\.presentation\.)" feature/*/src | grep "/domain/"
# presentation must not touch data
grep -rnE --include=*.kt "^import .*\.data\." feature/*/src | grep "/presentation/"
# features must not import other features
grep -rnE "^\s*- //feature/" feature/*/module.yaml
```
All three should return nothing. The module-graph checks for `core` are in
section 2b. The one allowed `core/network` import in domain is
`ResultNet`/`NetworkError`.

## 2. Module-level architecture (multi-module)

Clean architecture also applies **between modules**:

1. `app` → `feature` → `core`, and inside `core` everything points at `core/common`.
   Section 2b lists every core module, what it owns, and the only allowed edges.
2. **Use the standard feature names** (`feature/auth`, `home`, `settings`,
   `user`, `chat`, `notice`, `more`, ...) from `kmp-feature-scaffold` section 2a.
3. **Features never depend on each other.** If feature A needs to open feature B,
   it navigates by `Route` (in `core/ui`) or raises a callback, and `app/shared`
   wires it. If A needs B's data, extract the shared domain to a `core` module
   (`core/model`). Don't import B.
4. **`core` modules expose interfaces, hide SDKs.** For example, `Analytics` lives
   in `core/common`, and `FirebaseAnalytics` stays inside `core/firebase`. Bind
   the implementation per platform in `app/*/src/*KoinModules.kt`, with
   `NoOpAnalytics` where unsupported.
5. **Platform lists are constraints.** A wasm-targeting feature can't use
   `core/database` (Room) or `core/firebase`. Depend on a domain interface, and
   bind a Room-backed implementation only in the apps that support it.
   `core/supabase` runs on every platform, wasmJs included, so remote data,
   auth and storage work on Web too (`kmp-supabase`).
6. Only `core/ui`, `core/ads` (for its ad slot composables) and `feature/*`
   enable Compose. Data and domain code stays reusable, including by `server`.

## 2b. Core module map

`core/` holds shared infrastructure, **one module per concern**. A module puts
small, SDK-free interfaces in `src/`, and keeps the SDK and platform code inside
itself (`src@android/`, `src@ios/`, ...). Code goes in the module whose concern it
is, never in `core/common` "because everyone can see it".

| Module | Owns | Never put here | Depends on (core) | Platforms |
|---|---|---|---|---|
| `core/common` | pure Kotlin shared by all: `BaseViewModel`, `Strings`/`StringsEn`/`StringsBn` + `Language`, `AppEnvironment` + `GeneratedEnvironment`, `AppPlatform`, dispatchers, number/date helpers, and the SDK-free contracts other modules implement (`Analytics`, `AnalyticsTracker`, `AppLogger`, `ErrorReporter`, `NoOp*`) | any SDK (Ktor, Room, Firebase, AdMob), I/O, Compose UI | nothing | all |
| `core/ui` | `AppTheme`, colors, `Type`, `Resources` (all drawables and fonts in `composeResources`), components (`AppScaffold`, `MyAlertDialog`, `AppButton`, `AppVersionText`), illustrations, `Route` + nav serializers, `LocalizationManager`, `ScreenEnv`, click/screen tracking helpers | HTTP, database, preferences reads, business rules, SDK calls | `core/common` | all |
| `core/network` | `createHttpClient`, `NetworkConfig`, `ResultNet`, `NetworkError`, `safeApiCall`, `ApiEnvelope`, `TokenProvider` (interface), engine config incl. certificate pinning, `ApiTelemetry`, `MaintenanceSignal`, `RemoteErrorReporter` | feature endpoints and DTOs (they stay in the feature's `data/`), token storage | `core/common` | all |
| `core/database` | Room `AppDatabase`, every `@Entity` and `@Dao`, migrations, the per-platform database builder | network calls, domain models, mapping | `core/common` | android, iOS, jvm (no wasmJs) |
| `core/preferences` | non-sensitive key-value settings as `Flow`s: theme mode, language, onboarding seen, snooze and dismiss flags (`AppPreferences`) | tokens, passwords, PII (they go to `core/security`) | `core/common` | all |
| `core/supabase` | the one `SupabaseClient` (Auth, Postgrest, Storage, Realtime, Functions), `SupabaseConfig`, `safeSupabaseCall`, `SupabaseTables`, `AuthSessionSource`, native sign-in launcher (`kmp-supabase`) | feature queries and DTOs (they stay in the feature's `data/`), the session store (`SecureSessionManager` is in `app/shared`), the service-role key | `core/common`, `core/network` | all |
| `core/firebase` | Firebase init (`FirebaseConfig`, `GeneratedFirebaseConfig`), `FirebaseAnalytics : Analytics`, Crashlytics `ErrorReporter`, Remote Config, FCM push token: **free Firebase products only** | interfaces that features call (those live in `core/common`); Firestore, Realtime Database, Firebase Auth, Cloud Storage, Cloud Functions (use `core/supabase`) | `core/common` | android (iOS once the SDK is linked), never wasmJs |
| `core/ads` | `AdsConfig` (app and unit IDs from the environment), SDK init after consent (UMP), `AdBanner` slot composable, `InterstitialAds` / `RewardedAds` interfaces, no-op on jvm and wasmJs | hardcoded unit IDs, ad decisions inside features beyond placing a slot | `core/common` | all (no-op where the SDK is missing) |
| `core/ai` | AI behind interfaces: `AiAssistant` (complete, stream as `Flow`), request/response models, prompt templates, on-device models in `src@android`/`src@ios`, remote calls through **your own backend** | provider API keys (never ship them in the app), UI, feature prompts that only one feature uses | `core/common`, `core/network` | all |

Add more modules when a new concern appears, with the same shape. Known ones:
- `core/security`: `SecureStorage`, `DeviceIntegrity`, `ScreenSecurity`,
  `BiometricAuth` (`kmp-security`). Depends on `core/common`.
- `core/model`: domain models shared by two or more features. Pure Kotlin, no
  dependencies on other core modules.

To add one: one concern only, interfaces in `src/`, depend on `core/common` only,
register it in `project.yaml`, and keep `core/ui`'s platform list (with no-op
`actual`s where a platform has no SDK) or document why not.

**Allowed edges, and nothing else:**
- every core module → `core/common`
- `core/ai` → `core/network` (shared `HttpClient`, `ResultNet`)
- `core/supabase` → `core/network` (shared engine with pinning, `ResultNet`, `NetworkError`)

So `core/ui` never reads `core/preferences` (the app root reads the theme mode
and language and passes them in), and `core/security` doesn't depend on
`core/network` (`app/shared` binds `TokenProvider` to a `SecureStorage`-backed
implementation).

**Who may depend on what:**

| Consumer | May use |
|---|---|
| `feature/*` presentation | `core/common`, `core/ui`, `core/ads` |
| `feature/*` data | `core/network`, `core/supabase`, `core/database`, `core/preferences`, `core/security`, `core/ai`, `core/model` |
| `feature/*` domain | `core/common`, `core/model`, `ResultNet`/`NetworkError` from `core/network` |
| `app/shared` | every core module it wires except `core/firebase` (it targets wasmJs) |
| `app/androidApp`, `app/iosApp` | `core/firebase` and other platform-only bindings |

No feature depends on `core/firebase`. Features call the `core/common`
interfaces, and the platform app modules bind the Firebase implementations.

**Checks:**
```bash
# core → core edges: only core/common, plus core/ai and core/supabase → core/network
grep -nE "^\s*- //core/" core/*/module.yaml | grep -vE "//core/common\s*$" | grep -vE "^core/(ai|supabase)/module.yaml:.*//core/network(: exported)?\s*$"
# no feature depends on core/firebase
grep -nE "^\s*- //core/firebase" feature/*/module.yaml
# SDKs stay in their module
grep -rnlE "^import (com\.google\.firebase|dev\.gitlive\.firebase)" feature/ core/ | grep -v "^core/firebase/"
grep -rnlE "^import com\.google\.android\.gms\.ads" feature/ core/ | grep -v "^core/ads/"
grep -rnlE "^import androidx\.room" feature/ core/ | grep -v "^core/database/"
# supabase-kt only in core/supabase and feature data (plus auth's native sign-in)
grep -rnlE "^import io\.github\.jan\.supabase" feature/ core/ | grep -vE "^core/supabase/|/data/" | grep -v "^feature/auth/.*/presentation/"
# no paid Firebase products
grep -rnlE "^import (com\.google\.firebase\.(firestore|database|auth|storage|functions)|dev\.gitlive\.firebase\.(firestore|database|auth|storage|functions))" app/ feature/ core/
```
All should return nothing.

## 3. Domain layer

```
domain/
  model/       // business entities: immutable data classes, enums, value classes
  repository/  // interfaces: what the feature needs, phrased in domain terms
  usecase/     // one business action per class
```

**Models**
- Immutable (`val`), non-null where the business says a value always exists, with
  sensible defaults. Use `List`, not `ArrayList`.
- Named for the business, with a `Model` suffix in this template: `FlowerModel`,
  `NotificationData`. No `@Serializable`, `@Entity` or `@SerialName`.
- Put invariants and pure rules on the model or a use case, never in the UI:
  ```kotlin
  data class CartModel(val items: List<CartItemModel>) {
      val total: Long get() = items.sumOf { it.unitPrice * it.quantity }
      val isEmpty: Boolean get() = items.isEmpty()
  }
  ```
- Use value classes for IDs and money when mix-ups are possible:
  `@JvmInline value class FlowerId(val value: Int)`.

**Repository interfaces**
- Describe *what*, not *how*: `getFlowers()`, `observeCart()`, `markRead(id)`. No
  `fetchFromApi()` or `queryDb()`.
- One-shot calls are `suspend` and return `ResultNet<T, NetworkError>`. Streams
  return `Flow<T>`.
- Return domain models only, never DTOs or entities.

**Use cases**
- One public `operator fun invoke(...)`. Name it verb + noun:
  `GetFlowersUseCase`, `MarkNoticeReadUseCase`, `ValidateEmailUseCase`.
- Depend only on repository interfaces and other use cases.
- Put business rules here: validation, combining repositories, sorting and
  filtering policies, permissions.
  ```kotlin
  class GetActiveFlowersUseCase(private val repository: FlowerListRepository) {
      suspend operator fun invoke(): ResultNet<List<FlowerModel>, NetworkError> =
          repository.getFlowers().map { it.flowers.filter(FlowerModel::isActive).sortedBy(FlowerModel::name) }
  }
  ```
- A pass-through use case (it only forwards) is fine for consistency with the
  template, but keep it to one line. Never put UI formatting, `Strings` or
  navigation in a use case.
- Use cases have no state. Register them with `factory { }` in Koin.

## 4. Data layer

```
data/
  datasource/remote/api/      // FooApi interface (suspend fun getFoo(): FooDto)
  datasource/remote/source/   // FooSource(HttpClient) : FooApi, ApiUrls
                              // or FooSupabaseSource(SupabaseClient) : FooRemote (kmp-supabase)
  datasource/remote/dto/      // @Serializable FooDto
  datasource/local/           // FooLocalDataSource wrapping a core/database DAO or core/preferences
  mapper/                     // FooDto.toDomain(), FooEntity.toDomain(), FooModel.toEntity()
  repository/                 // FooRepositoryImpl : FooRepository
```

- **Three model types and no leaks:** `Dto` for network (in the feature),
  `Entity` for the database (in `core/database`, because Room's `AppDatabase`
  must list every entity), and `Model` for domain (in the feature). Mapping happens **only** in `mapper/` and is
  called by the repository. A DTO never crosses into domain or presentation.
- **Mappers are pure extension functions,** easy to unit test. Handle nulls and
  defaults there, once:
  ```kotlin
  fun MyJsonObject.toDomain() = FlowerModel(
      id = id ?: 0,
      name = name.orEmpty(),
      isActive = isActive ?: false,
      imageUrl = listOfNotNull(imgData?.baseUrl, imgData?.fileName).joinToString(""),
  )
  ```
- **Every remote call goes through `safeApiCall`** (Ktor) or
  **`safeSupabaseCall`** (supabase-kt), so exceptions become
  `ResultNet.Error(NetworkError.*)` at the boundary. Rethrow nothing to the
  ViewModel. Keep `CancellationException` cancelling.
- **Data sources are interfaces** (`FooApi`, `FooLocalDataSource`), so the
  repository can be tested with fakes.
- **The repository owns data policy:** caching, remote vs. local, refresh and
  merging. For offline-first, make the database the single source of truth:
  ```kotlin
  override fun observeFlowers(): Flow<List<FlowerModel>> =
      dao.observeAll().map { list -> list.map(FlowerEntity::toDomain) }

  override suspend fun refresh(): EmptyResult<NetworkError> = safeApiCall {
      dao.replaceAll(api.getFlowers().myJsonObject.map { it.toDomain().toEntity() })
  }
  ```
- Use `single { }` for a repository that holds a cache shared by several screens
  (like `FlowerListRepositoryImpl`). Otherwise use `factory { }`.

## 5. Presentation layer

- The ViewModel implements the feature's `Contract` (state, actions, `onEvent`)
  and receives **use cases only** in its constructor.
- It turns domain results into `State` and `Actions`. It never formats text with
  `Strings`, since that is UI. It exposes typed values, and the screen turns them
  into text.
- It maps errors to UI meaning:
  `NetworkError.NoInternet` → `state.error = UiError.Offline` → the screen shows
  `env.strings.noInternet`.
- The screen sees only the `Contract`, and the stateless `FooContent` sees only
  `State` and lambdas.
- Navigation arguments carry IDs and primitives only. The destination ViewModel
  loads the model through a use case (as `DetailsViewModel` does with
  `GetFlowerByIdUseCase`).

## 6. DI is the composition root

```kotlin
val flowersModule = module {
    // data
    factory<FlowerListApi> { FlowerListSource(get()) }
    single<FlowerListRepository> { FlowerListRepositoryImpl(get()) }   // interface → impl
    // domain
    factory { FlowerListUseCase(get()) }
    factory { GetFlowerByIdUseCase(get()) }
    // presentation
    factory { FlowerListViewModel(get()) }
    factory<FlowerListContract> { get<FlowerListViewModel>() }
    factory { (id: Int?) -> DetailsViewModel(id, get()) }
}
```
- Always bind to the **interface** type (`single<FooRepository> { FooRepositoryImpl(get()) }`).
- Shared infrastructure (`HttpClient`, `AppPreferences`, the database, `Analytics`,
  `SecureStorage`, `AiAssistant`, ads) is provided once in `app/shared` or the
  platform app modules, never created inside a feature. Each core module may
  export its own Koin module (`networkModule`, `databaseModule`, `adsModule`,
  `aiModule`), and `app/shared` includes them.
- No `KoinComponent` / `get()` calls inside domain or data classes. Use constructor
  injection only.

## 7. Error flow across layers

```
Ktor throws ──► safeApiCall ──► ResultNet.Error(NetworkError.X)   (data)
                                   │
                         use case passes or maps it               (domain)
                                   │
              ViewModel: state.update { error = UiError.from(X) } (presentation)
                                   │
              Screen: AppErrorState(env.strings.text(error), onRetry)  (UI)
```
Never swallow an error. Never show a raw exception message to the user.

## 8. Testing each layer (TDD, inside out)

| Layer | Test | Uses |
|---|---|---|
| mapper | DTO/Entity ↔ Model, nulls and defaults | plain values |
| use case | business rules | `FakeFooRepository` |
| repository | success, error mapping, caching, offline | `FakeFooApi`, `FakeFooDao` |
| ViewModel | each `Event` → `State` / `Actions` | real use cases + fake repos, test dispatcher, Turbine |

Because domain is pure Kotlin, its tests run on every target with no emulator.
If a class is hard to test, a dependency is hidden or pointing the wrong way.
Fix the architecture, not the test.

## 9. Anti-patterns to reject

- A ViewModel that takes `HttpClient`, `FooApi`, `FooRepositoryImpl` or a DAO.
- A DTO or `@Entity` class shown in the UI or returned from a repository interface.
- Domain importing Ktor, Room, Compose, Koin or `androidx`.
- Business rules (validation, totals, filtering) inside composables or ViewModels.
- One feature importing another feature module.
- A `Utils`/`Manager` god object that mixes I/O, rules and formatting.
- `GlobalScope`, `runBlocking` or `KoinComponent` lookups in data or domain.
- Repositories that return `null` or throw for errors instead of `ResultNet`.
- A shared "BaseRepository" that leaks framework types into domain.
- A `SupabaseClient`, `UserSession` or `PostgrestResult` outside `core/supabase` and feature `data/`.
- Firestore, Realtime Database, Firebase Auth, Cloud Storage or Cloud Functions added to the app (use Supabase, `kmp-supabase`).

## 10. Auditing or refactoring an existing feature

1. Run the grep checks in section 1, and list every violation with `file:line`.
2. Write characterization tests for current behavior (TDD safety net).
3. Fix from the inside out: introduce domain models and mappers, then repository
   interfaces, then use cases, then switch the ViewModel to use cases, then rewire DI.
4. Keep each step compiling (`./kotlin build`) and tests green.
5. Finish with the file report (added, changed, deleted) and screenshots that
   show the UI is unchanged, as in `kmp-feature-scaffold` sections 12–13.

## Review checklist

- [ ] Dependency rule holds; grep checks return nothing (except allowed `ResultNet`)
- [ ] Domain is pure Kotlin: immutable models, repository interfaces, single-purpose use cases
- [ ] Data maps Dto/Entity ↔ Model only in `mapper/`; all remote calls use `safeApiCall` or `safeSupabaseCall`
- [ ] Repository owns caching and source policy; data sources are interfaces
- [ ] ViewModel receives use cases only; no `Strings` or formatting in VM or use cases
- [ ] No feature-to-feature dependency; shared domain extracted to `core/model`
- [ ] Each piece of core code sits in its module from section 2b; only allowed core edges; no feature depends on `core/firebase`; Firebase used only for its free products
- [ ] Koin binds interfaces → implementations; constructor injection only
- [ ] Mapper, use case, repository and ViewModel tests written first and green
