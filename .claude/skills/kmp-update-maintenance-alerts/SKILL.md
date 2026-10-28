---
name: kmp-update-maintenance-alerts
description: Add admin-controlled maintenance alerts and soft/force update dialogs to a KMP + CMP app (KMP-MultiModule-Template / Amper), driven by GET api/v1/app/status from the web-admin-landing backend, per platform and per UAT/PROD environment. Use when the app must show a maintenance screen, an upcoming-maintenance notice, a "new version available" dialog, or block old versions until they update.
---

# Maintenance Alerts and Update Dialogs (admin controlled)

The admin portal decides, without an app release, whether the app shows:

| Alert | When | Look | Can the user continue? |
|---|---|---|---|
| **Maintenance (active)** | Admin started maintenance, or a scheduled window began | Full-screen blocking screen | No. Only "Try again" |
| **Maintenance notice (upcoming)** | A window starts soon (admin sets how long before) | `MyAlertDialog`, `Warning` | Yes, dismiss once per window |
| **Soft update** | A newer build exists and soft update is on | `MyAlertDialog`, `Info`, "Update" + "Later" | Yes. "Later" snoozes |
| **Force update** | The installed build is below the minimum supported build | `MyAlertDialog`, `Warning`, "Update" only, not dismissible | No |

Priority when several apply: **active maintenance > force update > upcoming
notice > soft update**. Only one alert is shown at a time (never stack dialogs,
`compose-multiplatform-ui` 4.1).

Related skills: the backend endpoint and admin screen are in
`web-admin-landing` section 6b. Feature structure, MVI, Koin and TDD follow
`kmp-feature-scaffold`. HTTP follows `kmp-networking-ktor`. Environment and
version come from `kmp-environments` (`AppEnvironment.versionCode`). Dialogs use
`MyAlertDialog` from `compose-multiplatform-ui` 4.1. Readable code follows
`kotlin-readable-code`.

## 1. Read the project first

```bash
ls feature/ core/ app/
grep -rn "versionCode\|versionName" core/common/src config/version.properties
grep -rn "getPlatform\|expect fun" core/common/src | head
grep -rn "MyAlertDialog\|LocalUriHandler" core/ui/src | head
grep -rn "multiplatform-settings\|datastore\|room" */module.yaml */*/module.yaml
```
- If an app-status or force-update feature exists, extend it instead of adding a second one.
- If `AppEnvironment` has no `versionCode`, add it first (`kmp-environments` section 3).
- Snooze, dismissed-notice and cached-status values are settings, so they go in
  `core/preferences` (`AppPreferences`), the module for non-sensitive key-value
  data (`kmp-clean-architecture` section 2b). If the project has no
  `core/preferences` yet, ask the user before adding it (`multiplatform-settings`
  is the smallest choice). Don't add a Room table for this.

## 2. How it works

```
App start / app resume (throttled) / any API call answered 503 + X-App-Maintenance
        │
        ▼
AppStatusViewModel ── CheckAppStatus ──> AppStatusRepository ── GET api/v1/app/status
        │                                     │  ?platform=android&build=12&version=1.1.1&locale=bn
        │                                     └─ caches the last good status
        ▼
resolveAlert(status, snooze, dismissedNotices, now)   ← pure, fully unit tested
        │
        ▼
AppStatusHost (wraps NavDisplay in App.kt)
   ├─ MaintenanceScreen        (active maintenance, covers everything)
   ├─ MyAlertDialog            (force / upcoming notice / soft)
   └─ content()                (the normal app)
```

- **The server decides the update type** (`none`, `soft`, `force`) from the
  platform and build it receives. The app never compares version strings itself.
  Builds are compared as integers (`VERSION_CODE`), never `"1.10.0" < "1.9.0"` text.
- **Environments stay apart for free.** A UAT build calls
  `https://abc.com/uat/api/v1/app/status`, so only the UAT admin portal can show
  alerts in UAT builds. Testing a force update in UAT never touches PROD users.
