---
name: kmp-feature-scaffold
description: Add a feature to a KMP + Compose Multiplatform app on the KMP-MultiModule-Template layout, with clean architecture, multi-module rules, Koin DI, MVI, Navigation 3, Material 3, keyboard handling, clean code and test-driven development. Use when adding a screen or feature.
---

# KMP + CMP Feature Scaffold

Add a feature to a multi-module KMP/CMP app so it builds on every target
(Android, iOS, Desktop/JVM, Web/Wasm), follows the house MVI pattern, and is
wired into navigation and DI.

The reference layout is
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
If the project in front of you differs, follow the project, but keep the same
ideas (one-way module graph, Contract-based MVI, feature never sees `app`).

**Work test-first (TDD).** For every behavior, write the failing test before the
code. Section 10 gives the order and patterns.

## 1. Read the project first

- `project.yaml`: the module list. Amper does **not** auto-discover modules.
- `libs.versions.toml`: the libraries in use. Do not add a second library for a
  job that one already does.
- `core/ui/module.yaml`: its `platforms:` list. A new feature must use the
  **same** list, or `app/shared` cannot depend on it.
- `ls core/`: which core modules exist (`common`, `ui`, `network`, `database`,
  `preferences`, `firebase`, `ads`, `ai`, `supabase`, and any extra such as `security`).
  Use an existing one before adding code anywhere else.
- `feature/flowers/` (backend-driven list + details) and `feature/about/`
  (smallest MVI shape): copy from whichever is closer to the new feature.
- If the project uses Gradle instead of Amper, the same structure applies, with
  `commonMain`/`androidMain`/`iosMain` instead of `src`/`src@android`/`src@ios`.

## 2. Module rules (never break these)

1. Dependencies point one way: `app` -> `feature` -> `core`. Inside `core`,
   everything points at `core/common` (only `core/ai` and `core/supabase` may
   also use `core/network`). The module map, with what each core module owns, is in
   `kmp-clean-architecture` section 2b.
2. A feature never depends on another feature or on `app`. If feature A must
   open feature B, A navigates by `Route` or raises a callback, and `app/shared`
   wires it.
3. Features own screens, the app owns navigation and the DI graph.
4. Only `core/ui`, `core/ads` (ad slots) and `feature/*` enable Compose.
5. Expose interfaces, hide SDKs (e.g. `Analytics`, not `FirebaseAnalytics`).
6. A feature that targets `wasmJs` cannot use `core/database` (Room) or
   `core/firebase`. Put those behind an interface and bind the implementation
   in the platform app modules (`app/*/src/*KoinModules.kt`).
7. A feature never depends on `core/firebase`. It calls the interfaces in
   `core/common` (`AnalyticsTracker`, `ErrorReporter`, ...).
8. Shared code the feature needs goes in the core module for that concern, not
   in the feature and not in `core/common` by default:

   | The feature needs | Put the shared part in | The feature keeps |
   |---|---|---|
   | a new icon, illustration, color, font, component | `core/ui` | the screen that uses it |
   | a new user-facing string | `core/common` `localization/` (`Strings` + every `StringsXx`) | nothing |
   | a route | `core/ui` `nav/Route.kt` | `<Name>Entry.kt` |
   | API calls | `core/network` only for client-wide plugins or envelopes | `Api`, `Source`, `ApiUrls`, DTOs |
   | remote data, login, file upload, live updates (Supabase) | `core/supabase` (client, `safeSupabaseCall`, `SupabaseTables`) + a migration in `supabase/migrations/` with RLS | `<Name>SupabaseSource`, DTOs (`kmp-supabase`) |
   | server logic with a secret (payment, AI key, sending push) | `supabase/functions/<name>/` | the call via `supabase.functions.invoke` |
   | a database table | `core/database` (`@Entity`, `@Dao`, migration, `AppDatabase` entry) | `FooLocalDataSource`, mapper |
   | a setting or flag | `core/preferences` (`AppPreferences` key + `Flow`) | the use case that reads it |
   | a token or secret | `core/security` (`SecureStorage`) | nothing |
   | push, remote config, crash reports, analytics (free Firebase only) | `core/firebase` implementation + `core/common` interface | calls to the interface |
   | an ad | `core/ads` (`AdBanner`, `InterstitialAds`) | where the slot is placed |
   | AI (chat, summarize, classify) | `core/ai` (`AiAssistant`, shared models) | its prompt, repository and use case |
   | a model used by two features | `core/model` | nothing |

## 2a. Standard feature modules

Use these module names, so every app built from the template looks the same.
Check `ls feature/` first and extend the matching module instead of adding a
second one for the same job.

