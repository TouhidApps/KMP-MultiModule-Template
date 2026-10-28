---
name: kmp-analytics-logging
description: Add analytics and logging to a KMP + CMP app (KMP-MultiModule-Template / Amper / Koin / Navigation3) - a screen_view for every navigation, a ui_click for every click, business events and tagged logs in every feature and core module, API telemetry in Ktor, and app errors sent to the backend error panel. Use when adding a screen, a button, a feature or a core module, or when asked about analytics, tracking, logging, crash or error reporting.
---

# Analytics and Logging (KMP + CMP)

Every feature and core module in the app reports what happens in it:

- **Navigation:** every change of the top route logs one `screen_view`. This is
  done once, in `MainNavigation`, so a new screen is tracked automatically.
- **Clicks:** every clickable element logs one `ui_click` with the screen and an
  element id. Features wrap clicks with `trackedClick("id") { ... }`.
- **Business events:** each feature has a `<Name>Analytics` object with its own
  events (`profile_saved`, `order_cancel_failed`), logged by its ViewModel.
- **Logs:** each module logs through one `AppLogger` with its own tag
  (`profile`, `profile.data`, `network`). Errors are also sent to the backend,
  where they show up in the admin error panel (`web-admin-landing` section 12c).

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
It already has an `Analytics` interface in `core/common`, `FirebaseAnalytics` in
`core/firebase` (Android) and `NoOpAnalytics`. This skill builds on those. Keep
their names. If the interface differs from section 2, add the missing methods
rather than renaming it.

Related skills: environments and Firebase projects per env follow
`kmp-environments`, the HTTP client follows `kmp-networking-ktor`, layering
follows `kmp-clean-architecture`, the feature workflow (TDD, screenshots, file
report) follows `kmp-feature-scaffold`, and the backend error log and admin
panel follow `web-admin-landing` section 12c.

## 1. Where things live

```
core/common/src/com/<org>/common/
  analytics/
    Analytics.kt                 # existing interface (SDK-free)
    NoOpAnalytics.kt             # existing
    AnalyticsEvent.kt            # name + params
    AnalyticsRules.kt            # Firebase limits + PII guard
    AnalyticsTracker.kt          # the ONLY thing features call
    CommonAnalytics.kt           # screen_view, ui_click, api_error
  log/
    AppLogger.kt                 # LogLevel, AppLogger, TaggedLogger, withTag()
    DefaultAppLogger.kt          # console (Kermit) + ErrorReporter
    LogRedactor.kt               # masks emails, tokens, phone numbers
    ErrorReporter.kt             # interface + NoOpErrorReporter
core/ui/src/com/<org>/ui/analytics/
  RouteAnalytics.kt              # Route.analyticsName (exhaustive when)
  ClickTracking.kt               # ClickTracker, LocalClickTracker, trackedClick()
  TrackedScreen.kt               # TrackedScreen, trackedEntry<K>()
  TrackScreenViews.kt            # screen_view from the back stack
core/network/src/.../network/
  ApiTelemetry.kt                # Ktor plugin: X-Request-Id, timings, api_error
  ApiPathMask.kt                 # /orders/01J9... -> /orders/{id}
  NetworkErrorLogging.kt         # NetworkError.logTo(), analyticsReason
  RemoteErrorReporter.kt         # batches errors to POST api/v1/client-errors
core/firebase/src/...            # FirebaseAnalytics (existing, Android only)
feature/<name>/src/.../presentation/<Name>Analytics.kt   # this feature's events
scripts/check_analytics.sh       # fails the build when a click or entry is untracked
```

Dependency direction stays `app → feature → core`, and inside `core` everything
points at `core/common`. Features depend on `AnalyticsTracker` and `AppLogger`,
never on Firebase, Kermit or Ktor plugins.

## 2. Core: events, rules and the tracker

```kotlin
// core/common/.../analytics/Analytics.kt  (SDK-free; FirebaseAnalytics implements it)
interface Analytics {
    fun logEvent(name: String, params: Map<String, Any>)
    fun setUserId(id: String?)
    fun setUserProperty(name: String, value: String?)
    fun setCollectionEnabled(enabled: Boolean)
}

// core/common/.../analytics/AnalyticsEvent.kt
/** Build events only in a feature's `<Name>Analytics` object or in [CommonAnalytics]. */
data class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap())
```

Firebase rules, checked before anything is sent:
```kotlin
// core/common/.../analytics/AnalyticsRules.kt
object AnalyticsRules {
    const val MAX_PARAMS = 25
    const val MAX_VALUE_LENGTH = 100
    private val SNAKE_CASE = Regex("^[a-z][a-z0-9_]{0,39}$")
    private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
    private val PERSONAL_KEYS = setOf(
        "email", "phone", "name", "full_name", "address", "password", "token", "otp", "card_number",
    )

    /** Returns why the event is invalid, or null when it may be sent. */
    fun check(event: AnalyticsEvent): String? = when {
        !isValidName(event.name) -> "event name must be snake_case, 1-40 chars, no reserved prefix"
        event.params.size > MAX_PARAMS -> "more than $MAX_PARAMS params"
        event.params.keys.any { !isValidName(it) } -> "param names must be snake_case, 1-40 chars"
        event.params.keys.any { it in PERSONAL_KEYS } -> "param looks like personal data"
        event.params.values.any { it !is String && it !is Number && it !is Boolean } ->
            "param values must be String, Number or Boolean"
        else -> null
    }

    fun normalize(params: Map<String, Any>): Map<String, Any> = params.mapValues { (_, value) ->
        when (value) {
            is String -> value.take(MAX_VALUE_LENGTH)
            is Boolean -> value.toString()
            is Int -> value.toLong()
            is Float -> value.toDouble()
            else -> value
        }
    }

    private fun isValidName(name: String): Boolean =
        SNAKE_CASE.matches(name) && RESERVED_PREFIXES.none { name.startsWith(it) }
}
```