- **Fail open.** If the check fails (offline, timeout, 5xx), the app keeps
  working. It uses the cached status only as described in section 6.

## 3. Contract (from `backend/contracts/openapi.yaml`)

```
GET api/v1/app/status?platform=android|ios|desktop|web&build=12&version=1.1.1&locale=en
```
Public (no auth), rate limited, `Cache-Control: public, max-age=30`.
```json
{ "data": {
    "update": {
      "type": "soft",
      "latestVersion": "1.2.0",
      "latestBuild": 15,
      "minSupportedBuild": 10,
      "storeUrl": "https://play.google.com/store/apps/details?id=com.touhidapps.kmp",
      "remindAfterHours": 24,
      "title": null,
      "message": "Faster checkout and bug fixes."
    },
    "maintenance": {
      "id": "01JBX8Q4W2N3T5V7Y9A1C3E5G7",
      "phase": "upcoming",
      "startsAt": "2026-11-02T20:00:00Z",
      "endsAt": "2026-11-02T22:00:00Z",
      "title": null,
      "message": null
    },
    "serverTime": "2026-11-02T19:10:00Z"
  },
  "error": null }
```
- `update.type`: `none` | `soft` | `force`. `maintenance` is `null` when nothing
  is scheduled within the notice period. `phase`: `upcoming` | `active`.
- `title` / `message` are optional admin overrides already picked for `locale`.
  When `null`, the app uses its own `Strings`. They are **plain text** (max 60 /
  300 chars). Never render them as HTML or Markdown.
- `storeUrl` is always `https` and set by the admin per platform. In UAT it may
  point to TestFlight or Firebase App Distribution.
- While maintenance is active, every **other** mobile endpoint answers
  `503` with header `X-App-Maintenance: 1`, `Retry-After`, and
  `{ "data": null, "error": { "code": "maintenance", ... } }`. The status endpoint
  itself keeps answering 200.

## 4. Files

```
feature/appstatus/
  module.yaml                                   # same platforms as core/ui
  src/com/<org>/appstatus/
    domain/
      model/AppStatus.kt                        # AppStatus, UpdateInfo, MaintenanceInfo, AppAlert
      model/AppPlatform.kt
      repository/AppStatusRepository.kt
      repository/AppStatusLocalStore.kt         # snooze, dismissed notices, cached status
      usecase/CheckAppStatusUseCase.kt
      usecase/ResolveAlert.kt                   # pure priority + snooze rules
    data/
      source/ApiUrls.kt
      source/AppStatusApi.kt  source/AppStatusSource.kt
      dto/AppStatusDto.kt
      mapper/AppStatusMapper.kt
      local/AppStatusLocalStoreImpl.kt
      repository/AppStatusRepositoryImpl.kt
    presentation/
      AppStatusContract.kt  AppStatusState.kt  AppStatusEvents.kt  AppStatusActions.kt
      AppStatusViewModel.kt
      ui/AppStatusHost.kt  ui/MaintenanceScreen.kt  ui/AppStatusDialogs.kt
    di/AppStatusModule.kt
  test/...                                      # section 9
core/network/src/.../MaintenanceSignal.kt        # 503 + X-App-Maintenance → event
core/common/src/.../platform/AppPlatform.kt      # only if the template has no platform helper
core/ui/src/.../illustrations/MaintenanceIllustration.kt   # own vector, no copyrighted art
core/common/src/.../localization/Strings.kt, StringsEn.kt, StringsBn.kt   # new keys
app/shared/src/.../App.kt                        # wraps NavDisplay in AppStatusHost
```
`feature/appstatus` depends on `core/network`, `core/common`, `core/ui` and
`core/preferences`. `MaintenanceSignal` stays in `core/network` because every API
call can raise it; the illustration stays in `core/ui`; strings stay in
`core/common`. Nothing depends on it except `app/shared`. It has no route:
it is an overlay, not a screen in the back stack.

## 5. Domain