| Module | Owns | Typical core modules |
|---|---|---|
| `feature/auth` | splash/session check, login, register (with the legal consent checkbox, `kmp-legal-consent`), OTP, forgot password, logout use case | network, security, preferences |
| `feature/home` | the first screen after login: dashboard, feed, shortcuts | network, database, ads |
| `feature/settings` | theme mode, language, notification preferences, privacy (with a Privacy Policy link), security toggles (biometric) | preferences, security |
| `feature/user` | the user's profile: view and edit, avatar, change password, delete account | network, security |
| `feature/chat` | conversations, messages, attachments, an AI assistant chat | network, database, ai |
| `feature/notice` | notifications and announcements: push list (latest 30), details, read state, push deep links (`kmp-push-notifications`) | network, database, security, preferences |
| `feature/more` | the "More" tab or drawer: about, licenses, help, app version, Privacy Policy and Terms & Conditions links (`kmp-legal-consent`), links to settings and user | none beyond common/ui |

Add more as the app needs them, named the same way: one lowercase singular
noun for the job (`feature/search`, `feature/onboarding`, `feature/payment`,
`feature/appstatus`, `feature/legal` for the legal re-acceptance overlay). Never `feature/common` or `feature/shared` (shared code
belongs in `core`, section 2 rule 8).

Naming inside a module follows the module name:
- Package and resources: `com.<org>.kmp.feature.user`,
  `com.<org>.kmp.feature.user.resources`.
- Entry and DI: `userEntry()` and `userModule`, one of each per module.
- Screens are named after what they show: `feature/user` has `ProfileScreen`,
  `EditProfileScreen`, and `Route.Profile`, `Route.EditProfile`.

The template's own `feature/flowers`, `feature/about` and `feature/notification`
are reference code to copy patterns from. In a new app, notification screens go
in `feature/notice` and the about screen goes in `feature/more`.

Features stay independent: logout lives in `feature/auth`. `feature/settings`
or `feature/more` raises an `onLogout` callback, and `app/shared` calls auth's
logout and resets the back stack. The same goes for "open profile" from `home`
or `more`: navigate by `Route.Profile`, never import `feature/user`.

## 2b. Clean code rules

Apply these to every file you write. Where existing template code breaks them
(commented-out blocks, `var` fields in data classes, `println`), do not copy that
into new code.

- **Single responsibility.** One public type per file, named after the file. A
  ViewModel handles events and state only; mapping goes in `mapper/`, rules go in
  use cases, I/O goes in data sources.
- **Immutability.** `val` everywhere, including data class fields. Use `List`, not
  `ArrayList`/`MutableList`, in public APIs and state. Update state only through
  `state.update { it.copy(...) }`.
- **Null safety.** Never use `!!`. Make domain fields non-null with sensible
  defaults, and handle nullability once at the mapper.
- **Visibility.** Mark classes that only the feature uses as `internal`. Only the
  `fooEntry()`, `fooModule` and anything another module truly needs stay public.
- **Naming.** Use intent-revealing names: `GetProfileUseCase`, `onEvent`,
  `isLoading`, `ProfileDto`/`ProfileModel`/`ProfileEntity`. Don't use
  abbreviations or names like `data2`, `temp` or `helper`.
- **No magic values.** Endpoints go in `ApiUrls`, user text goes in `Strings`,
  dimensions/colors/shapes come from the theme, and other constants go in a
  `const val`.
- **Small functions and composables.** If a function or composable outgrows about
  40 lines, or nests more than about three levels, extract it. Composables that
  only render should take state and lambdas, not the Contract.
- **Exhaustive `when`** over sealed types, and no `else -> {}`.
- **Leave no dead code.** No commented-out code, no unused imports, no TODOs
  unless the user asked for them. Use a logger, not `println`, if logging is needed.
- **No copyrighted material.** Don't add copyrighted images, fonts, text, logos
  or image links. Use Material Icons, the user's own assets, open-licensed
  (OFL/Apache/CC0) assets, or a placeholder, and write original copy and
  neutral sample data. When an icon, illustration or background is missing,
  generate an original vector in `core/ui`. Details are in the
  `compose-multiplatform-ui` skill (sections 2.0.1–2.0.2).
- **Errors.** Never swallow exceptions. They become `ResultNet.Error` at the data
  boundary, and the UI shows them through state or an Action.

## 2c. Clean architecture (inside each feature)

Every feature that owns data has three layers. Dependencies point inward only:

```
presentation  ──►  domain  ◄──  data
(Screen, VM)      (Model,       (Dto, Api, Source,
                   Repository    Mapper, RepositoryImpl)
                   interface,
                   UseCase)
```

