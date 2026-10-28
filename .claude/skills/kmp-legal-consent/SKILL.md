---
name: kmp-legal-consent
description: Connect a KMP + CMP app (KMP-MultiModule-Template / Amper / Koin / Navigation3) to the Privacy Policy and Terms & Conditions pages served by web-admin-landing - per-environment legal URLs, links in More, Settings and the login/sign-up screens, opened in the in-app browser, a required consent checkbox at sign-up, and a blocking re-acceptance screen when the admin publishes a version that users must accept. Use when adding privacy policy or terms links, sign-up consent, or "we've updated our terms" flows to the app.
---

# Privacy Policy and Terms in the App (links + consent)

The legal text lives on the website, not in the app. Admins write and version
it in the admin portal (`web-admin-landing` 6d), the landing page shows it at
`/privacy` and `/terms`, and the app:

1. **Links** to both pages from More, Settings > Privacy, Login and Sign up,
   opening them in the **in-app browser** (`kmp-push-notifications` 7).
2. **Asks for consent at sign-up**: an unticked checkbox, required to create an
   account.
3. **Asks again** with a full screen when the admin publishes a version marked
   "users must accept", and records each acceptance on the server.

No legal text is bundled in the app, so an update never needs an app release.
Never write real legal wording in the app or the skill output: the pages show
whatever the admin published, and the starter text is a reviewed template
(`web-admin-landing` 6d). Tell the user the text needs review by someone
qualified before release.

Related: URLs per environment come from `kmp-environments`, the HTTP client from
`kmp-networking-ktor`, sign-in from `kmp-supabase` 6, module rules and MVI from
`kmp-feature-scaffold`, theme and `Strings` from `compose-multiplatform-ui`,
analytics from `kmp-analytics-logging`, and the store URLs from
`play-console-release` / `app-store-connect-release`.

## 1. Read the project first

```bash
ls feature/ core/
grep -rn "WEB_URL\|webUrl" config core/common 2>/dev/null
grep -rln "InAppBrowser" core/ui 2>/dev/null
grep -rn "privacy\|terms" --include=*.kt feature core | head
```
- No `webUrl` in `AppEnvironment` → add it first (`kmp-environments` 1, 3).
- No `InAppBrowser` in `core/ui` → add it exactly as in `kmp-push-notifications`
  section 7 (Custom Tabs on Android, `SFSafariViewController` on iOS, system
  browser on desktop and web). Don't write a second one.
- Existing hardcoded legal URLs or bundled legal text → replace them with this
  flow and list them in the file report.
- Check that the backend has `web-admin-landing` 6d. If it doesn't, the links
  still work once the pages are published, but the consent gate needs
  `GET api/v1/legal/status`. Tell the user and build the links first.

## 2. Where things go

```
core/common/src/.../legal/
  LegalDocType.kt              # enum PRIVACY("privacy"), TERMS("terms")
  LegalUrls.kt                 # builds the page URL from AppEnvironment.webUrl + language
  SignupConsentStore.kt        # interface: remembers "agreed at sign-up" until the first status check
core/preferences/src/.../legal/
  SignupConsentStoreImpl.kt    # DataStore / settings implementation
core/ui/src/.../components/legal/
  LegalLinksText.kt            # "I agree to the Terms & Conditions and the Privacy Policy" with two links
  rememberOpenLegal.kt         # (LegalDocType) -> Unit: LegalUrls + InAppBrowser + analytics
core/common/.../localization/Strings.kt, StringsEn.kt, StringsBn.kt   # new keys (section 7)

feature/legal/                 # the re-acceptance gate (an overlay like feature/appstatus)
  module.yaml
  src/com/<org>/legal/
    domain/model/LegalStatus.kt        # LegalItem(type, currentId, currentVersion, acceptedVersion, mustAccept, title, changeSummary)
    domain/repository/LegalRepository.kt
    domain/usecase/GetLegalStatusUseCase.kt  AcceptLegalUseCase.kt
    data/source/LegalApi.kt  LegalSource.kt
    data/dto/LegalStatusDto.kt  AcceptLegalRequestDto.kt
    data/mapper/LegalMapper.kt
    data/repository/LegalRepositoryImpl.kt
    presentation/LegalConsentContract.kt  State.kt  Events.kt  Actions.kt  ViewModel.kt
    presentation/ui/LegalConsentHost.kt  LegalConsentScreen.kt
    di/LegalModule.kt
  test/...

feature/more       # "Legal" group: Privacy Policy, Terms & Conditions, Open-source licences
feature/settings   # Settings > Privacy: a "Privacy Policy" row
feature/auth       # Login: "By continuing…" line; Sign up: required checkbox
app/shared/.../App.kt   # wraps NavDisplay in LegalConsentHost (inside AppStatusHost)
```
Module rules (`kmp-feature-scaffold` 2):
- `LegalUrls`, `LegalDocType` and `SignupConsentStore` are in `core/common`
  because `feature/more`, `feature/settings`, `feature/auth` and `feature/legal`
  all need them, and features never import each other.