```kotlin
// domain/model/AppPlatform.kt
enum class AppPlatform(val apiValue: String) { ANDROID("android"), IOS("ios"), DESKTOP("desktop"), WEB("web") }
```
Get the value from the template's platform helper if it has one; otherwise add
`expect val currentAppPlatform: AppPlatform` in `core/common` with one `actual`
per platform folder (`src@android/`, `src@ios/`, `src@jvm/`, `src@wasmJs/`).

```kotlin
// domain/model/AppStatus.kt
enum class UpdateType { NONE, SOFT, FORCE }
enum class MaintenancePhase { UPCOMING, ACTIVE }

data class UpdateInfo(
    val type: UpdateType,
    val latestVersion: String,
    val latestBuild: Int,
    val storeUrl: String,
    val remindAfterHours: Int,
    val customTitle: String?,
    val customMessage: String?,
) {
    companion object { val None = UpdateInfo(UpdateType.NONE, "", 0, "", 24, null, null) }
}

data class MaintenanceInfo(
    val id: String,
    val phase: MaintenancePhase,
    val startsAt: Instant,
    val endsAt: Instant?,
    val customTitle: String?,
    val customMessage: String?,
)

data class AppStatus(
    val update: UpdateInfo,
    val maintenance: MaintenanceInfo?,
    val checkedAt: Instant,
)

sealed interface AppAlert {
    data object None : AppAlert
    data class MaintenanceActive(val info: MaintenanceInfo) : AppAlert
    data class MaintenanceUpcoming(val info: MaintenanceInfo) : AppAlert
    data class ForceUpdate(val info: UpdateInfo) : AppAlert
    data class SoftUpdate(val info: UpdateInfo) : AppAlert
}

/** "Later" on a soft update: hide it for this build until [until]. A newer build shows it again. */
data class SoftUpdateSnooze(val build: Int, val until: Instant)
```
Use `kotlin.time.Instant` (Kotlin 2.1.20+) or `kotlinx.datetime.Instant`,
whichever the project already uses.

```kotlin
// domain/usecase/ResolveAlert.kt
class ResolveAlert {
    operator fun invoke(
        status: AppStatus?,
        snooze: SoftUpdateSnooze?,
        dismissedNoticeIds: Set<String>,
        now: Instant,
    ): AppAlert {
        if (status == null) return AppAlert.None
        val maintenance = status.maintenance?.takeUnless { it.hasEnded(now) }
        val update = status.update

        return when {
            maintenance?.phase == MaintenancePhase.ACTIVE -> AppAlert.MaintenanceActive(maintenance)
            update.type == UpdateType.FORCE -> AppAlert.ForceUpdate(update)
            maintenance != null && maintenance.id !in dismissedNoticeIds -> AppAlert.MaintenanceUpcoming(maintenance)
            update.type == UpdateType.SOFT && !snooze.hides(update, now) -> AppAlert.SoftUpdate(update)
            else -> AppAlert.None
        }
    }

    private fun MaintenanceInfo.hasEnded(now: Instant) = endsAt != null && now >= endsAt

    private fun SoftUpdateSnooze?.hides(update: UpdateInfo, now: Instant) =
        this != null && build >= update.latestBuild && now < until
}
```

```kotlin
// domain/repository/AppStatusRepository.kt
interface AppStatusRepository {
    /** Fresh status from the server, or the cached one when the call fails (section 6). */
    suspend fun check(): ResultNet<AppStatus, NetworkError>
}

// domain/repository/AppStatusLocalStore.kt
interface AppStatusLocalStore {
    suspend fun cachedStatus(): AppStatus?
    suspend fun saveStatus(status: AppStatus)
    suspend fun snooze(): SoftUpdateSnooze?
    suspend fun saveSnooze(snooze: SoftUpdateSnooze)
    suspend fun dismissedNoticeIds(): Set<String>
    suspend fun dismissNotice(id: String)
}
```

## 6. Data