- **domain** is the center and pure Kotlin. It holds `model/` (business models),
  `repository/` (interfaces only) and `usecase/` (one business action per class,
  `operator fun invoke`). It imports no Ktor, Room, Compose, Koin, or platform
  APIs. The one house exception: repository and use case signatures return
  `ResultNet<T, NetworkError>` from `core/network`. That type is plain Kotlin and
  is shared on purpose. Never import anything else from `core/network` into domain.
- **data** implements domain. `RepositoryImpl` implements the domain interface,
  calls the `Api` interface (implemented by a Ktor `Source`) or a DAO, wraps calls
  in `safeApiCall`, and maps `Dto`/`Entity` → `Model` in `mapper/`. Caching and
  "remote vs. local" decisions belong here, never in the ViewModel.
- **presentation** depends on domain only. A ViewModel receives **use cases**,
  never a `RepositoryImpl`, `Api`, `HttpClient` or DAO. It turns domain results
  into `State` and `Actions`. Screens see only the `Contract`.
- **di/** is the only place that knows every layer. It binds interfaces to
  implementations (`single<FooRepository> { FooRepositoryImpl(get()) }`), so
  layers can be swapped and faked in tests.

Use cases:
- Create one when there is business logic (validation, combining two repositories,
  sorting/filtering rules), or when more than one ViewModel needs the same action.
- A use case that only forwards to the repository is acceptable for consistency
  with the template, but keep it one line. Don't put UI or formatting logic in it.
- Name it verb + noun: `GetFlowersUseCase`, `MarkNoticeReadUseCase`,
  `ValidateEmailUseCase`.

UI-only features (e.g. `more`, `about`) skip `domain/` and `data/` and keep only
`presentation/` and `di/`. Don't create empty layers.

## 3. File layout

```
feature/<name>/
  module.yaml
  src/com/<org>/<name>/
    <Name>Entry.kt                       // Navigation3 entry: fun EntryProviderScope<NavKey>.<name>Entry()
    di/KoinModule.kt                     // val <name>Module
    presentation/
      <Name>Contract.kt
      <Name>State.kt
      <Name>Events.kt                    // user intents, UI -> VM
      <Name>Actions.kt                   // one-off effects, VM -> UI (snackbar, etc.)
      <Name>ViewModel.kt
      <Name>Screen.kt
    domain/                              // only for features with data
      model/<Name>Model.kt               // pure Kotlin
      repository/<Name>Repository.kt     // interface
      usecase/Get<Name>UseCase.kt        // operator fun invoke
    data/
      datasource/remote/api/<Name>Api.kt         // interface
      datasource/remote/source/<Name>Source.kt   // Ktor implementation
      datasource/remote/source/ApiUrls.kt
      datasource/remote/dto/<Name>Dto.kt         // @Serializable
      mapper/<Name>Mapper.kt                     // Dto.toDomain(), Model.toDto()
      repository/<Name>RepositoryImpl.kt
  src@android/ src@ios/ ...              // only for real platform needs
```

Naming suffixes: `Dto` for network, `Entity` for the local DB, `Model` for domain.
Domain code never imports Ktor, Room, or Compose.

## 4. module.yaml

Start from `feature/notification/module.yaml` (network-backed) or
`feature/about/module.yaml` (UI only).

```yaml
product:
  type: kmp/lib
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64, wasmJs ]   # same as core/ui

dependencies:
  - //core/common
  - //core/ui
  - //core/network            # only if it calls the backend
  - //core/supabase           # only if its data uses Supabase (auth, tables, storage, realtime, functions)
  - //core/database           # only if it stores tables (not with wasmJs; see rule 6)
  - //core/preferences        # only if it reads or writes settings
  - //core/security           # only if it handles tokens or secrets
  - //core/ai                 # only if it uses AI
  - //core/ads                # only if a screen shows ads
  # never //core/firebase or another //feature/
  - $compose.runtime
  - $compose.foundation
  - $compose.material3
  - $compose.ui
  - $compose.components.resources
  - $compose.preview
  - $libs.androidx.lifecycle.viewmodel.compose
  - $libs.androidx.lifecycle.runtime.compose
  - $libs.androidx.lifecycle.viewmodel.nav3
  - $libs.androidx.nav3.ui
  - $libs.androidx.material3.adaptive
  - $libs.koin.core
  - $libs.koin.compose
  - $libs.koin.compose.viewmodel
  - $libs.kotlinx.serialization.json   # if it has DTOs
  - $libs.ktor.client.core             # if it has a remote source

dependencies@android:
  - $compose.uiTooling

settings:
  compose:
    enabled: true
    resources:
      packageName: com.<org>.kmp.feature.<name>.resources   # must be unique
      exposedAccessors: true
  kotlin:
    serialization:
      enabled: true                    # if it has DTOs
    freeCompilerArgs: [ -XXLanguage:+ExplicitBackingFields, -Xskip-prerelease-check ]
```

## 5. MVI presentation layer

### Contract: the only thing the screen sees
```kotlin
interface ProfileContract {
    val state: StateFlow<ProfileState>
    val actions: Flow<ProfileActions>
    fun onEvent(event: ProfileEvents)
}
```

### State, Events, Actions
```kotlin
data class ProfileState(
    val isLoading: Boolean = false,
    val profile: ProfileModel? = null,
    val errorMsg: String? = null,
)

sealed interface ProfileEvents {                 // UI -> VM
    data object Load : ProfileEvents
    data class NameChanged(val name: String) : ProfileEvents
}

sealed interface ProfileActions {                // VM -> UI, fire once
    data class ShowSnackbar(val message: String) : ProfileActions
}
```

### ViewModel with explicit backing fields
```kotlin
class ProfileViewModel(
    private val getProfileUseCase: GetProfileUseCase,
) : BaseViewModel(), ProfileContract {

    override final val state: StateFlow<ProfileState> field = MutableStateFlow(ProfileState())

    // replay = 0: don't re-deliver after config change; extraBufferCapacity = 1: don't drop
    // an action emitted before the screen starts collecting.
    override final val actions: Flow<ProfileActions> field =
        MutableSharedFlow<ProfileActions>(replay = 0, extraBufferCapacity = 1)

    init { onEvent(ProfileEvents.Load) }

    override fun onEvent(event: ProfileEvents) {
        when (event) {
            ProfileEvents.Load -> load()
            is ProfileEvents.NameChanged -> state.update { it.copy(/* ... */) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, errorMsg = null) }
            getProfileUseCase()
                .onSuccess { data -> state.update { it.copy(isLoading = false, profile = data) } }
                .onError { err ->
                    state.update { it.copy(isLoading = false, errorMsg = err.errMessage) }
                    actions.emit(ProfileActions.ShowSnackbar(err.errMessage))
                }
        }
    }
}
```
- Extend `BaseViewModel` from `core/common`.
- Make `when (event)` exhaustive. Avoid `else -> {}` so a new event cannot be
  silently ignored.
- No `GlobalScope`, `runBlocking`, or explicit `Dispatchers.IO` in shared code
  (`Dispatchers.IO` can resolve as internal on Kotlin/Native).

### Screen
```kotlin
@Composable
fun ProfileScreen(contract: ProfileContract) {
    val env = rememberScreenEnv()                       // env.navigate(Route.X), env.strings.*
    val state by contract.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        contract.actions.collect { action ->
            when (action) {
                is ProfileActions.ShowSnackbar -> { /* show it */ }
            }
        }
    }

    ProfileContent(state = state, onEvent = contract::onEvent, onOpenDetails = { id ->
        env.navigate(Route.ProfileDetails(Route.ProfileDetails.Data(id)))
    })
    ProgressUi(state.isLoading)
}