The tracker is a final class, so tests build it with fakes instead of mocking it:
```kotlin
// core/common/.../analytics/AnalyticsTracker.kt
class AnalyticsTracker(
    private val analytics: Analytics,
    logger: AppLogger,
) {
    private val log = logger.withTag("analytics")

    fun track(event: AnalyticsEvent) {
        val problem = AnalyticsRules.check(event)
        if (problem != null) {
            log.e { "Dropped analytics event '${event.name}': $problem" }   // visible in UAT and in tests
            return
        }
        val params = AnalyticsRules.normalize(event.params)
        log.d { "event ${event.name} $params" }
        analytics.logEvent(event.name, params)
    }

    fun setUser(publicId: String?) = analytics.setUserId(publicId)   // backend ULID, never an email
}
```

Events every screen shares:
```kotlin
// core/common/.../analytics/CommonAnalytics.kt
object CommonAnalytics {
    fun screenView(screen: String, previous: String?) = AnalyticsEvent(
        name = "screen_view",                                 // Firebase's own event name
        params = buildMap {
            put("screen_name", screen)
            put("screen_class", screen)
            previous?.let { put("previous_screen", it) }
        },
    )

    fun click(screen: String, element: String, extra: Map<String, Any> = emptyMap()) = AnalyticsEvent(
        name = "ui_click",
        params = mapOf("screen" to screen, "element_id" to element) + extra,
    )

    fun apiError(method: String, path: String, status: Int, durationMs: Long) = AnalyticsEvent(
        name = "api_error",
        params = mapOf("method" to method, "path" to path, "status" to status, "duration_ms" to durationMs),
    )
}
```
- One `ui_click` event with an `element_id` param, not one event name per
  button. Firebase allows 500 distinct event names per project; element ids
  have no such limit.
- Register `screen`, `element_id`, `previous_screen`, `path` and `status` as
  custom dimensions in each Firebase project (UAT and PROD), or they can't be
  used in GA4 reports.

## 3. Core: logging

```kotlin
// core/common/.../log/AppLogger.kt
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface AppLogger {
    fun log(level: LogLevel, tag: String, throwable: Throwable?, message: () -> String)
}

class TaggedLogger(private val logger: AppLogger, private val tag: String) {
    fun d(message: () -> String) = logger.log(LogLevel.DEBUG, tag, null, message)
    fun i(message: () -> String) = logger.log(LogLevel.INFO, tag, null, message)
    fun w(throwable: Throwable? = null, message: () -> String) = logger.log(LogLevel.WARN, tag, throwable, message)
    fun e(throwable: Throwable? = null, message: () -> String) = logger.log(LogLevel.ERROR, tag, throwable, message)
}

fun AppLogger.withTag(tag: String): TaggedLogger = TaggedLogger(this, tag)
```
```kotlin
// core/common/.../log/ErrorReporter.kt
interface ErrorReporter {
    fun report(tag: String, message: String, throwable: Throwable?)
}

object NoOpErrorReporter : ErrorReporter {
    override fun report(tag: String, message: String, throwable: Throwable?) = Unit
}
```
```kotlin
// core/common/.../log/DefaultAppLogger.kt
class DefaultAppLogger(
    private val minConsoleLevel: LogLevel,              // DEBUG in UAT, WARN in PROD
    private val errorReporter: ErrorReporter,
) : AppLogger {

    override fun log(level: LogLevel, tag: String, throwable: Throwable?, message: () -> String) {
        val toConsole = level >= minConsoleLevel
        val toReporter = level == LogLevel.ERROR
        if (!toConsole && !toReporter) return           // the message lambda is never built

        val text = LogRedactor.redact(message())
        if (toConsole) writeToConsole(level, tag, text, throwable)
        if (toReporter) errorReporter.report(tag, text, throwable)
    }

    private fun writeToConsole(level: LogLevel, tag: String, text: String, throwable: Throwable?) {
        val kermit = co.touchlab.kermit.Logger.withTag(tag)
        when (level) {
            LogLevel.DEBUG -> kermit.d(throwable) { text }
            LogLevel.INFO -> kermit.i(throwable) { text }
            LogLevel.WARN -> kermit.w(throwable) { text }
            LogLevel.ERROR -> kermit.e(throwable) { text }
        }
    }
}
```
```kotlin
// core/common/.../log/LogRedactor.kt
object LogRedactor {
    private val rules = listOf(
        Regex("(?i)bearer\\s+[a-z0-9._~+/=-]+") to "Bearer ***",
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "***@***",
        Regex("\\+\\d[\\d ]{7,14}\\d") to "+***",
    )

    fun redact(text: String): String = rules.fold(text) { acc, (pattern, mask) -> pattern.replace(acc, mask) }
}
```
- Kermit (`co.touchlab:kermit`, latest stable, pinned in the version catalog)
  writes to Logcat, the Xcode console, stdout and the browser console. It is
  the only logging library, and only `DefaultAppLogger` imports it. Add it to
  `core/common/module.yaml` `dependencies:`.