```kotlin
// data/source/ApiUrls.kt
object ApiUrls {
    const val APP_STATUS = "api/v1/app/status"      // no leading "/": keeps /uat (kmp-environments 1b)
}

// data/source/AppStatusSource.kt
class AppStatusSource(private val client: HttpClient) : AppStatusApi {
    override suspend fun getStatus(platform: String, build: Int, version: String, locale: String): ApiEnvelope<AppStatusDto> =
        client.get(ApiUrls.APP_STATUS) {
            parameter("platform", platform)
            parameter("build", build)
            parameter("version", version)
            parameter("locale", locale)
        }.body()
}
```

```kotlin
// data/dto/AppStatusDto.kt
@Serializable
data class AppStatusDto(
    @SerialName("update") val update: UpdateDto? = null,
    @SerialName("maintenance") val maintenance: MaintenanceDto? = null,
    @SerialName("serverTime") val serverTime: String? = null,
)

@Serializable
data class UpdateDto(
    @SerialName("type") val type: String? = null,
    @SerialName("latestVersion") val latestVersion: String? = null,
    @SerialName("latestBuild") val latestBuild: Int? = null,
    @SerialName("storeUrl") val storeUrl: String? = null,
    @SerialName("remindAfterHours") val remindAfterHours: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class MaintenanceDto(
    @SerialName("id") val id: String? = null,
    @SerialName("phase") val phase: String? = null,
    @SerialName("startsAt") val startsAt: String? = null,
    @SerialName("endsAt") val endsAt: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("message") val message: String? = null,
)
```

Mapper rules (each one has a test):
- Unknown or missing `update.type` → `NONE`. A typo on the server must never block users.
- `force` or `soft` with a missing or non-`https` `storeUrl` → `NONE`. A dialog
  whose Update button goes nowhere is worse than no dialog, and a force dialog
  without a way out would lock users in. The admin form refuses to save a
  policy without a valid URL, so this only guards against bad data.
- Maintenance with a missing `id`, unknown `phase` or unparsable `startsAt` → `null`.
- Blank `title` / `message` → `null`. Trim, and cut to 60 / 300 chars.
- `remindAfterHours` is clamped to 1..168 (default 24).

Repository and cache:
```kotlin
class AppStatusRepositoryImpl(
    private val api: AppStatusApi,
    private val store: AppStatusLocalStore,
    private val env: AppEnvironment,
    private val platform: AppPlatform,
    private val locale: () -> String,
    private val clock: Clock,
) : AppStatusRepository {

    override suspend fun check(): ResultNet<AppStatus, NetworkError> {
        val fresh = safeApiCall {
            api.getStatus(platform.apiValue, env.versionCode, env.versionName, locale())
                .requireData()
                .toDomain(checkedAt = clock.now())
        }
        return when (fresh) {
            is ResultNet.Success -> fresh.also { store.saveStatus(it.data) }
            is ResultNet.Error -> store.cachedStatus()
                ?.takeIf { it.isUsableOffline(clock.now()) }
                ?.let { ResultNet.Success(it) }
                ?: fresh
        }
    }

    /** A cached force update stays until the server says otherwise; anything else expires after 24 h. */
    private fun AppStatus.isUsableOffline(now: Instant) =
        update.type == UpdateType.FORCE || now - checkedAt < 24.hours
}
```
- `locale()` returns the app's current language code (`"en"`, `"bn"`) from the
  template's localization setup, so admin overrides come in the user's language.