- `feature/legal` depends on `core/common`, `core/ui`, `core/network`,
  `core/preferences`. Only `app/shared` depends on it. It has no route: it is
  an overlay, not a screen in the back stack.
- `feature/auth` never calls `feature/legal`. It only writes
  `SignupConsentStore`; the gate reads it (section 5).

## 3. URLs per environment

```kotlin
// core/common/.../legal/LegalDocType.kt
enum class LegalDocType(val slug: String) { PRIVACY("privacy"), TERMS("terms") }

// core/common/.../legal/LegalUrls.kt
class LegalUrls(private val webUrl: String) {            // "https://abc.com/" or "https://abc.com/uat/"
    init { require(webUrl.startsWith("https://") && webUrl.endsWith("/")) { "webUrl must be https and end with /" } }

    /** The page as the app opens it: language from the app, no site navigation. */
    fun forApp(type: LegalDocType, language: Language): String =
        "$webUrl${language.code}/${type.slug}?view=app"

    /** The stable public page, for sharing or the store listing. */
    fun public(type: LegalDocType): String = "$webUrl${type.slug}"
}
// Language.code: EN -> "en", BN -> "bn" (add the property to the existing enum if it isn't there)
```
| | PROD | UAT |
|---|---|---|
| `WEB_URL` | `https://abc.com/` | `https://abc.com/uat/` |
| Privacy in the app (Bangla) | `https://abc.com/bn/privacy?view=app` | `https://abc.com/uat/bn/privacy?view=app` |
| Terms, public | `https://abc.com/terms` | `https://abc.com/uat/terms` |

- The URL comes **only** from `AppEnvironment.webUrl` through DI
  (`single { LegalUrls(get<AppEnvironment>().webUrl) }`). No URL string in any
  feature, so a UAT build always opens the UAT pages.
- Paths are appended to a base that ends in `/`, never started with `/`, for
  the same reason as `ApiUrls` (`kmp-environments` 1b).

Opening a page (one helper, used by every screen):
```kotlin
// core/ui/.../components/legal/rememberOpenLegal.kt
@Composable
fun rememberOpenLegal(source: String): (LegalDocType) -> Unit {
    val urls: LegalUrls = koinInject()
    val analytics: Analytics = koinInject()
    val browser = rememberInAppBrowser()
    val language = LocalizationManager.currentLanguage
    return remember(urls, browser, language, source) {
        { type ->
            analytics.logEvent("legal_link_opened", mapOf("type" to type.slug, "source" to source))
            browser.open(urls.forApp(type, language))
        }
    }
}
```
`source` is a fixed screen name (`more`, `settings`, `login`, `signup`,
`consent`). The event carries no personal data.

## 4. Links in the UI

### Link text with two links (`core/ui`)
```kotlin
// core/ui/.../components/legal/LegalLinksText.kt
@Composable
fun LegalLinksText(
    template: String,                         // strings.legalAgreeTemplate: "I agree to the {terms} and the {privacy}."
    onOpen: (LegalDocType) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val strings = LocalizationManager.strings
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        var rest = template
        while (rest.isNotEmpty()) {
            val next = listOf("{terms}" to LegalDocType.TERMS, "{privacy}" to LegalDocType.PRIVACY)
                .mapNotNull { (key, type) -> rest.indexOf(key).takeIf { it >= 0 }?.let { Triple(it, key, type) } }
                .minByOrNull { it.first }
            if (next == null) { append(rest); break }
            val (at, key, type) = next
            append(rest.substring(0, at))
            val label = if (type == LegalDocType.TERMS) strings.legalTerms else strings.legalPrivacyPolicy
            withLink(LinkAnnotation.Clickable(tag = type.slug, styles = linkStyle) { onOpen(type) }) { append(label) }
            rest = rest.substring(at + key.length)
        }
    }
    Text(text = text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
```
The placeholders let each language put the links where its grammar needs them
(Bangla puts the verb last). Links are real link annotations, so TalkBack and
VoiceOver announce them as links, each with its own 48 dp touch area through
the text line height (`bodyMedium` with at least 20 sp line height).