- No `println`, no `Log.d`, no `NSLog` anywhere else.
- `ERROR` means "a developer must look at this". It is sent to the backend even
  in PROD. Expected failures (no internet, 401, validation) are `WARN`.
- Message lambdas are lazy, so `log.d { expensive() }` costs nothing in PROD.
- Never log tokens, passwords, OTPs, full request or response bodies, or
  personal data. The redactor is a safety net, not permission.

## 4. Navigation: one screen_view per route change

Give every route a stable analytics name. The `when` is exhaustive over the
sealed `Route`, so adding a route without a name doesn't compile. (If `Route`
isn't `sealed` yet, make it sealed.)
```kotlin
// core/ui/.../analytics/RouteAnalytics.kt
val Route.analyticsName: String
    get() = when (this) {
        Route.Home -> "home"
        Route.Profile -> "profile"
        is Route.ProfileDetails -> "profile_details"     // never put the id in the name
        Route.Settings -> "settings"
        Route.About -> "about"
    }
```

Log `screen_view` from the back stack, once, in `MainNavigation`:
```kotlin
// core/ui/.../analytics/TrackScreenViews.kt
@Composable
fun TrackScreenViews(backStack: List<NavKey>, tracker: AnalyticsTracker?) {
    var lastScreen by rememberSaveable { mutableStateOf<String?>(null) }   // no duplicate on rotation
    LaunchedEffect(backStack, tracker) {
        if (tracker == null) return@LaunchedEffect
        snapshotFlow { (backStack.lastOrNull() as? Route)?.analyticsName }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { screen ->
                if (screen == lastScreen) return@collect
                tracker.track(CommonAnalytics.screenView(screen, previous = lastScreen))
                lastScreen = screen
            }
    }
}

// MainNavigation.kt
val backStack = rememberNavBackStack(navConfig, Route.Home)
TrackScreenViews(backStack, LocalAnalyticsTracker.current)
NavDisplay(backStack = backStack, /* ... */)
```
- This covers forward navigation, back, drawer and bottom-bar switches and deep
  links, because each one changes the top of the back stack.
- Turn off Firebase's automatic screen reporting, or the single Activity shows
  up as one screen next to ours:
  - Android `app/androidApp/src/AndroidManifest.xml`, inside `<application>`:
    `<meta-data android:name="google_analytics_automatic_screen_reporting_enabled" android:value="false" />`
  - iOS `Info.plist`: `FirebaseAutomaticScreenReportingEnabled` = `NO`.
- If a screen needs context (e.g. a category), log a feature event when its data
  loads. Don't add params to `screen_view`.

## 5. Clicks: every clickable element is tracked

Each entry knows its screen name and provides a `ClickTracker` to its content:
```kotlin
// core/ui/.../analytics/ClickTracking.kt
val LocalAnalyticsTracker = staticCompositionLocalOf<AnalyticsTracker?> { null }   // set in App.kt from Koin

class ClickTracker(private val tracker: AnalyticsTracker?, val screen: String) {
    fun track(element: String, params: Map<String, Any> = emptyMap()) {
        tracker?.track(CommonAnalytics.click(screen, element, params))
    }
}

val LocalClickTracker = staticCompositionLocalOf { ClickTracker(tracker = null, screen = "unknown") }

/** Wraps [onClick] so it also logs `ui_click`. Use it for every onClick in features. */
@Composable
fun trackedClick(
    element: String,
    params: Map<String, Any> = emptyMap(),
    onClick: () -> Unit,
): () -> Unit {
    val clickTracker = LocalClickTracker.current
    val latestOnClick by rememberUpdatedState(onClick)
    val latestParams by rememberUpdatedState(params)
    return remember(clickTracker, element) {
        { clickTracker.track(element, latestParams); latestOnClick() }
    }
}
```
```kotlin
// core/ui/.../analytics/TrackedScreen.kt
@Composable
fun TrackedScreen(screen: String, content: @Composable () -> Unit) {
    val tracker = LocalAnalyticsTracker.current
    val clickTracker = remember(tracker, screen) { ClickTracker(tracker, screen) }
    CompositionLocalProvider(LocalClickTracker provides clickTracker, content = content)
}

/** Use instead of `entry<K>` in every feature, so clicks know their screen. */
inline fun <reified K : Route> EntryProviderScope<NavKey>.trackedEntry(
    metadata: Map<String, Any> = emptyMap(),
    noinline content: @Composable (K) -> Unit,
) {
    entry<K>(metadata = metadata) { key -> TrackedScreen(screen = key.analyticsName) { content(key) } }
}
```
Set the tracker once at the root:
```kotlin
// app/shared App.kt
CompositionLocalProvider(
    LocalAppEnvironment provides koinInject(),
    LocalAnalyticsTracker provides koinInject(),
) { MainNavigation() }
```
In a two-pane layout each pane is its own entry, so a click in the list pane is
reported with the list screen's name, not the detail screen's.

