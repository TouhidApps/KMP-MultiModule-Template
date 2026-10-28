---
name: kmp-push-notifications
description: Add Firebase Cloud Messaging push notifications to a KMP + CMP app (KMP-MultiModule-Template / Amper / Koin / Navigation3), sent from the web-admin-landing admin portal, with a push list screen (latest 30, all history kept on the server), tap actions (open the push list, open a chosen app page, app update to Play / App Store, web link in an in-app browser) and push analytics (sent, received, opened, open rate). Use when adding push, notification permission, a notification list, deep links from a push, or push reporting.
---

# Push Notifications (FCM, admin portal, push list, analytics)

Admins write and send pushes in the admin portal. The app shows them in the
system tray and in a **push list** screen, and opens the right place when the
user taps one. Every push and every send, receive and open is kept on the
server forever. The app list shows the **latest 30**.

| Push type (`type`) | Admin picks | Tap opens |
|---|---|---|
| `default` | title, message | the push list, with this push highlighted |
| `deeplink` | an app page from a fixed list (`target`) + its params | that page (`Route`) |
| `app_update` | nothing extra | Play Store on Android, App Store on iOS, for **this** app |
| `web_url` | an `https` URL | that page in the in-app browser (Custom Tabs / `SFSafariViewController`) |

Tapping an item in the push list does the same as tapping it in the tray,
except `default`, which opens its details screen.

Related skills: backend, admin page and push analytics are in
`web-admin-landing` section 6c. Module placement follows
`kmp-clean-architecture` 2b and `kmp-feature-scaffold` (the list lives in
`feature/notice`). Firebase projects per environment follow
`kmp-environments` (UAT and PROD are separate Firebase projects). Events and
logs follow `kmp-analytics-logging`. The store URL for `app_update` comes from
`kmp-update-maintenance-alerts`. HTTP follows `kmp-networking-ktor`; tokens
follow `kmp-security`; dialogs and theme follow `compose-multiplatform-ui`;
readable code follows `kotlin-readable-code`.

## 1. Read the project first

```bash
ls feature/ core/ app/
ls feature/notice feature/notification 2>/dev/null
grep -rn "firebase-messaging\|FirebaseMessaging\|UNUserNotificationCenter" core/ app/ | head
grep -rn "sealed class Route\|data object\|data class" core/ui/src/**/nav/Route.kt | head -30
grep -rn "SecureStorage\|AppPreferences\|AppDatabase" core/*/src | head
grep -rn "AppStatusRepository\|storeUrl" feature/appstatus 2>/dev/null | head
ls backend/api/modules 2>/dev/null
```
- If `feature/notice` exists, extend it. The template's `feature/notification`
  is reference code: copy patterns from it, don't add a second list.
- If `backend/api/modules/Push` doesn't exist, build it first
  (`web-admin-landing` 6c), contract first in `backend/contracts/openapi.yaml`.
- If `core/security` or `core/database` is missing, ask the user before adding
  it. Push needs both (device key, cached list).
- iOS: like Crashlytics (`kmp-analytics-logging` 8b), Amper can't link the
  Firebase iOS SDK, so iOS push goes through a small Swift bridge (section 8).

## 2. How it works

```
Admin portal ── POST admin/push ──> PHP Push module ── queue ──> FCM HTTP v1 ──> device
                 (stores push forever,              (per device: push_recipients)
                  resolves audience)

App start / token refresh / login / logout / permission change
   └─ SyncPushDeviceUseCase ── PUT api/v1/push/devices/{installationId}   (FCM token, platform, build, locale, user)

Push arrives
   Android: data-only message → AppMessagingService builds the notification → "received" event
   iOS:     APNs alert shown by the system (app code does not run)

User taps (tray or list)
   └─ PushEventSink.onOpened(payload, source) → "opened" event (server + Firebase Analytics)
        └─ PushTargetResolver → Navigate(Route) | OpenStore(url) | OpenBrowser(url)

Push list screen
   └─ GET api/v1/push/inbox   (latest 30 for this device, with readAt) → Room cache → screen
```

- **Environments stay apart.** A UAT build has the UAT `google-services.json` /
  `GoogleService-Info.plist` and calls `https://abc.com/uat/api/...`, so its
  tokens are registered only in the UAT backend, which sends only with the UAT
  Firebase service account. A UAT test push can never reach PROD users.
- **The server is the source of truth.** The list comes from the inbox
  endpoint. The app caches it for offline use and never builds the list from
  received messages, so pushes delivered while the app was killed still show up.
- **Every tap is safe.** Payloads are parsed into a closed set of targets. An
  unknown type, an unknown page, a missing param or a non-`https` URL falls
  back to the push list. The app never opens a URL or route it can't validate.

## 3. Contract (from `backend/contracts/openapi.yaml`)