### `feature/more`
A "Legal" group below "About", using the existing list item component:
```kotlin
val openLegal = rememberOpenLegal(source = "more")
SectionHeader(strings.legalSection)
MoreItem(icon = Icons.Rounded.PrivacyTip, title = strings.legalPrivacyPolicy, trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
         onClick = { openLegal(LegalDocType.PRIVACY) })
MoreItem(icon = Icons.Rounded.Gavel, title = strings.legalTerms, trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
         onClick = { openLegal(LegalDocType.TERMS) })
MoreItem(icon = Icons.Rounded.Description, title = strings.openSourceLicences, onClick = { env.navigate(Route.Licences) })
```
The `OpenInNew` icon tells the user the item opens a web page. Icons are Material
Symbols (Apache 2.0). These rows also show for signed-out users if More is
reachable before login.

### `feature/settings`
In Settings > Privacy (with analytics and notification toggles), add a
"Privacy Policy" row with the same `openLegal(LegalDocType.PRIVACY)`.

### `feature/auth`
- **Login**, below the buttons (including Google / Apple, which can create an
  account): `LegalLinksText(strings.legalContinueTemplate, openLegal)`,
  centred, `bodySmall`.
- **Sign up (email):** a checkbox row, **unticked by default**:
  ```kotlin
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.toggleable(value = state.agreedToLegal, role = Role.Checkbox,
          onValueChange = { onEvent(RegisterEvents.LegalAgreementChanged(it)) }),
  ) {
      Checkbox(checked = state.agreedToLegal, onCheckedChange = null)
      Spacer(Modifier.width(8.dp))
      LegalLinksText(strings.legalAgreeTemplate, onOpen = openLegal)
  }
  if (state.showLegalError) Text(strings.legalMustAgree, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
  ```
  The "Create account" button stays enabled; tapping it without the tick shows
  `legalMustAgree` and moves focus to the checkbox (a disabled button gives no
  reason). Tapping a link opens the page and does not toggle the box.
- `RegisterViewModel` on success: `signupConsentStore.markAgreed(clock.now())`
  **before** navigating, then continues as in `kmp-supabase` 6. It also sends
  the agreement in the sign-up metadata, so Supabase keeps a copy:
  `data = buildJsonObject { put("display_name", name); put("legal_agreed_at", now.toString()) }`.

## 5. The consent gate (`feature/legal`)

```
SignedIn (AuthState from core/supabase) ─┬─ app start / sign-in / resume (≥ 6 h since last check)
                                         ▼
LegalConsentViewModel ── GetLegalStatusUseCase ── GET api/v1/legal/status?locale=bn
        │
        ├─ nothing to accept ............................ content()
        ├─ must accept + fresh sign-up consent .......... POST acceptances (source=signup), content()
        └─ must accept .................................. LegalConsentScreen (covers the app)
```

Contract (from `backend/contracts/openapi.yaml`, `web-admin-landing` 6d):
```kotlin
// data/dto/LegalStatusDto.kt
@Serializable data class LegalStatusDto(val items: List<LegalItemDto>)
@Serializable data class LegalItemDto(
    val type: String, val currentId: String, val currentVersion: Int,
    val acceptedVersion: Int? = null, val mustAccept: Boolean,
    val title: String, val changeSummary: String? = null,
)
@Serializable data class AcceptLegalRequestDto(
    val versionIds: List<String>, val source: String,       // "signup" | "reaccept"
    val locale: String, val platform: String, val appBuild: Int,
)
// ApiUrls (no leading "/")
const val LEGAL_STATUS = "$V1/legal/status"
const val LEGAL_ACCEPTANCES = "$V1/legal/acceptances"
```
- The mapper drops unknown `type` values (a newer server may add
  `account_deletion`), so an older app never crashes or blocks on them.
- Both calls use the shared `HttpClient` with the signed-in user's token
  (`TokenProvider`, `kmp-networking-ktor`) and return `ResultNet` through
  `safeApiCall`.

ViewModel rules (each has a test):
- Runs only while `AuthState` is `SignedIn`. On `SignedOut` the state resets and
  nothing is shown.