Use in a feature:
```kotlin
// feature/user/.../UserEntry.kt
fun EntryProviderScope<NavKey>.userEntry() {
    trackedEntry<Route.Profile>(metadata = TwoPaneScene.twoPane()) {
        val contract: ProfileContract = getKoin().get<ProfileViewModel>()
        ProfileScreen(contract)
    }
}

// ProfileContent (stateless)
Button(onClick = trackedClick("save_button") { onEvent(ProfileEvents.Save) }) { Text(strings.save) }
IconButton(onClick = trackedClick("edit_avatar_button") { onEvent(ProfileEvents.EditAvatar) }) { /* icon */ }
ListItem(
    modifier = Modifier.clickable(onClick = trackedClick("order_item", mapOf("position" to index)) { onOpen(order.id) }),
    headlineContent = { Text(order.title) },
)
Switch(checked = state.notifications, onCheckedChange = { on ->
    clickTracker.track("notifications_switch", mapOf("value" to on))   // val clickTracker = LocalClickTracker.current
    onEvent(ProfileEvents.NotificationsChanged(on))
})
```
Element id rules:
- snake_case, `<thing>_<kind>`: `save_button`, `order_item`, `price_filter_chip`,
  `terms_link`. Unique within a screen. Never translated, never the label text.
- Params may hold positions, counts, enum values and opaque ids. Never the text a
  user typed, names, emails or phone numbers.
- Track at the Material component (`Button`, `IconButton`, `Card`, `ListItem`,
  `FilterChip`, `NavigationBarItem`, ...). If your own composable forwards
  `onClick` to one, track inside it, not at both levels.
- Shared chrome is tracked once in `core/ui`: `AppScaffold` back button
  (`back_button`), drawer items (`drawer_<route>`), bottom bar items
  (`tab_<route>`).
- Text field typing is not a click. Track the submit (`search_submit`) as a
  feature event with a result count, not every keystroke.

## 6. Features: business events and logs

Each feature has one analytics object next to its ViewModel:
```kotlin
// feature/user/.../presentation/ProfileAnalytics.kt
internal object ProfileAnalytics {
    fun loaded(durationMs: Long) = AnalyticsEvent("profile_loaded", mapOf("duration_ms" to durationMs))
    fun saved(fieldsChanged: Int) = AnalyticsEvent("profile_saved", mapOf("fields_changed" to fieldsChanged))
    fun saveFailed(error: NetworkError) =
        AnalyticsEvent("profile_save_failed", mapOf("reason" to error.analyticsReason))
}
```
Event names are `<feature>_<object>_<past-tense verb>` (`order_cancelled`,
`cart_item_added`) or `<feature>_<action>_failed`. Name each one after an
outcome the business cares about, not after a function.

The ViewModel logs the outcome; the repository logs the technical failure:
```kotlin
class ProfileViewModel(
    private val getProfile: GetProfileUseCase,
    private val saveProfile: SaveProfileUseCase,
    private val tracker: AnalyticsTracker,
    logger: AppLogger,
) : BaseViewModel(), ProfileContract {

    private val log = logger.withTag("profile")
    /* state, actions, onEvent as in kmp-feature-scaffold section 5 */

    private fun save() {
        viewModelScope.launch {
            val draft = state.value.draft
            saveProfile(draft)
                .onSuccess {
                    tracker.track(ProfileAnalytics.saved(fieldsChanged = draft.changedFieldCount))
                    log.i { "Profile saved" }
                }
                .onError { error ->
                    tracker.track(ProfileAnalytics.saveFailed(error))
                    actions.emit(ProfileActions.ShowError(error))   // the screen maps NetworkError to Strings
                }
        }
    }
}

class ProfileRepositoryImpl(private val api: ProfileApi, logger: AppLogger) : ProfileRepository {
    private val log = logger.withTag("profile.data")

    override suspend fun save(draft: ProfileModel): ResultNet<ProfileModel, NetworkError> =
        safeApiCall { api.updateProfile(draft.toDto()).toDomain() }
            .onError { it.logTo(log, operation = "save") }
}
```
```kotlin
// core/network/.../NetworkErrorLogging.kt
/** Expected failures are WARN; bugs (bad JSON, unknown exceptions) are ERROR and reach the error panel. */
fun NetworkError.logTo(logger: TaggedLogger, operation: String) = when (this) {
    is NetworkError.Serialization, is NetworkError.Unknown -> logger.e { "$operation failed: $analyticsReason $log" }
    else -> logger.w { "$operation failed: $analyticsReason" }
}

val NetworkError.analyticsReason: String
    get() = when (this) {
        is NetworkError.NoInternet -> "no_internet"
        is NetworkError.RequestTimeout -> "timeout"
        is NetworkError.Unauthorized -> "unauthorized"
        is NetworkError.Conflict -> "conflict"
        is NetworkError.PayloadTooLarge -> "payload_too_large"
        is NetworkError.TooManyRequests -> "rate_limited"
        is NetworkError.ServerError -> "server_error"
        is NetworkError.Serialization -> "bad_response"
        is NetworkError.Unknown -> "unknown"
    }
```
Keep the `when` exhaustive. If `NetworkError` gains a subtype, this stops
compiling until it has a reason.

### What each module reports