- `requireData()` lives next to `ApiEnvelope` in `core/network`. Add it if missing:
  ```kotlin
  class ApiEnvelopeException(val code: String) : Exception(code)

  fun <T> ApiEnvelope<T>.requireData(): T =
      data ?: throw ApiEnvelopeException(error?.code ?: "empty_data")
  ```
  Map `ApiEnvelopeException` in `safeApiCall` to `NetworkError.Server(code)` (or
  the project's equivalent).

**Maintenance signal from any API call** (`core/network`):
```kotlin
// core/network/.../MaintenanceSignal.kt
class MaintenanceSignal {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Unit> = _events
    fun notifyMaintenance() { _events.tryEmit(Unit) }
}

// inside createHttpClient(...)
HttpResponseValidator {
    validateResponse { response ->
        if (response.status == HttpStatusCode.ServiceUnavailable && response.headers["X-App-Maintenance"] == "1") {
            maintenanceSignal.notifyMaintenance()
        }
    }
}
```
Add `NetworkError.Maintenance` to the 503 mapping in `safeApiCall` so feature
screens don't show a generic error under the maintenance screen.
Register `single { MaintenanceSignal() }` next to the `HttpClient` in `appModule`.
Also send the platform on every request, so the backend can block only the
platforms under maintenance:
```kotlin
defaultRequest { header("X-App-Platform", platform.apiValue) }
```

## 7. Presentation (MVI)

```kotlin
data class AppStatusState(
    val alert: AppAlert = AppAlert.None,
    val isChecking: Boolean = false,
)

sealed interface AppStatusEvents {
    data object OnAppStarted : AppStatusEvents
    data object OnAppResumed : AppStatusEvents
    data object OnRetryClicked : AppStatusEvents
    data object OnUpdateClicked : AppStatusEvents
    data object OnLaterClicked : AppStatusEvents
    data object OnNoticeDismissed : AppStatusEvents
}

sealed interface AppStatusActions {
    data class OpenStore(val url: String) : AppStatusActions
}
```

ViewModel rules:
- `OnAppStarted` checks once. `OnAppResumed` checks only if the last check was
  more than **5 minutes** ago (use an injected `Clock`, so tests control time).
  `OnRetryClicked` always checks.
- `MaintenanceSignal.events` triggers a check (not a direct switch to the
  maintenance screen), so the server's `endsAt` and texts are shown.
- When the current alert is `MaintenanceUpcoming`, schedule one re-check at
  `startsAt` (cancel it on the next check). When it is `MaintenanceActive` with
  `endsAt`, re-check at `endsAt`, and the Retry button works any time.
- `OnLaterClicked` saves `SoftUpdateSnooze(latestBuild, now + remindAfterHours)`
  and sets `alert` to `None`. It is ignored for `ForceUpdate`.
- `OnNoticeDismissed` saves the window id; the notice is not shown again for that window.
- `OnUpdateClicked` emits `OpenStore(storeUrl)`. For a soft
  update it also snoozes, so coming back without updating doesn't show it again at once.
- A failed check never replaces a visible alert with an error, and never shows
  an error dialog. Failures are logged only when `env.isDebug`.

Host (the only UI entry point):
```kotlin
// presentation/ui/AppStatusHost.kt
@Composable
fun AppStatusHost(content: @Composable () -> Unit) {
    val contract: AppStatusContract = koinInject()
    val state by contract.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { contract.onEvent(AppStatusEvents.OnAppStarted) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { contract.onEvent(AppStatusEvents.OnAppResumed) }
    LaunchedEffect(Unit) {
        contract.actions.collect { action ->
            when (action) {
                is AppStatusActions.OpenStore -> runCatching { uriHandler.openUri(action.url) }
            }
        }
    }

    when (val alert = state.alert) {
        is AppAlert.MaintenanceActive -> MaintenanceScreen(
            info = alert.info,
            isChecking = state.isChecking,
            onRetry = { contract.onEvent(AppStatusEvents.OnRetryClicked) },
        )
        else -> {
            content()
            AppStatusDialog(alert = alert, onEvent = contract::onEvent)
        }
    }
}
```
```kotlin
// app/shared/.../App.kt
AppTheme {
    AppStatusHost {
        NavDisplay(/* unchanged */)
    }
}
```
While maintenance is active the normal content is **not composed**, so screens
don't keep firing requests that all return 503.

Dialogs (`AppStatusDialogs.kt`), all through `MyAlertDialog`:

| Alert | `type` | Title / message (fallback `Strings`) | Buttons | Outside tap / back |
|---|---|---|---|---|
| `SoftUpdate` | `Info`, icon `Rounded.SystemUpdate` | `updateAvailableTitle` / `updateAvailableMessage(latestVersion)` | `updateNow` + `later` | dismiss = Later |
| `ForceUpdate` | `Warning`, icon `Rounded.SystemUpdate` | `updateRequiredTitle` / `updateRequiredMessage` | `updateNow` only | **off** (`dismissOnClickOutside = false`, `onDismiss = {}`) |
| `MaintenanceUpcoming` | `Warning`, icon `Rounded.Schedule` | `maintenanceUpcomingTitle` / `maintenanceUpcomingMessage(start, end)` | `ok` | dismiss = OK |

The admin's `customTitle` / `customMessage` replace the fallback text when not null.
For `ForceUpdate`, the dialog stays after the store opens, so returning to the
app still shows it. On Android, back press on a force dialog does nothing.

Maintenance screen (`MaintenanceScreen.kt`):
- Full screen `Surface(color = colorScheme.background)`, content centered in
  `widthIn(max = 480.dp)`, safe-area padding, scrollable for small phones and
  large font scale.
- Top: the app's own `MaintenanceIllustration` from `core/ui` (original vector,
  theme colors only, about 180 dp). Then `headlineSmall` title, `bodyLarge`
  message in `onSurfaceVariant`, and, when `endsAt` is known,
  `maintenanceEndsAt(time)` in `bodyMedium`.