@Composable
fun ProfileContent(state: ProfileState, onEvent: (ProfileEvents) -> Unit, onOpenDetails: (Int) -> Unit) {
    /* stateless, previewable */
}
```
- Reuse `core/ui` components (`ProgressUi`, `ErrorMessageWithIcon`,
  `MyAsyncImage`, `MyAlertDialog`) and `MaterialTheme` colors and typography.
  Don't hardcode `Color(0xFF...)` in features.
- User-facing text comes from `env.strings`. To add a string, add it to
  `Strings` and to every implementation (`StringsEn`, `StringsBn`) in
  `core/common/.../localization/`.

### Material 3
- Use Material 3 only (`androidx.compose.material3.*`), never `material` (M2)
  components. Icons come from `material-icons-extended`, which is already in
  the catalog.
- The theme lives in `core/ui/theme` (`AppTheme`, `AppColorScheme`, `Type`,
  `Shape`, `AppFont`). Read from `MaterialTheme.colorScheme`, `.typography`
  and `.shapes`. Never hardcode `Color(0xFF...)`, font sizes or corner radii in
  a feature. If a token is missing, add it to `core/ui/theme`.
- Use semantic color pairs (`primary`/`onPrimary`, `surface`/`onSurface`,
  `surfaceVariant`/`onSurfaceVariant`, `error`/`onError`) so dark mode works
  automatically. Check every new screen in both light and dark.
- Use M3 components: `Card`/`ElevatedCard`, `Button`/`FilledTonalButton`/
  `OutlinedButton`/`TextButton`, `OutlinedTextField`, `ListItem`, `TopAppBar`,
  `ModalBottomSheet`, `AlertDialog` (or `MyAlertDialog` from `core/ui`),
  `SnackbarHost`. The screen scaffold is `AppScaffold` in `core/ui`. Don't nest a
  second `Scaffold` inside a feature.
- For list/detail screens, use `TwoPaneScene.twoPane()` metadata and M3 adaptive
  (`currentWindowAdaptiveInfo()`). Don't branch on platform to change layout.
  Branch on window size.
- Keep touch targets at least 48.dp, and give icon-only buttons a
  `contentDescription` from `Strings`.

### Keyboard: IME action buttons and tap-outside to dismiss
Every screen with a text field must do both of these.

**1. Add a tap-outside helper to `core/ui`** if it isn't there yet
(e.g. `core/ui/src/com/<org>/ui/customUI/KeyboardUi.kt`), so every feature
reuses it:
```kotlin
/** Clears focus (and so hides the soft keyboard) when the user taps empty space. */
@Composable
fun Modifier.clearFocusOnTapOutside(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}
```
Apply it to the **root** container of the screen. Clickable children (buttons,
fields, list items) consume their own taps first, so they keep working:
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .clearFocusOnTapOutside()
        .imePadding()                 // keep the focused field above the keyboard
        .verticalScroll(rememberScrollState())
) { /* fields */ }
```