| Module / layer | Logs (tag) | Analytics |
|---|---|---|
| Screen (Compose) | nothing | `ui_click` via `trackedClick` |
| ViewModel | `i` milestones, `w` recoverable problems (`<feature>`) | `<feature>_*` business events |
| Use case | nothing (pure); throw or return errors instead | none |
| Repository / data source | `w`/`e` via `logTo` with the operation name; `d` cache hit/miss (`<feature>.data`) | none |
| `core/network` | `d` each request, `w` each failure (`network`) | `api_error` |
| `core/database` | `e` migration or corruption, `w` slow queries (`database`) | none |
| `core/firebase` | `w` when Firebase isn't configured (`firebase`) | it is the sink |
| `core/preferences` | `w` read or write failures (`preferences`); never log the values | none |
| `core/security` | `w` integrity or biometric failures (`security`); never log secrets | `security_check_failed` (reason only) |
| `core/ads` | `d` load, `w` load failure (`ads`) | `ad_impression`, `ad_failed` |
| `core/ai` | `d` request size and latency, `w` failures (`ai`); never log prompts or replies | `ai_request` (feature, latency, success) |
| `core/ui` | nothing beyond click and screen tracking | `ui_click`, `screen_view` |
| `app/*` startup | `i` env name, version, platform (`app`) | user properties (section 9) |
| Navigation | `d` route changes (`navigation`) | `screen_view` |

## 7. Network telemetry (Ktor plugin)

```kotlin
// core/network/.../ApiTelemetry.kt
const val REQUEST_ID_HEADER = "X-Request-Id"

class ApiTelemetryConfig {
    lateinit var logger: AppLogger
    lateinit var tracker: AnalyticsTracker
}

@OptIn(ExperimentalUuidApi::class)
val ApiTelemetry = createClientPlugin("ApiTelemetry", ::ApiTelemetryConfig) {
    val log = pluginConfig.logger.withTag("network")
    val tracker = pluginConfig.tracker

    on(Send) { request ->
        val requestId = Uuid.random().toString()
        request.headers[REQUEST_ID_HEADER] = requestId               // same id appears in the backend error log
        val method = request.method.value
        val path = ApiPathMask.mask(request.url.encodedPath)
        val started = TimeSource.Monotonic.markNow()

        fun failed(status: Int) {
            val ms = started.elapsedNow().inWholeMilliseconds
            log.w { "$method $path -> ${if (status == 0) "no response" else status} in ${ms}ms (request $requestId)" }
            tracker.track(CommonAnalytics.apiError(method, path, status, ms))
        }

        try {
            val call = proceed(request)
            val status = call.response.status.value
            if (status >= 400) failed(status)
            else log.d { "$method $path -> $status in ${started.elapsedNow().inWholeMilliseconds}ms" }
            call
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpStatusException) {                           // thrown by HttpResponseValidator
            failed(e.code)
            throw e
        } catch (e: Exception) {
            failed(status = 0)
            throw e
        }
    }
}

// core/network/.../ApiPathMask.kt
object ApiPathMask {
    private val ID_SEGMENT = Regex("^(\\d+|[0-9A-HJKMNP-TV-Z]{26}|[0-9a-fA-F]{8}-[0-9a-fA-F-]{27})$")

    fun mask(path: String): String =
        path.split('/').joinToString("/") { segment -> if (ID_SEGMENT.matches(segment)) "{id}" else segment }
}
```
Install it in `createHttpClient` and pass the logger and tracker in:
```kotlin
fun createHttpClient(config: NetworkConfig, tokens: TokenProvider, logger: AppLogger, tracker: AnalyticsTracker) =
    HttpClient {
        /* defaultRequest, ContentNegotiation, HttpTimeout, HttpRequestRetry, Auth, HttpResponseValidator ... */
        install(ApiTelemetry) {
            this.logger = logger
            this.tracker = tracker
        }
    }
```
- Paths are masked, so `api/v1/orders/01J9Z...` and `api/v1/orders/01JA0...` are
  one `api_error` row in reports, and no id leaves the device as a dimension.
- Network failures are `WARN`, not `ERROR`. The backend already logs every API
  error it sees (`web-admin-landing` 12c); the app only reports what the backend
  can't see (bad JSON, crashes, unknown exceptions).
- Ktor's `Logging` plugin stays debug-only, as `kmp-networking-ktor` says.

## 8. App errors reach the admin error panel

`ErrorReporter` in PROD and UAT sends `ERROR` logs to the backend, which stores
them in the same error log as API errors, with `source` = `android`, `ios`,
`desktop` or `web`. Admins filter them by date in the error panel.

Contract (in `backend/contracts/openapi.yaml`, see `web-admin-landing` 12c):
```
POST api/v1/client-errors          # auth optional, rate limited, max 20 per request
{ "errors": [ { "occurredAt": "2026-10-28T06:30:00Z", "platform": "android", "appVersion": "1.1.1",
               "tag": "profile.data", "message": "save failed: bad_response ...",
               "exceptionClass": "SerializationException", "stackTrace": "...", "screen": "profile" } ] }
→ 202 { "data": { "accepted": 1 }, "error": null }
```
```kotlin
// core/network/.../RemoteErrorReporter.kt
@OptIn(ExperimentalTime::class)
class RemoteErrorReporter(
    private val client: HttpClient,
    private val env: AppEnvironment,
    private val platform: String,                     // "android", "ios", "desktop", "web"
    private val scope: CoroutineScope,                // app-wide, SupervisorJob + Dispatchers.Default
    private val clock: Clock = Clock.System,
) : ErrorReporter {

    private val queue = Channel<ClientErrorDto>(capacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init { scope.launch { sendForever() } }

    override fun report(tag: String, message: String, throwable: Throwable?) {
        queue.trySend(
            ClientErrorDto(
                occurredAt = clock.now().toString(),
                platform = platform,
                appVersion = env.versionName,
                tag = tag,
                message = message.take(1_000),
                exceptionClass = throwable?.let { it::class.simpleName },
                stackTrace = throwable?.stackTraceToString()?.take(8_000),
            )
        )
    }

    private suspend fun sendForever() {
        while (true) {
            val first = queue.receive()
            delay(5.seconds)                              // collect a burst into one request
            val batch = buildList {
                add(first)
                while (size < 20) add(queue.tryReceive().getOrNull() ?: break)
            }
            try {
                client.post(ApiUrls.CLIENT_ERRORS) { contentType(ContentType.Application.Json); setBody(ClientErrorsRequestDto(batch)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Dropped on purpose: never log here as ERROR, or a failure would report itself forever.
            }
        }
    }
}
```
- At most 50 errors are kept in memory, oldest dropped first. A burst never
  floods the backend or the battery.