**Device registration** (no user login needed; links the user when signed in):
```
POST api/v1/push/devices                       first time → 201 { "data": { "deviceKey": "dk_..." } }
PUT  api/v1/push/devices/{installationId}      every later sync, header X-Device-Key
```
```json
{ "installationId": "8f0e6f9c-...", "token": "fcm-token", "platform": "android",
  "appBuild": 15, "appVersion": "1.2.0", "locale": "bn", "timeZone": "Asia/Dhaka",
  "notificationsEnabled": true }
```
- `Authorization: Bearer <user token>` present → the server links the device to
  that user. Absent → the server unlinks it (that's how logout works).
- `deviceKey` is returned once. It is a secret: store it in `SecureStorage`,
  never log it, never put it in analytics.

**Inbox** (latest 30, newest first, texts already in the device's language):
```
GET api/v1/push/inbox?locale=bn               header X-Device-Key
```
```json
{ "data": [
    { "id": "01JC2Y7G8K9M0N1P2Q3R4S5T6V", "type": "deeplink",
      "title": "Your order is on the way", "body": "Tap to see the details.",
      "imageUrl": null, "target": "notice_details", "params": { "id": "42" },
      "url": null, "sentAt": "2026-11-02T10:15:00Z", "readAt": null }
  ],
  "error": null, "meta": { "limit": 30 } }
```
**Events** (batched, idempotent per push + device + event):
```
POST api/v1/push/events                       header X-Device-Key, at most 50 per call
{ "events": [ { "pushId": "01JC...", "event": "opened", "source": "tray", "occurredAt": "2026-11-02T10:16:03Z" } ] }
```
`event`: `received` (Android only) | `opened` | `list_opened`. `source`: `tray` | `list`.

**FCM data keys** (the same keys on Android and iOS; all values are strings):

| Key | Example | Notes |
|---|---|---|
| `push_id` | `01JC2Y7G8K9M0N1P2Q3R4S5T6V` | ULID, required |
| `type` | `default` / `deeplink` / `app_update` / `web_url` | required |
| `title`, `body` | localized | Android builds the notification from them |
| `image` | `https://cdn.abc.com/p/1.jpg` | optional, `https` only |
| `target` | `notice_details` | `deeplink` only, one of section 5's keys |
| `params` | `{"id":"42"}` | `deeplink` only, JSON object of strings |
| `url` | `https://abc.com/offer` | `web_url` only, `https` only |

## 4. Files

```
core/common/src/com/<org>/common/push/
  PushPayload.kt             # PushType, PushTarget, DeepLinkScreen, PushPayload, PushOpenSource
  PushPayloadParser.kt       # Map<String, String> → PushPayload? (pure, fully tested)
  PushMessaging.kt           # token + permission, implemented per platform
  PushEventSink.kt           # onNewToken / onReceived / onOpened (Android service, iOS bridge call it)
  NativePushBridge.kt        # implemented in Swift (section 8)
  StoreLinkProvider.kt       # store URL for app_update
  PushAnalytics.kt           # push_* events
core/firebase/src@android/.../FirebasePushMessaging.kt
core/ui/src/.../browser/InAppBrowser.kt (+ src@android, src@ios, src@jvm, src@wasmJs)
core/ui/src/.../nav/Route.kt                       # Route.Notices, Route.NoticeDetails
core/database/src/.../notice/NoticeEntity.kt, NoticeDao.kt   # + AppDatabase entry + migration
core/common/src/.../localization/Strings.kt, StringsEn.kt, StringsBn.kt
feature/notice/
  module.yaml
  src/com/<org>/notice/
    NoticeEntry.kt
    di/NoticeModule.kt
    domain/model/Notice.kt
    domain/repository/NoticeRepository.kt  domain/repository/PushDeviceRepository.kt
    domain/usecase/ObserveNoticesUseCase.kt  RefreshNoticesUseCase.kt
                   OpenNoticeUseCase.kt  SyncPushDeviceUseCase.kt  ReportPushEventUseCase.kt
    data/datasource/remote/api/PushApi.kt  source/PushSource.kt  source/ApiUrls.kt
    data/datasource/remote/dto/NoticeDto.kt  PushDeviceDto.kt  PushEventDto.kt
    data/datasource/local/NoticeLocalDataSource.kt  PushDeviceStore.kt
    data/mapper/NoticeMapper.kt
    data/repository/NoticeRepositoryImpl.kt  PushDeviceRepositoryImpl.kt  PendingPushEvents.kt
    presentation/NoticesContract.kt ... NoticesViewModel.kt  ui/NoticesScreen.kt  ui/NoticeItem.kt
    presentation/details/NoticeDetailsContract.kt ... ui/NoticeDetailsScreen.kt
    presentation/NoticeAnalytics.kt
  src@room/.../data/datasource/local/RoomNoticeLocalDataSource.kt
  src@wasmJs/.../data/datasource/local/InMemoryNoticeLocalDataSource.kt
  test/...
app/shared/src/.../push/
  PushCoordinator.kt         # PushEventSink impl: token sync, open queue, event reporting
  PushTargetResolver.kt      # PushTarget → PushNavigation (pure)
  HandlePushOpens.kt         # composable that runs PushNavigation on the back stack
  StoreLinkProviderImpl.kt
app/androidApp/src/.../push/AppMessagingService.kt  PushNotifier.kt
app/androidApp/src/AndroidManifest.xml, res/drawable/ic_stat_notification.xml
app/iosApp/ (Swift) PushBridge.swift, AppDelegate.swift, entitlements
```
Placement follows the module rules: features never touch `core/firebase`;
`feature/notice` doesn't know other features or `Route`s beyond its own, so
deep links are resolved in `app/shared`, which knows every route.

## 5. Core: payload, targets, parser

```kotlin
// core/common/.../push/PushPayload.kt
enum class PushType(val apiValue: String) {
    DEFAULT("default"), DEEP_LINK("deeplink"), APP_UPDATE("app_update"), WEB_URL("web_url");

    companion object {
        fun from(value: String?): PushType? = entries.firstOrNull { it.apiValue == value }
    }
}

/** App pages an admin may open from a push. Keys match `PushDeepLinkTarget` in openapi.yaml. */
enum class DeepLinkScreen(val key: String, val requiredParams: Set<String> = emptySet()) {
    HOME("home"),
    NOTICES("notices"),
    NOTICE_DETAILS("notice_details", setOf("id")),
    PROFILE("profile"),
    SETTINGS("settings"),
    CHAT("chat", setOf("conversationId")),
    MORE("more");

    companion object {
        fun from(key: String?): DeepLinkScreen? = entries.firstOrNull { it.key == key }
    }
}

sealed interface PushTarget {
    data class Inbox(val pushId: String) : PushTarget
    data class Screen(val pushId: String, val screen: DeepLinkScreen, val params: Map<String, String>) : PushTarget
    data class AppUpdate(val pushId: String) : PushTarget
    data class WebUrl(val pushId: String, val url: String) : PushTarget
}

data class PushPayload(
    val pushId: String,
    val type: PushType,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val target: PushTarget,
)

enum class PushOpenSource(val apiValue: String) { TRAY("tray"), LIST("list") }
```
Add a page by adding an enum entry, a `Route` case in `PushTargetResolver`
(the `when` is exhaustive, so it won't compile until you do) and the same key
in `openapi.yaml`. The admin portal reads the list from the API, so it shows
the new page without a web change.

```kotlin
// core/common/.../push/PushPayloadParser.kt
object PushPayloadParser {
    private val PUSH_ID = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
    private const val MAX_TITLE = 65
    private const val MAX_BODY = 240

    fun parse(data: Map<String, String>): PushPayload? {
        val pushId = data["push_id"]?.takeIf(PUSH_ID::matches) ?: return null
        val type = PushType.from(data["type"]) ?: PushType.DEFAULT
        return PushPayload(
            pushId = pushId,
            type = type,
            title = data["title"].orEmpty().trim().take(MAX_TITLE),
            body = data["body"].orEmpty().trim().take(MAX_BODY),
            imageUrl = data["image"]?.takeIf(::isHttps),
            target = targetOf(pushId, type, data),
        )
    }

    fun targetOf(pushId: String, type: PushType, data: Map<String, String>): PushTarget = when (type) {
        PushType.DEFAULT -> PushTarget.Inbox(pushId)
        PushType.APP_UPDATE -> PushTarget.AppUpdate(pushId)
        PushType.WEB_URL -> data["url"]?.takeIf(::isHttps)
            ?.let { PushTarget.WebUrl(pushId, it) }
            ?: PushTarget.Inbox(pushId)
        PushType.DEEP_LINK -> screenTarget(pushId, data) ?: PushTarget.Inbox(pushId)
    }

    private fun screenTarget(pushId: String, data: Map<String, String>): PushTarget.Screen? {
        val screen = DeepLinkScreen.from(data["target"]) ?: return null
        val params = parseParams(data["params"])
        if (!params.keys.containsAll(screen.requiredParams)) return null
        return PushTarget.Screen(pushId, screen, params.filterKeys { it in screen.requiredParams })
    }

    private fun parseParams(json: String?): Map<String, String> =
        json?.let {
            runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        }.orEmpty()

    private fun isHttps(url: String): Boolean =
        url.length <= 2048 && url.startsWith("https://") && ' ' !in url
}
```
`core/common` gets `kotlinx-serialization-json` for `parseParams`. If the
project keeps `core/common` free of it, pass a `(String) -> Map<String, String>`
decoder in from `app/shared` instead.

```kotlin
// core/common/.../push/PushMessaging.kt
interface PushMessaging {
    /** Current FCM token, then every refresh. Emits null where push isn't supported. */
    val tokens: Flow<String?>
    suspend fun permissionGranted(): Boolean
    /** Shows the system prompt (Android 13+, iOS). Returns the result. */
    suspend fun requestPermission(): Boolean
}

object NoOpPushMessaging : PushMessaging {          // desktop, web, and Android/iOS without Firebase config
    override val tokens: Flow<String?> = flowOf(null)
    override suspend fun permissionGranted() = false
    override suspend fun requestPermission() = false
}

// core/common/.../push/PushEventSink.kt
interface PushEventSink {
    fun onNewToken(token: String)
    fun onReceived(payload: PushPayload)
    fun onOpened(payload: PushPayload, source: PushOpenSource)
}

// core/common/.../push/StoreLinkProvider.kt
interface StoreLinkProvider {
    /** The https store page of this app for the current platform, or null if unknown. */
    suspend fun storeUrl(): String?
}
```

## 6. Android (FCM, notification, tap)

**Dependencies** (`core/firebase/module.yaml`, android only, next to Analytics
and Crashlytics):
```yaml
dependencies@android:
  - bom: com.google.firebase:firebase-bom:<current>
  - com.google.firebase:firebase-messaging
```
```kotlin
// core/firebase/src@android/.../FirebasePushMessaging.kt
class FirebasePushMessaging(private val context: Context) : PushMessaging {
    private val refreshed = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override val tokens: Flow<String?> = flow {
        emit(runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull())
        emitAll(refreshed)
    }

    fun onTokenRefreshed(token: String) { refreshed.tryEmit(token) }

    override suspend fun permissionGranted(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override suspend fun requestPermission(): Boolean =
        if (Build.VERSION.SDK_INT < 33) permissionGranted()
        else PermissionRequester.request(Manifest.permission.POST_NOTIFICATIONS)
}
```
`PermissionRequester` is a small helper in `app/androidApp` that holds an
`ActivityResultLauncher` registered in `MainActivity.onCreate` and resumes a
`CompletableDeferred` with the result. Bind `FirebasePushMessaging` only when
`generatedFirebaseConfig != null` (`kmp-environments` 5); otherwise `NoOpPushMessaging`.

**Service** (`app/androidApp`, it needs the manifest):
```kotlin
// app/androidApp/src/.../push/AppMessagingService.kt
class AppMessagingService : FirebaseMessagingService(), KoinComponent {
    private val sink: PushEventSink by inject()
    private val messaging: PushMessaging by inject()

    override fun onNewToken(token: String) {
        (messaging as? FirebasePushMessaging)?.onTokenRefreshed(token)
        sink.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayloadParser.parse(message.data) ?: return
        PushNotifier(applicationContext).show(payload)
        sink.onReceived(payload)
    }
}
```
```xml
<!-- app/androidApp/src/AndroidManifest.xml -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<application ...>
    <activity android:name=".MainActivity" android:launchMode="singleTop" ... />
    <service android:name=".push.AppMessagingService" android:exported="false">
        <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
    </service>
    <meta-data android:name="com.google.firebase.messaging.default_notification_channel_id"
               android:value="general" />
    <meta-data android:name="com.google.firebase.messaging.default_notification_icon"
               android:resource="@drawable/ic_stat_notification" />
</application>
```
The backend sends Android pushes **data-only, high priority** (`web-admin-landing`
6c), so `onMessageReceived` runs in the foreground, background and after the
app was swiped away. That gives one notification style, the same tap intent,
and a `received` event. (A force-stopped app gets nothing until it is opened
again; that's Android's rule for every push.)

```kotlin
// app/androidApp/src/.../push/PushNotifier.kt
class PushNotifier(private val context: Context) {

    fun show(payload: PushPayload) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_stat_notification)          // original monochrome vector
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent(payload))
            .build()
        // The image (if any) is loaded off the main thread with the app's image loader and set
        // with BigPictureStyle before notify(); on failure the text notification is shown.
        NotificationManagerCompat.from(context).notify(payload.pushId.hashCode(), notification)
    }

    private fun tapIntent(payload: PushPayload): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_PUSH_DATA, payload.toDataBundle())
        return PendingIntent.getActivity(
            context, payload.pushId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_GENERAL, context.getString(R.string.push_channel_general),
            NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_GENERAL = "general"
        const val EXTRA_PUSH_DATA = "push_data"
    }
}
```
`toDataBundle()` puts the original FCM data map in a `Bundle`; `MainActivity`
reads it back:
```kotlin
// MainActivity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) handlePushIntent(intent)       // not again on rotation
    setContent { App() }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handlePushIntent(intent)
}

private fun handlePushIntent(intent: Intent?) {
    val data = intent?.getBundleExtra(PushNotifier.EXTRA_PUSH_DATA)?.toStringMap() ?: return
    PushPayloadParser.parse(data)?.let { get<PushEventSink>().onOpened(it, PushOpenSource.TRAY) }
    intent.removeExtra(PushNotifier.EXTRA_PUSH_DATA)
}
```
The channel name `push_channel_general` ("General" / "সাধারণ") lives in
`res/values/strings.xml` and `res/values-bn/strings.xml`, because Android reads it
outside Compose. The small icon is an original white-on-transparent vector made
with `app-graphics-generator`, never the launcher icon.

## 7. In-app browser (`core/ui`)

`web_url` pushes open in the in-app browser: **Custom Tabs** on Android and
**`SFSafariViewController`** on iOS. The user stays in the app, sees the real
address bar, and the page runs in the system browser engine with no access to
the app. This is what "in-app browser" means on both stores, and it's safer
than an embedded `WebView`.
```kotlin
// core/ui/src/.../browser/InAppBrowser.kt
fun interface InAppBrowser { fun open(url: String) }

@Composable
expect fun rememberInAppBrowser(): InAppBrowser
```
```kotlin
// core/ui/src@android/.../browser/InAppBrowser.android.kt   (dependency androidx.browser:browser)
@Composable
actual fun rememberInAppBrowser(): InAppBrowser {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    return remember(context, toolbarColor) {
        InAppBrowser { url ->
            val tab = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
                .build()
            runCatching { tab.launchUrl(context, url.toUri()) }
                .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }  // no browser with Custom Tabs
        }
    }
}

// core/ui/src@ios/.../browser/InAppBrowser.ios.kt   (SafariServices is a system framework, no SPM)
@Composable
actual fun rememberInAppBrowser(): InAppBrowser = remember {
    InAppBrowser { url ->
        val nsUrl = NSURL.URLWithString(url) ?: return@InAppBrowser
        val safari = SFSafariViewController(uRL = nsUrl)
        topViewController()?.presentViewController(safari, animated = true, completion = null)
    }
}

// core/ui/src@jvm and src@wasmJs: open the system browser
@Composable
actual fun rememberInAppBrowser(): InAppBrowser {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) { InAppBrowser { url -> runCatching { uriHandler.openUri(url) } } }
}
```
`topViewController()` walks from the key window's `rootViewController` through
`presentedViewController`. Only `https` URLs reach `open`; the parser already
dropped the rest. The same `InAppBrowser` opens the Privacy Policy and Terms
pages (`kmp-legal-consent`), so add it once and reuse it.

If the user wants an embedded **WebView** with the app's own top bar instead
(for pages of their own site that need app styling), ask first. Then add a
`Route.WebPage(url)` screen in `feature/more` using a maintained CMP WebView
library, restrict it to an allowlist of the user's own hosts, keep JavaScript
bridges off, and open every other host in the in-app browser.

## 8. iOS (Swift bridge)

Kotlin can't call the Firebase iOS SDK under Amper, so Swift owns FCM and APNs
and talks to Kotlin through two small pieces:
```kotlin
// core/common/.../push/NativePushBridge.kt    (implemented in Swift)
interface NativePushBridge {
    fun permissionGranted(onResult: (Boolean) -> Unit)
    fun requestPermission(onResult: (Boolean) -> Unit)
    fun currentToken(onResult: (String?) -> Unit)
}

// app/shared/src@ios/.../push/IosPushMessaging.kt
class IosPushMessaging(private val bridge: NativePushBridge) : PushMessaging {
    private val refreshed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val tokens: Flow<String?> = flow {
        emit(suspendCoroutine { cont -> bridge.currentToken { cont.resume(it) } })
        emitAll(refreshed)
    }
    fun onTokenRefreshed(token: String) { refreshed.tryEmit(token) }
    override suspend fun permissionGranted() = suspendCoroutine { c -> bridge.permissionGranted { c.resume(it) } }
    override suspend fun requestPermission() = suspendCoroutine { c -> bridge.requestPermission { c.resume(it) } }
}

// app/shared/src@ios/.../push/PushIosEntry.kt    (Swift calls these)
object PushIosEntry : KoinComponent {
    fun onNewToken(token: String) {
        (get<PushMessaging>() as? IosPushMessaging)?.onTokenRefreshed(token)
        get<PushEventSink>().onNewToken(token)
    }
    fun onNotificationOpened(data: Map<String, String>) {
        PushPayloadParser.parse(data)?.let { get<PushEventSink>().onOpened(it, PushOpenSource.TRAY) }
    }
}
```
```swift
// app/iosApp/PushBridge.swift   (FirebaseMessaging added with Swift Package Manager)
import FirebaseMessaging
import UserNotifications
import Shared

final class PushBridge: NSObject, NativePushBridge, MessagingDelegate, UNUserNotificationCenterDelegate {
    func start() {
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        UIApplication.shared.registerForRemoteNotifications()   // gets the APNs token; no prompt
    }

    func permissionGranted(onResult: @escaping (KotlinBoolean) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { s in
            onResult(KotlinBoolean(bool: s.authorizationStatus == .authorized || s.authorizationStatus == .provisional))
        }
    }

    func requestPermission(onResult: @escaping (KotlinBoolean) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            onResult(KotlinBoolean(bool: granted))
        }
    }

    func currentToken(onResult: @escaping (String?) -> Void) {
        Messaging.messaging().token { token, _ in onResult(token) }
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        if let token = fcmToken { PushIosEntry.shared.onNewToken(token: token) }
    }

    // Show the banner while the app is open, too.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent n: UNNotification,
                                withCompletionHandler done: @escaping (UNNotificationPresentationOptions) -> Void) {
        done([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler done: @escaping () -> Void) {
        let data = response.notification.request.content.userInfo
            .reduce(into: [String: String]()) { acc, pair in
                if let k = pair.key as? String, let v = pair.value as? String { acc[k] = v }
            }
        PushIosEntry.shared.onNotificationOpened(data: data)
        done()
    }
}
```
```swift
// app/iosApp/AppDelegate.swift   (iosApp.swift: @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate)
final class AppDelegate: NSObject, UIApplicationDelegate {
    let push = PushBridge()
    func application(_ app: UIApplication,
                     didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()                       // reads the active GoogleService-Info.plist (UAT or PROD)
        push.start()                                  // before launch ends, so a cold-start tap is delivered
        KoinIosKt.doInitKoin(nativePushBridge: push /*, nativeCrashReporter: ... */)
        return true
    }
}
```
iOS setup (do each step, and tell the user which ones need their Apple account):
- Xcode target → Signing & Capabilities: **Push Notifications**, and
  **Background Modes → Remote notifications**. This adds `aps-environment` to
  the entitlements. App Store and TestFlight profiles must include Push
  (`app-store-connect-release` checks the profile).
- Create one **APNs auth key (.p8)** in the Apple Developer account and upload
  it in **both** Firebase projects (UAT and PROD) → Project settings → Cloud
  Messaging → Apple app configuration. The key never goes in the repo.
- Keep Firebase's method swizzling on (the default), so the APNs token is
  passed to FCM without extra code.
- Pushes with an image need a Notification Service Extension target (download
  the `image` into an attachment). Ask the user before adding it; without it
  iOS shows the text only.
- `PushIosEntry.onNotificationOpened` may run before Compose starts. That's
  fine: `PushCoordinator` queues opens until the navigation collects them.

## 9. App layer: coordinator, resolver, navigation

```kotlin
// app/shared/src/.../push/PushCoordinator.kt
class PushCoordinator(
    private val messaging: PushMessaging,
    private val syncDevice: SyncPushDeviceUseCase,
    private val reportEvent: ReportPushEventUseCase,
    private val tracker: AnalyticsTracker,
    private val scope: CoroutineScope,
    logger: AppLogger,
) : PushEventSink {

    private val log = logger.withTag("push")
    private val opens = Channel<PushTarget>(capacity = Channel.BUFFERED)
    /** Collected once by [HandlePushOpens]; buffers taps that happen before Compose is ready. */
    val openRequests: Flow<PushTarget> = opens.receiveAsFlow()

    fun start() {
        scope.launch {
            messaging.tokens.filterNotNull().distinctUntilChanged().collect { token -> sync(token) }
        }
    }

    /** Also call after login, logout, a language change and a permission change. */
    fun resync() { scope.launch { sync(token = null) } }

    override fun onNewToken(token: String) { scope.launch { sync(token) } }

    override fun onReceived(payload: PushPayload) {
        tracker.track(PushAnalytics.received(payload))
        scope.launch { reportEvent(payload.pushId, PushEventType.RECEIVED, source = null) }
    }

    override fun onOpened(payload: PushPayload, source: PushOpenSource) {
        tracker.track(PushAnalytics.opened(payload, source))
        scope.launch {
            val event = if (source == PushOpenSource.LIST) PushEventType.LIST_OPENED else PushEventType.OPENED
            reportEvent(payload.pushId, event, source)
        }
        opens.trySend(payload.target)
    }

    private suspend fun sync(token: String?) {
        syncDevice(token = token, notificationsEnabled = messaging.permissionGranted())
            .onError { it.logTo(log, operation = "sync device") }
    }
}
```
The list screen calls `onOpened(..., LIST)` through its ViewModel's use case
(section 10), so taps from the tray and the list are counted the same way.

```kotlin
// app/shared/src/.../push/PushTargetResolver.kt
sealed interface PushNavigation {
    data class Navigate(val route: Route) : PushNavigation
    data class OpenStore(val url: String) : PushNavigation
    data class OpenBrowser(val url: String) : PushNavigation
}

class PushTargetResolver(private val storeLinks: StoreLinkProvider) {

    suspend fun resolve(target: PushTarget): PushNavigation = when (target) {
        is PushTarget.Inbox -> PushNavigation.Navigate(noticesFor(target.pushId))
        is PushTarget.Screen -> PushNavigation.Navigate(routeOf(target))
        is PushTarget.AppUpdate -> storeLinks.storeUrl()
            ?.let { PushNavigation.OpenStore(it) }
            ?: PushNavigation.Navigate(noticesFor(target.pushId))
        is PushTarget.WebUrl -> PushNavigation.OpenBrowser(target.url)
    }

    private fun routeOf(target: PushTarget.Screen): Route = when (target.screen) {
        DeepLinkScreen.HOME -> Route.Home
        DeepLinkScreen.NOTICES -> noticesFor(target.pushId)
        DeepLinkScreen.NOTICE_DETAILS -> Route.NoticeDetails(Route.NoticeDetails.Data(id = target.params.getValue("id")))
        DeepLinkScreen.PROFILE -> Route.Profile
        DeepLinkScreen.SETTINGS -> Route.Settings
        DeepLinkScreen.CHAT -> Route.Conversation(Route.Conversation.Data(id = target.params.getValue("conversationId")))
        DeepLinkScreen.MORE -> Route.More
    }

    private fun noticesFor(pushId: String) = Route.Notices(Route.Notices.Data(highlightId = pushId))
}
```
Keep only the entries the app really has. Remove `CHAT` if there's no chat.
`getValue` is safe because the parser already required those params.

```kotlin
// app/shared/src/.../push/HandlePushOpens.kt
@Composable
fun HandlePushOpens(backStack: NavBackStack<NavKey>, coordinator: PushCoordinator, resolver: PushTargetResolver) {
    val browser = rememberInAppBrowser()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(coordinator) {
        coordinator.openRequests.collect { target ->
            when (val nav = resolver.resolve(target)) {
                is PushNavigation.Navigate -> backStack.openFromPush(nav.route)
                is PushNavigation.OpenStore -> runCatching { uriHandler.openUri(nav.url) }   // Play / App Store app
                is PushNavigation.OpenBrowser -> browser.open(nav.url)
            }
        }
    }
}

/** Keeps Home at the bottom, so Back from a pushed page lands in the app, and avoids duplicates. */
private fun NavBackStack<NavKey>.openFromPush(route: Route) {
    if (lastOrNull() == route) return
    if (route is Route.Notices) removeAll { it is Route.Notices }
    if (isEmpty() || first() != Route.Home) add(0, Route.Home)
    add(route)
}
```
- Put `HandlePushOpens(backStack, koinInject(), koinInject())` in
  `MainNavigation` right after `TrackScreenViews`, so the `screen_view` of the
  opened page is tracked as usual.
- If the start route is a splash or login (`feature/auth`), collect only after
  the session check finished. If the target needs a signed-in user and there is
  none, open the login screen and keep the target until login succeeds (hold it
  in `PushCoordinator`, not in a route).
- Active maintenance and force update (`kmp-update-maintenance-alerts`) still
  cover the content. A push tap never gets around them.

```kotlin
// app/shared/src/.../push/StoreLinkProviderImpl.kt
class StoreLinkProviderImpl(
    private val appStatus: AppStatusRepository,     // feature/appstatus, cached status (its section 6)
    private val env: AppEnvironment,
    private val platform: AppPlatform,
) : StoreLinkProvider {
    override suspend fun storeUrl(): String? {
        val fromStatus = (appStatus.check() as? ResultNet.Success)?.data?.update?.storeUrl
        return fromStatus?.takeIf { it.startsWith("https://") } ?: fallback()
    }

    private fun fallback(): String? = when (platform) {
        AppPlatform.ANDROID -> "https://play.google.com/store/apps/details?id=${env.androidApplicationId}"
        AppPlatform.IOS -> env.iosAppStoreId?.let { "https://apps.apple.com/app/id$it" }
        else -> null
    }
}
```
The store URL never comes from the push itself (rule in
`kmp-update-maintenance-alerts` 10): it's the admin's per-platform release
policy, or a URL built from this app's own ids. If `AppEnvironment` has no
`androidApplicationId` / `iosAppStoreId`, add them to `config/<env>/env.properties`
(`kmp-environments` 3); PROD and UAT may point to the same store page.

**Koin** (`app/shared` `appModule`, plus the platform modules):
```kotlin
single { PushCoordinator(get(), get(), get(), get(), get(named("app")), get()) }
single<PushEventSink> { get<PushCoordinator>() }
single<StoreLinkProvider> { StoreLinkProviderImpl(get(), get(), currentAppPlatform) }
factory { PushTargetResolver(get()) }
// androidKoinModule
single<PushMessaging> { if (generatedFirebaseConfig != null) FirebasePushMessaging(androidContext()) else NoOpPushMessaging }
// iosKoinModule(nativePushBridge)
single<PushMessaging> { IosPushMessaging(nativePushBridge) }
// jvm / wasmJs
single<PushMessaging> { NoOpPushMessaging }
```
Call `get<PushCoordinator>().start()` once after Koin starts (`MyApp.onCreate`,
`doInitKoin`). Call `resync()` from `feature/auth`'s login/logout callbacks in
`app/shared`, from the language switch, and when the app resumes and the
permission changed.

## 10. `feature/notice`: the push list

```yaml
# feature/notice/module.yaml (only the parts that differ from kmp-feature-scaffold 4)
product:
  type: kmp/lib
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64, wasmJs ]
aliases:
  - room: [ jvm, android, iosArm64, iosSimulatorArm64 ]   # Room has no wasmJs
dependencies:
  - //core/common
  - //core/ui
  - //core/network
  - //core/security        # installation id + device key
  - //core/preferences     # pending push events queue
dependencies@room:
  - //core/database
```

**Domain**
```kotlin
data class Notice(
    val id: String,
    val type: PushType,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val target: PushTarget,
    val sentAt: Instant,
    val readAt: Instant?,
) {
    val isUnread: Boolean get() = readAt == null
}

interface NoticeRepository {
    fun observeNotices(): Flow<List<Notice>>                     // newest first, at most 30
    fun observeUnreadCount(): Flow<Int>
    suspend fun refresh(): EmptyResult<NetworkError>
    suspend fun markRead(id: String, at: Instant)
}

interface PushDeviceRepository {
    /** Registers on first call, updates afterwards. A null token keeps the last known one. */
    suspend fun sync(token: String?, notificationsEnabled: Boolean): EmptyResult<NetworkError>
    suspend fun report(event: PushEvent)
}

enum class PushEventType(val apiValue: String) { RECEIVED("received"), OPENED("opened"), LIST_OPENED("list_opened") }
data class PushEvent(val pushId: String, val type: PushEventType, val source: PushOpenSource?, val occurredAt: Instant)
```
```kotlin
class OpenNoticeUseCase(
    private val notices: NoticeRepository,
    private val sink: PushEventSink,
    private val clock: Clock,
) {
    suspend operator fun invoke(notice: Notice) {
        if (notice.isUnread) notices.markRead(notice.id, clock.now())
        sink.onOpened(notice.toPayload(), PushOpenSource.LIST)
    }
}
```
For `default` items the screen opens `NoticeDetails` itself instead
(see Presentation below), and only marks it read and reports `list_opened`.

**Data**
```kotlin
object ApiUrls {                                   // no leading "/": keeps /uat (kmp-environments 1b)
    const val PUSH_DEVICES = "api/v1/push/devices"
    const val PUSH_INBOX = "api/v1/push/inbox"
    const val PUSH_EVENTS = "api/v1/push/events"
}
const val INBOX_LIMIT = 30
```
- `PushDeviceStore` (backed by `SecureStorage`): `installationId` (random UUID,
  created once), `deviceKey`, `lastToken`, `lastSyncedHash`. Both id and key in
  `SecureStorage`, so they stay together; on iOS they survive a reinstall and
  the user keeps their push history.
- `PushDeviceRepositoryImpl.sync`: no `deviceKey` → `POST` and save the key;
  otherwise `PUT` with `X-Device-Key`. Skip the call when the body hash
  (token, enabled, build, locale, signed-in user id) equals `lastSyncedHash`.
  A `401 device_key_invalid` clears the key and registers again once.
- `NoticeRepositoryImpl.refresh`: `GET inbox?locale=`, map, then
  `local.replaceAll(notices.take(INBOX_LIMIT))` in one transaction. Local
  `readAt` wins over a server `null` (the event may still be in flight).
- **Events never get lost:** `report` writes to `PendingPushEvents` (a small
  queue in `core/preferences`, max 200, oldest dropped) and flushes it in one
  `POST events` call; a failed flush retries on the next app start or refresh.
  The server de-duplicates, so sending twice is harmless.
- Mapper rules (each has a test): unknown `type` → `DEFAULT`; target built with
  `PushPayloadParser.targetOf` so the list and the tray agree; unparsable
  `sentAt` → item dropped; blank title → item dropped; non-`https` `imageUrl` → null.

```kotlin
// core/database/.../notice/NoticeEntity.kt
@Entity(tableName = "notices", indices = [Index("sent_at")])
data class NoticeEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    val target: String?,
    val params: String?,                            // JSON object, as received
    val url: String?,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "read_at") val readAt: Long?,
)

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY sent_at DESC LIMIT 30")
    fun observeLatest(): Flow<List<NoticeEntity>>

    @Query("SELECT COUNT(*) FROM notices WHERE read_at IS NULL")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE notices SET read_at = :at WHERE id = :id AND read_at IS NULL")
    suspend fun markRead(id: String, at: Long)

    @Transaction
    suspend fun replaceAll(items: List<NoticeEntity>) {
        val localReads = readTimes(items.map { it.id }).associate { it.id to it.readAt }
        deleteAll()
        insertAll(items.map { it.copy(readAt = it.readAt ?: localReads[it.id]) })
    }
    /* readTimes, deleteAll, insertAll */
}
```
The device keeps at most 30 rows. All older history stays on the server.

**Presentation (MVI, `kmp-feature-scaffold` 5)**
```kotlin
data class NoticesState(
    val notices: List<Notice> = emptyList(),
    val highlightId: String? = null,
    val isRefreshing: Boolean = false,
    val showPermissionCard: Boolean = false,
    val loadFailed: Boolean = false,              // only shown when the list is empty
)

sealed interface NoticesEvents {
    data object OnScreenShown : NoticesEvents
    data object OnRefresh : NoticesEvents
    data class OnNoticeClicked(val notice: Notice) : NoticesEvents
    data object OnEnableNotificationsClicked : NoticesEvents
}

sealed interface NoticesActions {
    data class OpenDetails(val id: String) : NoticesActions
}
```
- `OnScreenShown`: refresh, check the permission, log
  `NoticeAnalytics.listViewed(count, unread)` once per visit.
- `OnNoticeClicked`: `default` → mark read, report `list_opened`, track
  `PushAnalytics.opened(source = LIST)`, emit `OpenDetails(id)`. Every other type → `OpenNoticeUseCase` (the app layer
  opens the page, store or browser).
- `OnEnableNotificationsClicked` → `PushMessaging.requestPermission()`, log
  `push_permission_result`, `resync()` through a `() -> Unit` callback the app
  passes to the entry. If the user denied it before (Android "don't ask again",
  iOS denied), open the app's notification settings instead.
- `highlightId` comes from `Route.Notices.Data`; the list scrolls to it and
  shows it with a `primaryContainer` tint for 2 s.

Screen (`ui/NoticesScreen.kt`, Material 3, theme roles only):
- `PullToRefreshBox` + `LazyColumn(key = { it.id })`, content in `widthIn(max = 640.dp)`.
- `NoticeItem`: leading icon by type in a tonal circle (`Rounded.Notifications`,
  `Rounded.OpenInNew` for `deeplink`, `Rounded.SystemUpdate`, `Rounded.Language`
  for `web_url`); title `titleMedium` (bold while unread) with an unread dot;
  body `bodyMedium`, 2 lines; relative time (`5 min ago`, then the local date)
  in `labelSmall`; optional image thumbnail 56 dp, rounded, loaded with the
  app's image loader, no placeholder art from the web.
- Every click goes through `trackedClick("notice_item", mapOf("type" to n.type.apiValue))`.
- Permission card above the list while permission is off: text
  `strings.pushPermissionMessage` + `AppButton(strings.turnOnNotifications)`.
- Empty state: own illustration from `core/ui` (`app-graphics-generator`),
  `strings.noNoticesTitle` / `strings.noNoticesMessage`.
- Error with an empty list: inline message + Retry. With a cached list: keep it,
  show nothing (offline is normal here).
- `NoticeDetailsScreen`: title, full body (selectable text, links not clickable),
  image full width, sent time. Plain text only, never HTML.

Entry, with callbacks from the app:
```kotlin
fun EntryProviderScope<NavKey>.noticeEntry(onEnableNotificationsChanged: () -> Unit) {
    entry<Route.Notices> { key ->
        val contract: NoticesContract = getKoin().get<NoticesViewModel>(
            parameters = { parametersOf(key.data?.highlightId) },
        )
        NoticesScreen(contract, onPermissionChanged = onEnableNotificationsChanged)
    }
    entry<Route.NoticeDetails> { key -> /* NoticeDetailsViewModel(id) */ }
}
```
Routes in `core/ui/.../nav/Route.kt` (and in `MainNavigation`'s
`polymorphic(NavKey::class)` list, and in `Route.analyticsName`):
```kotlin
@Serializable data class Notices(val data: Data? = null) : Route() {
    @Serializable data class Data(val highlightId: String? = null)
}
@Serializable data class NoticeDetails(val data: Data) : Route() {
    @Serializable data class Data(val id: String)
}
// analyticsName: is Route.Notices -> "notices", is Route.NoticeDetails -> "notice_details"
```
Add a bell `IconButton` with a `BadgedBox` (unread count from
`ObserveUnreadCountUseCase`, "9+" above 9) to the home top bar or the bottom
bar, opening `Route.Notices()`.

## 11. Analytics (app side)

Server-side push analytics (sent, failed, received, opened, open rate per push
and per platform) are built by the backend from the events in section 3 and
shown in the admin portal (`web-admin-landing` 6c). The app also logs the same
moments to Firebase Analytics for GA4 funnels:
```kotlin
// core/common/.../push/PushAnalytics.kt
object PushAnalytics {
    fun received(p: PushPayload) = AnalyticsEvent("push_received", mapOf("push_id" to p.pushId, "push_type" to p.type.apiValue))
    fun opened(p: PushPayload, source: PushOpenSource) = AnalyticsEvent(
        "push_opened", mapOf("push_id" to p.pushId, "push_type" to p.type.apiValue, "source" to source.apiValue),
    )
    fun permissionResult(granted: Boolean, asked: Boolean) =
        AnalyticsEvent("push_permission_result", mapOf("granted" to granted, "asked" to asked))
    fun invalidTarget(type: String, reason: String) =
        AnalyticsEvent("push_target_invalid", mapOf("push_type" to type, "reason" to reason))
}
// feature/notice/.../presentation/NoticeAnalytics.kt
internal object NoticeAnalytics {
    fun listViewed(count: Int, unread: Int) = AnalyticsEvent("notice_list_viewed", mapOf("count" to count, "unread" to unread))
}
```
- `push_id` is a public ULID, not personal data. Never log the title, body,
  URL, token, installation id or device key.
- Register `push_id`, `push_type` and `source` as custom dimensions in both
  Firebase projects (`kmp-analytics-logging` 2).
- Set the user property `push_enabled` (`true` / `false`) after each sync.
- Logs use the tags `push` (app layer) and `notice` / `notice.data` (feature).
  Token refreshes and syncs are `DEBUG`; failed syncs `WARN` via `logTo`.
- iOS has no `received` event (app code doesn't run when an alert arrives);
  the admin page labels "Received" as Android only. FCM's own delivery report
  per `analytics_label` (Firebase console → Messaging → Reports) covers both
  platforms, and the backend sets that label to the push id.

## 12. Strings (`core/common` localization)

| Key | English | Bangla |
|---|---|---|
| `noticesTitle` | Notifications | নোটিফিকেশন |
| `noNoticesTitle` | No notifications yet | এখনো কোনো নোটিফিকেশন নেই |
| `noNoticesMessage` | News, offers and updates will show up here. | খবর, অফার ও আপডেট এখানে দেখা যাবে। |
| `pushPermissionMessage` | Turn on notifications so you don't miss updates. | আপডেট মিস না করতে নোটিফিকেশন চালু করুন। |
| `turnOnNotifications` | Turn on | চালু করুন |
| `openSettings` | Open settings | সেটিংস খুলুন |
| `noticeLoadFailed` | Couldn't load notifications. | নোটিফিকেশন লোড করা যায়নি। |
| `minutesAgo(n)` | $n min ago | $n মিনিট আগে |
| `hoursAgo(n)` | $n h ago | $n ঘণ্টা আগে |
| `justNow` | Just now | এইমাত্র |

Reuse `tryAgain` and `ok` if they exist. Push titles and bodies come from the
admin already in the device's language (the device registers its `locale`).

## 13. Tests first (order from `kmp-feature-scaffold` 10)

1. **`PushPayloadParserTest`** (`core/common`): each type maps to its target;
   unknown type → `Inbox`; missing or malformed `push_id` → `null`; `web_url`
   with `http://`, `javascript:`, a space or no URL → `Inbox`; `deeplink` with an
   unknown target or a missing required param → `Inbox`; extra params are
   dropped; bad `params` JSON → `Inbox`; title cut to 65, body to 240; `image`
   over `http` → `null`.
2. **`DeepLinkContractTest`**: `DeepLinkScreen` keys equal the
   `PushDeepLinkTarget` enum in `backend/contracts/openapi.yaml` (read the file
   in the test), so app and admin portal can't drift.
3. **`PushTargetResolverTest`** (fake `StoreLinkProvider`): every
   `DeepLinkScreen` resolves to its route with its param; `Inbox` → `Notices`
   with `highlightId`; `AppUpdate` → `OpenStore` with the provider URL, and →
   `Notices` when it returns null; `WebUrl` → `OpenBrowser`.
4. **`StoreLinkProviderImplTest`**: status URL wins; non-`https` status URL →
   fallback; Android fallback uses the application id; iOS without a store id → null.
5. **`NoticeMapperTest`**: rules in section 10 Data.
6. **`NoticeRepositoryImplTest`** (`MockEngine`, base `https://test.local/uat/`,
   fake local source): requests `/uat/api/v1/push/inbox?locale=bn` with
   `X-Device-Key`; keeps at most 30; local `readAt` survives a refresh; a
   network error leaves the cache untouched.
7. **`PushDeviceRepositoryImplTest`**: first sync POSTs and stores the key;
   next sync PUTs with the key; identical data skips the call; `401
   device_key_invalid` re-registers once; the key never appears in logs (fake logger).
8. **`PendingPushEventsTest`**: events are queued, flushed in one call, kept on
   failure, capped at 200.
9. **`PushCoordinatorTest`** (fakes, `TestScope`): a token emission syncs once;
   the same token twice syncs once; `onOpened` before anyone collects is
   delivered when collection starts; `onOpened` tracks `push_opened` and
   reports `opened` (tray) or `list_opened` (list); `onReceived` reports `received`.
10. **`NoticesViewModelTest`**: shown → refresh + `notice_list_viewed`; default
    item → read + `OpenDetails`; deeplink item → `OpenNoticeUseCase`; permission
    card follows the fake permission; failed refresh with a cache shows no error.
11. **`openFromPushTest`**: cold start → `[Home, route]`; same route on top →
    unchanged; a second `Notices` replaces the first.
12. **UI tests** (`runComposeUiTest`): unread item is bold with a dot; tapping
    sends `OnNoticeClicked`; empty state and permission card render; the
    highlighted item is visible after load.

Android `PushNotifier` and the Swift bridge are checked on devices (section 15).

## 14. Rules

- One source of truth: the server's inbox. The app shows the latest 30 and
  never deletes server history; admins hide a push instead of deleting it.
- Only parsed, validated targets are opened: `https` only, known pages only,
  required params only. Anything else opens the push list.
- The `app_update` store URL comes from the app's release policy or its own
  ids, never from the push.
- Ask for notification permission at a moment that explains it (the push
  list's card, after onboarding, or a settings switch), never on the first
  launch screen. Respect "denied": offer settings, don't ask in a loop.
- Device key and installation id live in `SecureStorage`; FCM tokens are sent
  only to the backend. None of them are logged or sent to analytics.
- Push text is plain text everywhere. No HTML, no Markdown, no clickable links
  inside the body.
- UAT builds register only with the UAT backend and Firebase project. Never
  copy a token between environments to "test in PROD".
- Desktop and web bind `NoOpPushMessaging`; the push list still works there if
  the app includes it, but it stays empty without a registered device.
- No copyrighted images: the notification icon, empty state art and any
  sample push images are original or open-licensed (`app-graphics-generator`).

## 15. Show the result (required)

Follow `kmp-feature-scaffold` sections 12 and 13. Send real pushes from the
**UAT** admin portal ("Send test to my devices" after labelling the test phone
as a test device) and capture on Android and iOS, light and dark:
- a notification in the tray (with and without an image on Android)
- the push list with unread and read items, the empty state, the permission card,
  and the list in Bangla
- one short recording per type: tap a `default` push → list with the item
  highlighted → details; `deeplink` → the chosen page; `app_update` → the store
  page; `web_url` → the in-app browser, then back to the app
- the admin push detail page showing the received / opened counts going up
  after those taps

If the iOS steps that need the user's Apple account (APNs key, capabilities)
aren't done yet, say so and list them. Don't claim iOS push works before a
real device received one.

## Checklist

- [ ] Backend `Push` module, contract and admin page exist (`web-admin-landing` 6c); contract first
- [ ] Separate UAT / PROD Firebase projects; APNs key uploaded to both; UAT build registers with `/uat` backend only
- [ ] `PushPayloadParser` validates everything; unknown or unsafe → push list
- [ ] Android: data-only messages, `AppMessagingService`, channel, own small icon, `POST_NOTIFICATIONS` asked in context, tap via `MainActivity` intent
- [ ] iOS: Swift bridge (FirebaseMessaging via SPM), Push + Remote notifications capabilities, foreground banners, tap via `PushIosEntry`
- [ ] Device registration with `installationId` + `deviceKey` in `SecureStorage`; resync on token, login, logout, language, permission change
- [ ] `PushTargetResolver` exhaustive over `DeepLinkScreen`; keys match `openapi.yaml` (test)
- [ ] `app_update` → store URL from release policy or app ids; `web_url` → in-app browser (Custom Tabs / SFSafariViewController)
- [ ] `feature/notice`: list of latest 30 (Room cache, server history kept), unread state, highlight, details, empty / error / permission states, bell badge
- [ ] Events `received` (Android) / `opened` / `list_opened` queued and sent; `push_*` analytics events; no personal data
- [ ] Strings in En + Bn; theme roles only; original artwork
- [ ] Tests from section 13 written first and green; screenshots, recordings and file report delivered