**2. Set an IME action on every field.** Use `Next` to move between fields, and
`Done` (or `Search`/`Send`/`Go`) on the last field to hide the keyboard and submit:
```kotlin
val focusManager = LocalFocusManager.current

OutlinedTextField(
    value = state.email,
    onValueChange = { onEvent(LoginEvents.EmailChanged(it)) },
    label = { Text(env.strings.email) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
    ),
    keyboardActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Down) },
    ),
)

OutlinedTextField(
    value = state.password,
    onValueChange = { onEvent(LoginEvents.PasswordChanged(it)) },
    label = { Text(env.strings.password) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
    ),
    keyboardActions = KeyboardActions(
        onDone = {
            focusManager.clearFocus()
            onEvent(LoginEvents.Submit)
        },
    ),
)
```
- Set `singleLine = true` on any field that should show an action key. A
  multi-line field shows a newline key instead.
- The iOS number and phone pads have **no return key**. On iOS, tap-outside is
  the only way to dismiss them, so never skip it on screens with numeric fields.
- Clear focus before navigating away or opening a dialog, so the keyboard
  doesn't stay up over the next screen.
- Text values go through `State` and `Events`, like any other input. Don't keep a
  separate `remember { mutableStateOf("") }` copy in the composable.

## 6. Data layer (backend-driven features)

```kotlin
interface ProfileApi { suspend fun getProfile(): ProfileDto }

class ProfileSource(private val client: HttpClient) : ProfileApi {
    override suspend fun getProfile(): ProfileDto =
        client.get { url { encodedPath = ApiUrls.PROFILE } }.body()
}

class ProfileRepositoryImpl(private val api: ProfileApi) : ProfileRepository {
    override suspend fun getProfile(): ResultNet<ProfileModel, NetworkError> =
        safeApiCall { api.getProfile().toDomain() }
}

class GetProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): ResultNet<ProfileModel, NetworkError> = repository.getProfile()
}
```
- Always wrap remote calls in `safeApiCall` and return `ResultNet<T, NetworkError>`.
  Don't let exceptions reach the ViewModel.
- Map at the repository: the DTO never leaves `data/`.
- Use the shared `HttpClient` from DI (`createHttpClient()` in `core/network`). Never
  create a client inside a feature.
- Prefer non-null domain fields with defaults over copying nullable DTO fields 1:1.

## 7. DI (Koin)

```kotlin
val userModule: Module = module {     // feature/user
    factory<ProfileApi> { ProfileSource(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }   // single only if screens share cached data
    factory { GetProfileUseCase(get()) }

    factory { ProfileViewModel(get()) }
    factory<ProfileContract> { get<ProfileViewModel>() }

    // A ViewModel with a nav argument:
    factory { (id: Int?) -> ProfileDetailsViewModel(id = id, getProfileById = get()) }
}
```

## 8. Navigation3 wiring

1. **Route** in `core/ui/.../nav/Route.kt`:
   ```kotlin
   @Serializable data object Profile : Route()
   @Serializable data class ProfileDetails(val data: Data? = null) : Route() {
       @Serializable data class Data(val profileId: Int? = null)
   }
   ```
2. **Register every new route** in `MainNavigation.kt`'s `SavedStateConfiguration`
   `polymorphic(NavKey::class) { subclass(...) }`. A route missing here crashes when
   the back stack is saved and restored (process death, rotation on some targets).