- The reporter's own requests go through `ApiTelemetry` like any other, which
  only logs `WARN`, so there is no loop.
- **Crashes:** install an uncaught exception handler at startup
  (`Thread.setDefaultUncaughtExceptionHandler` on Android and desktop,
  `setUnhandledExceptionHook` on iOS). It writes the crash as JSON to
  `AppPreferences` (`pending_crash`) and lets the app die. On the next start,
  `RemoteErrorReporter` sends it with `tag = "crash"`.
- **Crashlytics is the crash report** (section 8b). It's free, so every app
  has it. The backend error panel keeps the app's `ERROR` logs.

## 8b. Firebase Crashlytics (free) on Amper

Amper can't apply the Crashlytics Gradle plugin, so do its two jobs by hand:
give the build a mapping file id, and upload the R8 mapping with the Firebase
CLI (already used by `firebase-app-distribution`).

**Android (`core/firebase`, android only)**
```yaml
# core/firebase/module.yaml
dependencies@android:
  - bom: com.google.firebase:firebase-bom:<current>
  - com.google.firebase:firebase-crashlytics
```
```kotlin
// core/firebase/src@android/.../CrashlyticsErrorReporter.kt
class CrashlyticsErrorReporter : ErrorReporter {
    private val crashlytics = FirebaseCrashlytics.getInstance()
    override fun report(tag: String, message: String, throwable: Throwable?) {
        crashlytics.log("$tag: ${LogRedactor.redact(message)}")
        crashlytics.recordException(throwable ?: NonFatalError("$tag: ${LogRedactor.redact(message)}"))
    }
    fun setUser(publicId: String?) = crashlytics.setUserId(publicId.orEmpty())
}
private class NonFatalError(message: String) : Exception(message)
```
Release steps (put them in `scripts/distribute.py` and the release workflow,
right after `use_env.py` and before the build):
```bash
# 1. new mapping file id for this build (writes a res/values string the SDK reads)
firebase crashlytics:mappingfile:generateid \
  --resource-file=app/androidApp/res/values/crashlytics_mapping_id.xml
# 2. build the release APK/AAB (R8 on), then upload its mapping to the ACTIVE env's Firebase app
firebase crashlytics:mappingfile:upload build/.../mapping.txt \
  --app=<mobilesdk_app_id from the active google-services.json> \
  --resource-file=app/androidApp/res/values/crashlytics_mapping_id.xml
```
- Git-ignore `crashlytics_mapping_id.xml`. Without it the SDK may refuse to
  start ("build ID is missing"), so `use_env.py` writes a placeholder id for
  local debug builds.
- Native (NDK) crashes need the plugin's symbol upload. Skip them unless the
  app has native code, and say so.

**iOS (Swift bridge).** Kotlin can't link the Firebase iOS SDK under Amper, so
Swift does the Crashlytics call and Kotlin hands it the crash:
```kotlin
// core/common/.../log/NativeCrashReporter.kt
interface NativeCrashReporter {                       // implemented in Swift
    fun record(tag: String, message: String, stackTrace: String, fatal: Boolean)
    fun setUser(publicId: String?)
}
// app/iosApp Kotlin entry: unhandled Kotlin exceptions reach Crashlytics before the app dies
setUnhandledExceptionHook { t ->
    nativeCrashReporter.record("crash", t.message.orEmpty(), t.stackTraceToString(), fatal = true)
    terminateWithUnhandledException(t)
}
```
```swift
// app/iosApp: FirebaseCrashlytics added with Swift Package Manager; FirebaseApp.configure() at launch
final class CrashlyticsBridge: NativeCrashReporter {
    func record(tag: String, message: String, stackTrace: String, fatal: Bool) {
        let model = ExceptionModel(name: fatal ? "KotlinCrash" : tag, reason: message)
        model.stackTrace = stackTrace.split(separator: "\n").prefix(64).map { StackFrame(symbol: String($0)) }
        Crashlytics.crashlytics().record(exceptionModel: model)
    }
    func setUser(publicId: String?) { Crashlytics.crashlytics().setUserID(publicId ?? "") }
}
```
Add the Crashlytics "Run Script" build phase in Xcode for dSYM upload. Pass the
bridge into Koin at startup (`initKoin(nativeCrashReporter = CrashlyticsBridge())`),
and bind `NoOp` on jvm and wasmJs.