- Bottom: `AppButton(text = strings.tryAgain, isLoading = isChecking)`.
- Times are shown in the device's time zone and locale format (kotlinx-datetime
  `toLocalDateTime(TimeZone.currentSystemDefault())`). Never show raw ISO strings.
- In UAT the screen still shows `AppVersionText(env.versionLabel)` at the bottom,
  so testers can tell which build is blocked.

## 8. Strings (`core/common` localization)

Add to `Strings` and implement in `StringsEn` and `StringsBn` (keep the
template's style: plain properties, functions for values with arguments):

| Key | English | Bangla |
|---|---|---|
| `updateAvailableTitle` | New version available | নতুন ভার্সন এসেছে |
| `updateAvailableMessage(v)` | Version $v is ready with improvements and fixes. | ভার্সন $v এ নতুন উন্নতি ও সমাধান আছে। |
| `updateRequiredTitle` | Update required | আপডেট করা প্রয়োজন |
| `updateRequiredMessage` | This version is no longer supported. Please update to continue. | এই ভার্সনটি আর সাপোর্টেড নয়। চালিয়ে যেতে অ্যাপটি আপডেট করুন। |
| `updateNow` | Update | আপডেট করুন |
| `later` | Later | পরে |
| `maintenanceTitle` | We'll be right back | আমরা শীঘ্রই ফিরছি |
| `maintenanceMessage` | The app is under maintenance. Please try again soon. | অ্যাপটির রক্ষণাবেক্ষণ চলছে। কিছুক্ষণ পর আবার চেষ্টা করুন। |
| `maintenanceEndsAt(t)` | Expected back by $t | আনুমানিক $t এর মধ্যে ফিরে আসবে |
| `maintenanceUpcomingTitle` | Scheduled maintenance | নির্ধারিত রক্ষণাবেক্ষণ |
| `maintenanceUpcomingMessage(s, e)` | The app will be unavailable from $s to $e. | $s থেকে $e পর্যন্ত অ্যাপটি বন্ধ থাকবে। |
| `tryAgain` | Try again | আবার চেষ্টা করুন |

Reuse `ok` if it already exists. When `endsAt` is unknown, use a variant without `$e`.

## 9. Tests first (order from `kmp-feature-scaffold` 10)

1. **`AppStatusMapperTest`**: `"soft"`/`"force"`/`"none"` map; unknown type → `NONE`;
   soft or force with an `http://` or missing store URL → `NONE`; blank texts → `null`; long message cut to 300;
   maintenance with a bad date → `null`; `remindAfterHours` 0 → 1, 999 → 168.
2. **`ResolveAlertTest`** (table of cases, `FixedClock`):
   - active maintenance beats force update
   - force update ignores a snooze
   - upcoming notice shows once; a dismissed id hides it
   - soft update hidden while snoozed for the same build, shown after `until`
   - soft update shown again at once when `latestBuild` is newer than the snoozed build
   - maintenance whose `endsAt` has passed → `None`
   - `null` status → `None`
3. **`AppStatusRepositoryImplTest`** with Ktor `MockEngine` and base URL
   `https://test.local/uat/`: request path is `/uat/api/v1/app/status` with
   `platform`, `build`, `version`, `locale`; success is cached; offline uses a
   cached force update of any age; offline ignores a 25 h old soft status;
   envelope `error` → `ResultNet.Error`.
4. **`MaintenanceSignalTest`**: a 503 with `X-App-Maintenance: 1` emits; a plain
   503 does not.
5. **`AppStatusViewModelTest`** (fake repository, fake store, controllable clock):
   start checks once; resume after 2 min does nothing, after 6 min checks; Later
   saves a snooze with `now + remindAfterHours` and clears the alert; Update emits
   `OpenStore` with the server URL; Later is ignored for a
   force update; a failed check keeps the current alert; a maintenance signal
   triggers a check.
6. **UI tests** (`runComposeUiTest`): force dialog has no "Later" button and
   doesn't close on outside tap; maintenance screen hides the content and Retry
   sends `OnRetryClicked`.

## 10. Rules

- The update decision is the server's. The app sends its build; it never parses
  version names to decide.
- Never block the app because the check failed. Only a server-confirmed (or
  cached) force update or active maintenance blocks.
- Admin texts are plain text, length-limited, and never executed or rendered as HTML.
- Only open `https` store URLs that came from the status endpoint. Never open
  a URL from a push message or deep link as a "store URL". An `app_update`
  push (`kmp-push-notifications`) asks `StoreLinkProvider`, which reads this
  feature's cached `storeUrl`.
- Desktop and web send their platform too. The backend returns `update.type = none`
  for them unless a policy exists, and maintenance applies if the admin picked them.
- Optional on Android: Google Play In-App Updates (flexible for soft, immediate
  for force) in `src@android/`. Ask the user first, since it adds a Play
  dependency, and keep the dialogs as the fallback for sideloaded and UAT builds.
- No copyrighted images. The maintenance illustration and icons are original
  vectors or Material Symbols (Apache 2.0).

## 11. Show the result (required)

Follow `kmp-feature-scaffold` sections 12 and 13. Use the UAT admin portal (or a
`MockEngine` preview) to produce each state, and capture on Android and iOS,
light and dark:
- soft update dialog, force update dialog, upcoming maintenance notice,
  maintenance screen with an end time, maintenance screen in Bangla
- a short recording: admin turns on soft update in UAT → app resumes → dialog →
  Later → dialog gone; then admin raises the minimum build → force dialog.

## Checklist

- [ ] Backend endpoint and admin controls exist (`web-admin-landing` 6b); contract in `openapi.yaml` first
- [ ] `AppEnvironment.versionCode` comes from `config/version.properties`; app sends `platform`, `build`, `version`, `locale`
- [ ] `ApiUrls.APP_STATUS` has no leading `/`; UAT build hits `/uat/api/v1/app/status`
- [ ] `ResolveAlert` priority: active maintenance > force > upcoming notice > soft; one alert at a time
- [ ] Soft update snoozes per build for `remindAfterHours`; force has no Later and can't be dismissed
- [ ] Maintenance screen replaces content; 503 + `X-App-Maintenance` triggers a re-check
- [ ] Check on start, on resume (5 min throttle), on retry, and at scheduled start/end times
- [ ] Fails open; cache rules from section 6; no error dialogs from this feature
- [ ] All texts from `Strings` (En + Bn) unless the admin set an override; times in local zone
- [ ] `MyAlertDialog` and theme roles only; original illustration
- [ ] Tests from section 9 written first and green; screenshots, recording and file report delivered