- **Sign-up consent:** if any item has `mustAccept` and
  `signupConsentStore.takeIfFresh(now)` returns true (agreed within the last
  7 days on this device; the call clears it), accept every `mustAccept` item
  with `source = "signup"` without showing the screen. If that call fails, show
  the screen. A user who confirms their email on another device sees the screen
  once; that's expected.
- **Fail open:** a failed status check (offline, timeout, 5xx) never blocks the
  app and never shows an error. It retries on the next resume. Only a successful
  answer with `mustAccept` shows the screen.
- **Accept:** sends the `currentId` of every `mustAccept` item. `409
  legal_version_not_current` → reload the status and show the new text (the
  admin published again). Other errors → snackbar `legalAcceptFailed`, the
  screen stays.
- **Sign out** from the screen raises `onSignOut`; `app/shared` calls auth's
  logout (`kmp-feature-scaffold` 2a: features don't log out on their own).
- Throttle: the last successful check time is kept in memory; resume checks run
  at most every 6 hours, sign-in always checks.

Host and screen:
```kotlin
// presentation/ui/LegalConsentHost.kt
@Composable
fun LegalConsentHost(isSignedIn: Boolean, onSignOut: () -> Unit, content: @Composable () -> Unit) {
    val contract: LegalConsentContract = koinInject()
    val state by contract.state.collectAsStateWithLifecycle()

    LaunchedEffect(isSignedIn) { contract.onEvent(LegalConsentEvents.SignedInChanged(isSignedIn)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { contract.onEvent(LegalConsentEvents.AppResumed) }
    LaunchedEffect(Unit) {
        contract.actions.collect { action ->
            when (action) {
                LegalConsentActions.SignOut -> onSignOut()
            }
        }
    }

    if (state.pending.isEmpty()) content()
    else LegalConsentScreen(state = state, onEvent = contract::onEvent)
}
```
```kotlin
// app/shared/.../App.kt
AppTheme {
    AppStatusHost {                                           // maintenance and force update win
        LegalConsentHost(isSignedIn = authState is AuthState.SignedIn, onSignOut = ::logout) {
            NavDisplay(/* unchanged */)
        }
    }
}
```
`LegalConsentScreen` (Material 3, `compose-multiplatform-ui` 3):
- Full-screen `Surface(colorScheme.background)`, content in
  `widthIn(max = 560.dp)`, safe-area padding, scrollable.
- Title: `legalFirstTitle` ("Before you continue") when no item has an
  `acceptedVersion`, else `legalUpdatedTitle` ("We've updated our policies").
  Then `legalReviewMessage`.
- One `OutlinedCard` per pending item: its `title` (from the server, already in
  the user's language), "Version 3", the `changeSummary` when present, and a
  `TextButton(strings.legalRead)` that opens it with
  `rememberOpenLegal("consent")`.
- A checkbox row `legalIAgree`, then `AppButton(strings.legalAcceptContinue,
  enabled = state.agreed, isLoading = state.isSaving)`, then
  `TextButton(strings.signOut)`.
- Back press on Android does nothing on this screen (`BackHandler {}`); the user
  either accepts or signs out. In UAT, show `AppVersionText(env.versionLabel)` at
  the bottom.
- Log `legal_accepted` with `types` and `source` after a successful accept.

## 6. Account deletion link (Play requirement)

If the app has accounts, Google Play also needs a web page where users can ask
to delete their account and data (`play-console-release` 2). The app's own
delete-account flow lives in `feature/user`. Add a `LegalDocType` for the web
page (for example `account_deletion` with slug `delete-account`) only after the
backend serves it (`web-admin-landing` 6d: new types are added the same way).
Ask the user before building it.

## 7. Strings (`core/common` localization)

Add to `Strings`, `StringsEn` and `StringsBn` (never a default in the interface):

| Member | English | Bangla |
|---|---|---|
| `legalSection` | Legal | আইনি তথ্য |
| `legalPrivacyPolicy` | Privacy Policy | গোপনীয়তা নীতি |
| `legalTerms` | Terms & Conditions | শর্তাবলী |
| `legalAgreeTemplate` | I agree to the {terms} and the {privacy}. | আমি {terms} ও {privacy}-তে সম্মত। |
| `legalContinueTemplate` | By continuing, you agree to the {terms} and the {privacy}. | চালিয়ে গেলে আপনি {terms} ও {privacy}-তে সম্মত হচ্ছেন। |
| `legalMustAgree` | Please agree to continue. | চালিয়ে যেতে সম্মতি দিন। |
| `legalFirstTitle` | Before you continue | শুরু করার আগে |
| `legalUpdatedTitle` | We've updated our policies | আমাদের নীতিমালা হালনাগাদ হয়েছে |
| `legalReviewMessage` | Please read and accept to keep using the app. | অ্যাপ ব্যবহার চালিয়ে যেতে অনুগ্রহ করে পড়ে সম্মতি দিন। |
| `legalRead` | Read | পড়ুন |
| `legalVersion(n)` | Version $n | সংস্করণ ${n.localizeNumber()} |
| `legalIAgree` | I have read and agree | আমি পড়েছি এবং সম্মত |
| `legalAcceptContinue` | Accept and continue | সম্মতি দিয়ে চালিয়ে যান |
| `legalAcceptFailed` | Couldn't save your consent. Please try again. | সম্মতি সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন। |

Reuse `signOut` and `openSourceLicences` if they exist. Ask a fluent Bangla
speaker to review the Bangla before release. `{terms}` and `{privacy}` must
appear exactly once in each template; a unit test checks every `StringsXx`.

## 8. Tests first (order from `kmp-feature-scaffold` 10)

1. **`LegalUrls`** (core/common): PROD `forApp(PRIVACY, BN)` =
   `https://abc.com/bn/privacy?view=app`; UAT = `https://abc.com/uat/bn/privacy?view=app`;
   `public(TERMS)` keeps `/uat/`; a `webUrl` without the trailing `/` or with
   `http://` throws.
2. **Strings:** every `StringsXx` has `{terms}` and `{privacy}` exactly once in
   both templates.
3. **Mapper:** unknown `type` dropped; missing `changeSummary` → null.
4. **Repository** (`MockEngine`, base `https://test.local/uat/`): status calls
   `/uat/api/v1/legal/status?locale=bn` with the bearer token; accept sends the
   body above; 409 maps to `NotCurrent`; network failure → `ResultNet` error.
5. **`LegalConsentViewModel`** (fakes + `FixedClock`): signed out → nothing
   pending; status fails → nothing pending; `mustAccept` → pending shown; fresh
   sign-up consent → accepted with `source=signup`, nothing shown, store
   cleared; stale (8 days) → shown; accept succeeds → pending empty and
   `legal_accepted` logged; 409 → status reloaded; other error → snackbar,
   still pending; sign out → `SignOut` action; resume within 6 h → no second call.
6. **`RegisterViewModel`:** submit without the tick → `showLegalError`, no
   sign-up call; with the tick → sign-up called with `legal_agreed_at` and
   `markAgreed` before navigation.
7. **UI** (`runComposeUiTest`): tapping each link in `LegalLinksText` calls
   `onOpen` with the right type and does not toggle the checkbox;
   `LegalConsentScreen` button disabled until the box is ticked; More shows both
   rows and opens the right URL through a fake `InAppBrowser`.

## 9. Show the result (required)

Screenshots per `kmp-feature-scaffold` 12, Android and iOS, light and dark,
English and Bangla: More with the Legal group, Sign up with the checkbox and
the error, Login with the "By continuing" line, the consent screen (first time
and updated), and the privacy page open in Custom Tabs / Safari view in UAT
(showing `/uat/` in the address bar). Record one short video: tick the box,
sign up, land in the app without the gate; then publish Terms v2 "must accept"
in the UAT admin portal, resume the app, accept. Then give the file report
(`kmp-feature-scaffold` 13), with the new `WEB_URL` key called out.

## Checklist
- [ ] `WEB_URL` in both `env.properties`, `AppEnvironment.webUrl`, `LegalUrls` built only from it (UAT opens `/uat/` pages)
- [ ] Links open in the in-app browser (Custom Tabs / `SFSafariViewController`) with `?view=app` and the app's language
- [ ] More has Privacy Policy + Terms & Conditions; Settings > Privacy has Privacy Policy; Login has the "By continuing" line
- [ ] Sign up has an unticked consent checkbox with both links; submit without it shows the reason; agreement stored for the first status check
- [ ] `feature/legal` overlay: only when signed in, fails open, accepts only current versions, handles 409, sign out instead of accept
- [ ] No legal text or URLs hardcoded in the app; texts only from `Strings`; Bangla reviewed
- [ ] Server pages published (`web-admin-landing` 6d) before the store submission; the user told that the template text needs a qualified review
- [ ] Tests from section 8 written first and green; screenshots, video and file report delivered