**Wiring:** `ErrorReporter` becomes a `CompositeErrorReporter(crashlytics, remote)`
on Android and iOS, so each `ERROR` goes to Crashlytics and (when the app has
a backend error panel) to `RemoteErrorReporter`. Desktop and Web keep the
remote reporter only.

**Verify** in the **UAT** Firebase project: force a test crash in a UAT build
(`throw RuntimeException("Crashlytics test")` behind a debug-only button),
reopen the app, and check that the crash shows up with a readable stack trace
on both platforms. Tell the user what you saw. Don't claim it works before that.

## 9. Wiring (Koin) and environments

```kotlin
// app/shared appModule
single(named("app")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
single<ErrorReporter> {
    RemoteErrorReporter(client = get(), env = get(), platform = currentPlatformName(), scope = get(named("app")))
}
single<AppLogger> {
    DefaultAppLogger(
        minConsoleLevel = if (get<AppEnvironment>().isDebug) LogLevel.DEBUG else LogLevel.WARN,
        errorReporter = get(),
    )
}
single { AnalyticsTracker(analytics = get(), logger = get()) }
single<HttpClient> { createHttpClient(get(), get(), get(), get()) }
// Analytics itself is bound per platform (kmp-environments section 5):
// FirebaseAnalytics on Android when configured, NoOpAnalytics elsewhere.
```
`RemoteErrorReporter` needs the `HttpClient`, and the `HttpClient` needs the
logger. Koin resolves lazily, so this works as long as no one logs an `ERROR`
while the client is being built. If it ever loops, make the reporter take a
`Lazy<HttpClient>` (`inject()`).

Environments (`kmp-environments`):
- UAT and PROD already use separate Firebase projects, so their analytics never
  mix. Errors go to the active `BASE_URL`, so UAT errors land in the UAT admin
  panel and PROD errors in the PROD one.