3. Optional: add a title case in `titleChangeByRoute`.
4. **Entry** in the feature:
   ```kotlin
   fun EntryProviderScope<NavKey>.userEntry() {        // feature/user/.../UserEntry.kt
       entry<Route.Profile>(metadata = TwoPaneScene.twoPane()) {
           val contract: ProfileContract = getKoin().get<ProfileViewModel>()
           ProfileScreen(contract)
       }
       entry<Route.ProfileDetails>(metadata = TwoPaneScene.twoPane()) { key ->
           val contract: ProfileDetailsContract = getKoin().get<ProfileDetailsViewModel>(
               parameters = { parametersOf(key.data?.profileId) }
           )
           ProfileDetailsScreen(contract)
       }
   }
   ```
   Pass only IDs and small primitives in a route. Load the full object from the
   repository in the details ViewModel.

## 9. Register the feature in the app

- `project.yaml`: add `feature/<name>` to `modules:`.
- `app/shared/module.yaml`: add `//feature/<name>`.
- `app/shared/src/AppEntryProvider.kt`: call `<name>Entry()`.
- `app/shared/src/KoinModules.kt`: add `<name>Module` to the `loadKoinModules(listOf(...))` list.

Forgetting the Koin module or the serializer registration are the two most
common runtime crashes. Check both.

## 10. Test-driven development (write tests FIRST)

Build every feature test-first, with **Red → Green → Refactor** for each piece of
behavior:
1. **Red:** write one small failing test for the next behavior. Run it and see it
   fail for the right reason (a missing class or a wrong value, not a typo).
2. **Green:** write the minimum production code that makes it pass.
3. **Refactor:** clean up names, duplication and structure with all tests green,
   then commit or move on.

Never write production logic that no failing test asked for. A bug fix starts
with a test that reproduces the bug.

### Order inside a feature (inside out, following clean architecture)
| Step | Test first (in `test/`) | Then implement |
|---|---|---|
| 1 | `<Name>MapperTest`: DTO → Model, nulls/defaults | `mapper/` |
| 2 | `<Verb><Name>UseCaseTest`: business rules, using `Fake<Name>Repository` | `domain/usecase/` + `domain/repository` interface |
| 3 | `<Name>RepositoryImplTest`: success, error, caching, using `Fake<Name>Api` that returns a DTO or throws | `data/repository/` |
| 4 | `<Name>ViewModelTest`: each `Event` → expected `State`, plus the expected `Actions` | `presentation/` (Contract, State, Events, Actions, ViewModel) |
| 5 | Optional `<Name>ScreenTest`: key UI behavior with `runComposeUiTest` | `<Name>Screen` / `<Name>Content` |

DI, navigation and the module wiring come last, once the tested pieces exist.

### Setup (Amper)
- Common tests go in `feature/<name>/test/`, and platform-only tests in
  `test@android/`, `test@ios/`, etc. Mirror the `src/` package structure.
- In `module.yaml`:
  ```yaml
  test-dependencies:
    - $libs.kotlinx.coroutines.test
    - $libs.turbine            # add to libs.versions.toml if missing
  ```
  `kotlin.test` (`@Test`, `assertEquals`) is available in common tests. Add it
  to the catalog only if the build says it is missing.
- Use hand-written **fakes**, not mocking libraries. Mocking is limited on
  Kotlin/Native and Wasm. Put shared fakes in `test/.../fake/`.

### Fakes
```kotlin
class FakeProfileRepository(
    var result: ResultNet<ProfileModel, NetworkError> = ResultNet.Success(sampleProfile),
) : ProfileRepository {
    var getProfileCalls = 0
        private set
    override suspend fun getProfile(): ResultNet<ProfileModel, NetworkError> {
        getProfileCalls++
        return result
    }
}

class FakeProfileApi(var dto: ProfileDto = sampleDto, var error: Throwable? = null) : ProfileApi {
    override suspend fun getProfile(): ProfileDto = error?.let { throw it } ?: dto
}
```

### Example tests (one behavior per test, named in plain language)
```kotlin
class ProfileRepositoryImplTest {
    @Test
    fun returnsNoInternetErrorOnIOException() = runTest {
        val repo = ProfileRepositoryImpl(FakeProfileApi(error = IOException("offline")))
        val result = repo.getProfile()
        val error = assertIs<ResultNet.Error<NetworkError>>(result).error
        assertIs<NetworkError.NoInternet>(error)
    }
}

class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadSuccessShowsProfileAndStopsLoading() = runTest(dispatcher) {
        val vm = ProfileViewModel(GetProfileUseCase(FakeProfileRepository()))
        advanceUntilIdle()
        assertEquals(sampleProfile, vm.state.value.profile)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadErrorEmitsSnackbarAction() = runTest(dispatcher) {
        val repo = FakeProfileRepository(result = ResultNet.Error(NetworkError.NoInternet()))
        val vm = ProfileViewModel(GetProfileUseCase(repo))
        vm.actions.test {                              // Turbine
            advanceUntilIdle()
            assertIs<ProfileActions.ShowSnackbar>(awaitItem())
        }
    }
}
```

### Rules
- Test behavior through public APIs (`onEvent`, `state`, `actions`, `invoke`),
  not private functions or implementation details.
- Arrange / Act / Assert, one logical assertion group per test, and no
  `Thread.sleep` or real delays. Use the test dispatcher and `advanceUntilIdle()`.
- No real network, database or clock in unit tests. Inject them as interfaces,
  so fakes can replace them.
- Cover the four UI states: loading, success, empty and error. Cover every
  `Events` branch.
- If code is hard to test, treat it as a design smell (a hidden dependency, too
  many responsibilities). Fix the design, don't skip the test.
- Never delete, skip (`@Ignore`) or weaken a test to make the build green.

## 11. Verify

- `./kotlin build` (whole project). This compiles every platform, including
  Kotlin/Native and Wasm, which catch errors JVM does not.
- `./kotlin test -m <name>` if tests were added.
- Run at least one app: `./kotlin run -m androidApp` or `-m desktopApp`.

If you cannot run the toolchain, say so plainly and give the user these commands.

## 12. Show the result: screenshots and screen recordings (required)

A feature is not done until you hand the user **visual proof**. Capture the real
app, and save the files in `docs/screenshots/<feature>/` (or the project's
equivalent). Attach them to your final reply or PR.

### What to capture
- **Screenshots:** every UI state (loading, empty, error, content), in light
  **and** dark, on a phone size and on a wide window (tablet, desktop or web),
  plus any dialog (`MyAlertDialog`) the feature shows.
- **One short recording (10–30 s)** of the main flow: open the screen, type
  (show the IME `Next`/`Done` keys and tap-outside dismiss), submit, and see the
  result and navigation.
- Name files clearly: `android-light-content.png`, `ios-dark-error.png`,
  `desktop-wide-content.png`, `android-flow.mp4`.

### How to capture
**Android (emulator or device)**
```bash
./kotlin run -m androidApp
adb shell cmd uimode night no        # or "yes" for dark
adb exec-out screencap -p > docs/screenshots/<feature>/android-light-content.png
adb shell screenrecord --time-limit 30 /sdcard/flow.mp4   # perform the flow, then:
adb pull /sdcard/flow.mp4 docs/screenshots/<feature>/android-flow.mp4
```

**iOS (simulator, macOS only)**
```bash
./kotlin run -m iosApp
xcrun simctl ui booted appearance light        # or dark
xcrun simctl io booted screenshot docs/screenshots/<feature>/ios-light-content.png
xcrun simctl io booted recordVideo docs/screenshots/<feature>/ios-flow.mp4   # Ctrl+C to stop
```

**Desktop / headless (works on Linux and CI, with no device)**
Render the stateless `FooContent` straight to PNG in a JVM test (`test@jvm/`).
This is also the fallback when no emulator or simulator is available:
```kotlin
@Test
fun captureProfileScreens() {
    listOf(false to "light", true to "dark").forEach { (dark, name) ->
        val scene = ImageComposeScene(width = 1080, height = 2340, density = Density(3f)) {
            AppTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                ProfileContent(state = ProfileState(profile = sampleProfile), onEvent = {})
            }
        }
        val png = scene.render().encodeToData()!!.bytes
        File("docs/screenshots/user/desktop-$name-content.png").apply { parentFile.mkdirs() }.writeBytes(png)
        scene.close()
    }
}
```
For the running desktop app (`./kotlin run -m desktopApp`), use the OS screenshot
tool or a screen recorder.

**Web (Wasm)**
Run `./kotlin run -m webApp` and capture with Playwright (Chromium) at phone width
(390×844) and desktop width (1440×900), using `page.screenshot()` and
`recordVideo` for the flow.

### Rules
- Capture the **real** build. Never mock up, edit or fake a screenshot. Sample
  data must be neutral and non-copyrighted.
- Before you send them, check the images yourself: no clipped text, overlaps,
  wrong colors in dark mode, or missing strings, and the keyboard doesn't hide
  the focused field.
- If a platform can't be captured in your environment (e.g. no macOS for iOS),
  say so plainly. Provide what you could capture (headless renders at minimum),
  and give the user the exact commands above to capture the rest.

## 13. Report added and changed files (required)

End every feature with a file report, so the user can review the change at a glance.
Build it from `git status --porcelain` / `git diff --stat` (or from your own record
of edits when there is no git). Never write it from memory.