- Console logs are `DEBUG` in UAT and `WARN` in PROD. `ERROR`s are reported in both.
- On startup, set user properties: `app_env` (`uat` / `prod`), and after login
  `setUser(publicId)` with an opaque id (the Supabase auth user id, or the
  backend's ULID), never an email or phone. Clear it on logout
  (`setUser(null)`).
- **Consent:** read the user's choice from `AppPreferences` and call
  `analytics.setCollectionEnabled(...)` before the first event. Add a switch in
  Settings. Ask the user what the default should be for PROD in their markets.
  Firebase Analytics without ads (no AdSupport / IDFA) doesn't need an ATT prompt
  on iOS.
- **iOS:** analytics stays `NoOpAnalytics` until the Firebase iOS SDK is linked
  (`kmp-environments` section 5). The Swift bridge pattern from section 8b
  works for Analytics too (`NativeAnalytics` interface, Swift calls
  `Analytics.logEvent`), so offer it when iOS analytics matters. Logs and the backend error reporter work on
  every platform, because they are pure Kotlin and Ktor.

## 10. Enforce it (no untracked clicks or screens)

```bash
#!/usr/bin/env bash
# scripts/check_analytics.sh: run in CI and before every commit.
set -euo pipefail
fail=0
scan() { grep -rnE --include='*.kt' "$1" feature | grep -v '/test/' || true; }

# Every navigation entry must use trackedEntry, so clicks know their screen.
hits=$(scan '\bentry<' | grep -v 'trackedEntry' || true)
[ -n "$hits" ] && { echo "$hits"; echo "Use trackedEntry<Route.X> instead of entry<Route.X>"; fail=1; }

# Every click must be wrapped. Mark a forwarded lambda with: // analytics: tracked inside
hits=$(scan 'onClick = \{|\.clickable \{|onClick = \(\) ->' | grep -v 'analytics: tracked inside' || true)
[ -n "$hits" ] && { echo "$hits"; echo "Wrap clicks: onClick = trackedClick(\"element_id\") { ... }"; fail=1; }

# No other logging or SDK access in features.
hits=$(scan '\bprintln\(|android\.util\.Log|NSLog|FirebaseAnalytics|co\.touchlab\.kermit')
[ -n "$hits" ] && { echo "$hits"; echo "Log through AppLogger and track through AnalyticsTracker only"; fail=1; }
exit $fail
```
Run it in `kmp-feature-scaffold` section 11 (Verify), next to the build and tests.

## 11. Test-driven development (write these first)

Shared fakes in `core/common`'s `test/`:
```kotlin
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    var userId: String? = null
    override fun logEvent(name: String, params: Map<String, Any>) { events += AnalyticsEvent(name, params) }
    override fun setUserId(id: String?) { userId = id }
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun setCollectionEnabled(enabled: Boolean) = Unit
}

class FakeLogger : AppLogger {
    val lines = mutableListOf<Triple<LogLevel, String, String>>()
    override fun log(level: LogLevel, tag: String, throwable: Throwable?, message: () -> String) {
        lines += Triple(level, tag, message())
    }
}

fun fakeTracker(analytics: FakeAnalytics = FakeAnalytics()) = AnalyticsTracker(analytics, FakeLogger())
```
Tests, one behavior each:
```kotlin
class AnalyticsTrackerTest {
    @Test fun dropsEventWithPersonalDataParam() {
        val analytics = FakeAnalytics()
        AnalyticsTracker(analytics, FakeLogger()).track(AnalyticsEvent("signup_done", mapOf("email" to "a@b.c")))
        assertTrue(analytics.events.isEmpty())
    }

    @Test fun truncatesLongStringValuesTo100Chars() { /* ... */ }
    @Test fun rejectsReservedPrefix() { /* "firebase_x" is dropped */ }
}

class ProfileAnalyticsTest {
    @Test fun everyEventFollowsTheRules() {
        listOf(ProfileAnalytics.loaded(10), ProfileAnalytics.saved(2), ProfileAnalytics.saveFailed(NetworkError.NoInternet()))
            .forEach { assertNull(AnalyticsRules.check(it), it.name) }
    }
}

class ProfileViewModelTest {
    @Test fun savingTracksProfileSaved() = runTest(dispatcher) {
        val analytics = FakeAnalytics()
        val vm = profileViewModel(tracker = fakeTracker(analytics))
        vm.onEvent(ProfileEvents.Save)
        advanceUntilIdle()
        assertEquals("profile_saved", analytics.events.last().name)
    }

    @Test fun failedSaveTracksReason() { /* NoInternet -> profile_save_failed, reason = no_internet */ }
}

class ApiTelemetryTest {
    @Test fun serverErrorTracksApiErrorWithMaskedPath() = runTest {
        val analytics = FakeAnalytics()
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }) {
            install(ApiTelemetry) { logger = FakeLogger(); tracker = fakeTracker(analytics) }
        }
        client.get("https://test.local/uat/api/v1/orders/01J9ZQ4Y0V8X2B7K3M5N6P7Q8R")
        val params = analytics.events.single { it.name == "api_error" }.params
        assertEquals("/uat/api/v1/orders/{id}", params["path"])
        assertEquals(500L, params["status"])
    }

    @Test fun everyRequestCarriesARequestId() { /* MockEngine asserts the X-Request-Id header */ }
}

class DefaultAppLoggerTest {
    @Test fun errorIsReportedEvenWhenConsoleLevelIsWarn() { /* FakeErrorReporter gets it */ }
    @Test fun debugIsNotBuiltBelowMinLevel() { /* the lambda is never called */ }
    @Test fun redactsEmailsAndBearerTokens() { /* "Bearer abc" -> "Bearer ***" */ }
}

class RemoteErrorReporterTest {
    @Test fun sendsABurstAsOneBatch() { /* 3 reports -> 1 POST with 3 errors */ }
    @Test fun keepsOnlyTheNewest50() { /* 60 reports offline -> 50 sent */ }
    @Test fun neverThrowsWhenTheBackendFails() { /* MockEngine 500 -> no exception, no ERROR log */ }
}
```
- `Route.analyticsName`: test that names are snake_case and unique for one
  instance of each route. The exhaustive `when` covers new routes at compile time.
- Click tracking: if the module has Compose UI tests, `runComposeUiTest`
  that clicking a `trackedClick("save_button")` button inside
  `TrackedScreen("profile")` records `ui_click` with `screen = profile` and
  `element_id = save_button`.
- Never assert on Firebase or Kermit directly. Assert on the fakes.

## 12. Verify on a device

- Android UAT: `adb shell setprop debug.firebase.analytics.app <applicationId>`,
  then open **DebugView** in the **UAT** Firebase project. Navigate and click;
  each action should show one `screen_view` or `ui_click`. Turn it off with
  `adb shell setprop debug.firebase.analytics.app .none.`
- Logcat: filter by tag (`profile`, `network`, `analytics`). A dropped event
  shows as `Dropped analytics event ...` at `ERROR`.
- Errors: force a bad JSON response in UAT (or call a debug-only "Send test
  error" button), then open the UAT admin error panel and filter by today. The
  row should show `source = android`, the tag and the app version.
- Add the DebugView and error panel screenshots to the feature screenshots
  (`kmp-feature-scaffold` section 12).

## Checklist

- [ ] `Analytics` (SDK-free) + `AnalyticsTracker` + `AnalyticsRules` + `CommonAnalytics` in `core/common`; features use only `AnalyticsTracker`
- [ ] `AppLogger` + `DefaultAppLogger` (Kermit inside only) + `LogRedactor` + `ErrorReporter`; no `println` / `Log.d` / `NSLog`
- [ ] Every route has an `analyticsName` (exhaustive `when`); `TrackScreenViews` in `MainNavigation`; Firebase automatic screen reporting off
- [ ] Every feature entry uses `trackedEntry`; every onClick uses `trackedClick("element_id")`; shared chrome tracked once in `core/ui`
- [ ] Each feature has `<Name>Analytics` with outcome events; ViewModel tracks them; repositories log failures with `logTo` and a tag
- [ ] `ApiTelemetry` installed: `X-Request-Id`, masked paths, `api_error`; Ktor `Logging` debug only
- [ ] `RemoteErrorReporter` sends `ERROR`s and crashes to `POST api/v1/client-errors`; bounded queue; no self-reporting loop
- [ ] Crashlytics on Android (mapping id + CLI mapping upload per release) and iOS (Swift bridge + dSYM script); test crash seen in the UAT console on both
- [ ] No personal data in events, params or logs; user id is opaque (Supabase auth id or backend ULID); consent switch respected
- [ ] UAT logs `DEBUG`, PROD logs `WARN`; errors reported in both to their own backend; iOS analytics status stated honestly
- [ ] `scripts/check_analytics.sh` passes; tests written first and green (rules, tracker, feature events, telemetry, logger, reporter)
- [ ] DebugView and error panel verified on UAT; screenshots and file report included (`kmp-feature-scaffold` 12-13)