Group it by module, and keep **Added**, **Changed** and **Deleted** separate:

```markdown
### Files added (12)
| Module | File | Purpose |
|---|---|---|
| feature/user | `module.yaml` | new feature module |
| feature/user | `src/.../presentation/ProfileViewModel.kt` | MVI ViewModel |
| feature/user | `test/.../ProfileViewModelTest.kt` | ViewModel tests (written first) |
| core/ui | `composeResources/drawable/ic_profile.xml` | generated vector icon |
| docs | `screenshots/user/android-light-content.png` | screenshot |

### Files changed (6)
| Module | File | What changed |
|---|---|---|
| (root) | `project.yaml` | added `feature/user` |
| app/shared | `module.yaml` | added `//feature/user` dependency |
| app/shared | `src/AppEntryProvider.kt` | called `userEntry()` |
| app/shared | `src/KoinModules.kt` | loaded `userModule` |
| core/ui | `src/.../nav/Route.kt`, `nav/MainNavigation.kt` | `Route.Profile` + serializer registration |
| core/common | `localization/Strings.kt`, `StringsEn.kt`, `StringsBn.kt` | 4 new strings |

### Files deleted (0)
```

Rules:
- List **every** file. Collapse only obviously repetitive groups (e.g. "6 files in
  `presentation/`"), and still give the count.
- Use paths relative to the repo root. In the "What changed" column, describe the
  change in a few words, not the code.
- Call out anything risky separately (a migration, a dependency version bump, a
  change to a shared `core` API, or generated or placeholder assets the user should
  replace).
- Include the same report in the PR description when you open a PR.

## 14. Add the feature to the release notes (required)

Every finished feature adds one line to the release notes of the version being
developed, so the notes are ready when the build is uploaded
(`firebase-app-distribution`, `play-console-release`,
`app-store-connect-release` all read this file).

1. Read `VERSION_NAME` from `config/version.properties`.
2. Append one line to `release-notes/<VERSION_NAME>.txt` at the repo root
   (e.g. `release-notes/1.1.1.txt`). Create the folder and file if missing.
3. If `release-notes/<VERSION_NAME>.bn.txt` exists, append the Bangla line
   there too, written properly, not machine-literal.

```text
- New: edit your profile photo and name
- Fixed: login stayed on the loading screen when offline
```

- Write for users: what they can do now, in plain words. No module names,
  class names, ticket numbers or "refactor". Start with `New:`, `Improved:` or
  `Fixed:`.
- One line per feature (or user-visible fix), under about 80 characters. Don't
  add a line for internal-only work (tests, refactors, build changes).
- Don't duplicate: if the feature already has a line, update it.
- Watch the total: Google Play allows **500 characters** per file. When the
  file passes about 400, tell the user it will need shortening before a Play
  upload.
- Show the added line in the file report (section 13) under "Files changed".

## Checklist
- [ ] Clean architecture: VM gets use cases only; domain has no framework imports; DTOs mapped in `data/`
- [ ] No copyrighted images, fonts, text or logos added
- [ ] TDD: tests written first (mapper → use case → repository → ViewModel), all green, no skipped tests
- [ ] Clean code rules followed (no `!!`, no dead code, `val`s, `internal` where possible)
- [ ] Material 3 components and theme tokens only; checked in dark mode
- [ ] Text fields: IME action set (`Next`/`Done`) and `clearFocusOnTapOutside()` on the screen root
- [ ] `platforms:` matches `core/ui`; unique `compose.resources.packageName`
- [ ] Module name is from section 2a (or a new single-noun name); entry and Koin module named after it
- [ ] Only the core modules the feature uses are listed; shared code went to the right core module (rule 8); no `//core/firebase`
- [ ] Contract / State / Events / Actions / ViewModel / Screen present
- [ ] No feature-to-feature or feature-to-app dependency
- [ ] Remote calls go through `safeApiCall` -> `ResultNet`; DTOs mapped in `data/`
- [ ] Strings added to `Strings`, `StringsEn`, `StringsBn`
- [ ] Route added **and** registered in the `serializersModule`
- [ ] Entry called from `AppEntryProvider`; Koin module loaded in `appModule`
- [ ] Module added to `project.yaml` and `app/shared/module.yaml`
- [ ] Screenshots (all states, light + dark, phone + wide) and a flow recording attached, or the gap stated with commands
- [ ] File report given: added / changed / deleted, grouped by module, from git
- [ ] One user-facing line appended to `release-notes/<VERSION_NAME>.txt` (and `.bn.txt` if present)
- [ ] `./kotlin build` passes (or commands handed to the user)
