---
name: kmp-environments
description: Set up UAT and Production environments in a KMP + CMP app (KMP-MultiModule-Template / Amper) for API base URLs, Supabase and Firebase, with a separate Supabase project and a separate google-services.json / GoogleService-Info.plist per environment, and the app version label ("v1.1.1 (UAT)" vs "v1.1.1"). Use when adding or switching environments, showing the app version, wiring Supabase or Firebase per env, or preparing a release build.
---

# UAT and Production Environments (API + Supabase + Firebase)

The app has two environments, **UAT** and **PROD**. Each has its own API base URL,
flags, **its own Supabase project** (database, auth users, storage, edge
functions) and **its own Firebase project** (Analytics, Crashlytics, App
Distribution, FCM, Remote Config), each with its own config files. Switching
environments is one command. Shared code never contains `if (prod)` URL strings.

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
Its build is Amper, which has no Android product flavors and no Google Services
Gradle plugin. Firebase is initialized manually from `FirebaseConfig` in
`core/firebase`. This skill keeps that design and feeds it from per-environment
JSON and plist files. Networking details are in `kmp-networking-ktor`.

## 1. Layout

```
config/
  uat/
    env.properties               # committed: BASE_URL, ANDROID_APPLICATION_ID, flags
    google-services.json         # NOT committed (Firebase UAT project, Android app)
    GoogleService-Info.plist     # NOT committed (Firebase UAT project, iOS app)
    supabase.properties          # NOT committed (Supabase UAT project URL + publishable key)
  prod/
    env.properties               # committed
    google-services.json         # NOT committed (Firebase PROD project)
    GoogleService-Info.plist     # NOT committed
    supabase.properties          # NOT committed (Supabase PROD project)
  version.properties             # committed: VERSION_NAME + VERSION_CODE, shared by all envs
  README.md                      # where to download each file, how to switch
scripts/
  use_env.py                     # selects an environment and generates config code

core/common/src/com/<org>/common/env/
  AppEnvironment.kt              # committed: EnvName + AppEnvironment model
  GeneratedEnvironment.kt        # GENERATED, git-ignored
core/firebase/src/com/<org>/.../firebase/
  GeneratedFirebaseConfig.kt     # GENERATED, git-ignored (Android FirebaseConfig)
app/iosApp/src/GoogleService-Info.plist   # COPIED for the active env, git-ignored
```

`env.properties` example:
```properties
# config/uat/env.properties
ENV_NAME=UAT
APP_NAME=KMP Template
# Backend UAT lives under /uat (web-admin-landing 12b). Keep the trailing slash.
BASE_URL=https://abc.com/uat/
# Landing page root (Privacy Policy, Terms: kmp-legal-consent). Same as BASE_URL here.
WEB_URL=https://abc.com/uat/
ANDROID_APPLICATION_ID=com.touhidapps.kmp
IOS_BUNDLE_ID=com.touhidapps.kmp
IS_DEBUG=true
```
```properties
# config/prod/env.properties
ENV_NAME=PROD
APP_NAME=KMP Template
BASE_URL=https://abc.com/
WEB_URL=https://abc.com/
ANDROID_APPLICATION_ID=com.touhidapps.kmp
IOS_BUNDLE_ID=com.touhidapps.kmp
IS_DEBUG=false
```

Values per environment feed the core module that owns them
(`kmp-clean-architecture` section 2b): `BASE_URL` → `NetworkConfig` in
`core/network`, `supabase.properties` → `SupabaseConfig` in `core/supabase`,
Firebase files → `core/firebase`, and, when the app has ads,
`ADMOB_APP_ID_*` / `ADMOB_BANNER_ID_*` keys → `AdsConfig` in `core/ads`. UAT
must use Google's published test ad unit IDs, so testers never click real ads.
The AI backend path stays under `BASE_URL`; no AI provider key ever goes in
`env.properties`.

### 1b. API URLs per environment (Android and iOS)

Android and iOS read the same `BASE_URL` from the active environment. The backend
from `web-admin-landing` (section 12b) serves both environments from one domain,
with UAT under `/uat`:

| | PROD | UAT |
|---|---|---|
| `BASE_URL` (Android + iOS) | `https://abc.com/` | `https://abc.com/uat/` |
| `WEB_URL` (landing, legal pages) | `https://abc.com/` | `https://abc.com/uat/` |
| Privacy Policy in the app | `https://abc.com/en/privacy?view=app` | `https://abc.com/uat/en/privacy?view=app` |
| Example call `api/v1/profile` | `https://abc.com/api/v1/profile` | `https://abc.com/uat/api/v1/profile` |
| Supabase project | "MyApp Prod" (`https://<prod-ref>.supabase.co`) | "MyApp UAT" (`https://<uat-ref>.supabase.co`) |
| Firebase project | "MyApp Prod" | "MyApp UAT" |

Two rules make the `/uat` prefix survive:
- `BASE_URL` **ends with `/`**. The script refuses a value without it.
- Request paths in `ApiUrls` are **relative, with no leading `/`** (`"api/v1/profile"`).
  A path that starts with `/` replaces the base path, so `"/api/v1/profile"` silently
  calls **PROD's** `https://abc.com/api/v1/profile` from a UAT build. See
  `kmp-networking-ktor` section 4.

If the backend uses a separate host instead (`https://uat-api.abc.com/`), only
`BASE_URL` changes. The rules above still hold. `WEB_URL` is the landing page
root (`web-admin-landing` 12b), used for the Privacy Policy and Terms links
(`kmp-legal-consent`); it is a separate key so the API can move to its own host
without breaking those links.

`version.properties` is the **only** place the app version is written. Both
environments ship the same version; only the label differs (section 5b).
```properties
# config/version.properties
VERSION_NAME=1.1.1
VERSION_CODE=12
```

Add to `.gitignore`:
```gitignore
config/*/google-services.json
config/*/GoogleService-Info.plist
config/*/supabase.properties
**/GeneratedEnvironment.kt
**/GeneratedFirebaseConfig.kt
app/iosApp/src/GoogleService-Info.plist
app/androidApp/res/values/env_strings.xml
app/androidApp/res/values/crashlytics_mapping_id.xml
```

## 2. Firebase: one project per environment

- In the Firebase console, create **two projects**, e.g. "MyApp UAT" and
  "MyApp Prod". Separate projects keep analytics, crash reports, push and
  testers apart. (Auth users and data live in Supabase, section 2b.)
- In each project, register the Android app (package = `ANDROID_APPLICATION_ID`)
  and the iOS app (bundle id = `IOS_BUNDLE_ID`). Download its `google-services.json` and
  `GoogleService-Info.plist` into `config/<env>/`.
- Both projects may use the same package name. To install UAT and PROD side by
  side on one device, use a suffix (`com.touhidapps.kmp.uat`). That also needs the
  matching `applicationId` in `app/androidApp/module.yaml`. Ask the user before
  changing it.
- Firebase client API keys identify the project, not the user. Still keep the
  files out of git as the user asked, and restrict the keys in Google Cloud
  (Android package + SHA-1, iOS bundle id).

## 2b. Supabase: one project per environment

- In the Supabase dashboard, create **two projects**, "MyApp UAT" and "MyApp
  Prod" (the free plan allows exactly two active projects). Pick the region
  closest to the users, the same for both.
- Copy each project's URL and **publishable key** (Project Settings > API Keys,
  `sb_publishable_...`; older projects call it the anon key) into
  `config/<env>/supabase.properties`:
  ```properties
  # config/uat/supabase.properties  (git-ignored)
  SUPABASE_URL=https://<uat-ref>.supabase.co
  SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
  ```
- **Never** put the secret / service-role key here or anywhere in the app. It
  belongs only in edge function secrets (`kmp-supabase` section 10). The script
  refuses it.
- In each project, add the auth redirect `com.touhidapps.kmp://auth-callback`
  (or the UAT application id if it has a suffix), and enable the same auth
  providers. Google and Apple sign-in need their own client ids per project.
- Schema and functions reach both projects only through `supabase/migrations`
  and `supabase functions deploy`: UAT first, then PROD with the user's go-ahead.
- A free project pauses after 7 days without traffic. Restore UAT from the
  dashboard when it happens. Warn the user before PROD goes live on the free plan.

## 3. Committed model

```kotlin
// core/common/.../env/AppEnvironment.kt
enum class EnvName { UAT, PROD }

data class AppEnvironment(
    val name: EnvName,
    val baseUrl: String,
    val webUrl: String,                   // landing root for legal pages (kmp-legal-consent), ends with "/"
    val isDebug: Boolean,
    val versionName: String,              // "1.1.1", from config/version.properties
    val versionCode: Int,                 // 12, from config/version.properties; sent to app/status (kmp-update-maintenance-alerts)
    val supabaseUrl: String?,             // null when config/<env>/supabase.properties is missing
    val supabasePublishableKey: String?,
) {
    val isProduction: Boolean get() = name == EnvName.PROD
    /** Appended to every visible app title: " (UAT)" in UAT, "" in PROD. */
    val titleSuffix: String get() = if (isProduction) "" else " (${name.name})"
    /** Shown on the About page or drawer footer: "v1.1.1 (UAT)" in UAT, "v1.1.1" in PROD. */
    val versionLabel: String get() = "v$versionName$titleSuffix"
}
```

## 4. The switch script (generates code, Amper-friendly)

Amper compiles any `.kt` file under `src/`, so the script writes small
git-ignored Kotlin files from the selected environment's files.

```python
#!/usr/bin/env python3
"""Select the app environment.  Usage: python3 scripts/use_env.py uat|prod"""
import base64, json, pathlib, plistlib, re, shutil, sys, uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
PKG = "com.touhidapps"                                    # change to the project's base package
ENV_KT = ROOT / "core/common/src/com/touhidapps/common/env/GeneratedEnvironment.kt"
FIREBASE_KT = ROOT / "core/firebase/src/GeneratedFirebaseConfig.kt"   # match core/firebase's package dir
IOS_PLIST_TARGET = ROOT / "app/iosApp/src/GoogleService-Info.plist"
ANDROID_NAME_XML = ROOT / "app/androidApp/res/values/env_strings.xml"
IOS_INFO_PLIST = ROOT / "app/iosApp/src/Info.plist"
ANDROID_MODULE_YAML = ROOT / "app/androidApp/module.yaml"

def read_props(path):
    out = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            out[key.strip()] = value.strip()
    return out

def kt(value):
    if value is None:
        return "null"
    return '"' + str(value).replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'

def set_android_version(name, code):
    """Keep Amper's android versionName/versionCode in sync with version.properties."""
    text = ANDROID_MODULE_YAML.read_text()
    new = re.sub(r"(versionName:\s*)\S+", rf'\g<1>"{name}"', text)
    new = re.sub(r"(versionCode:\s*)\d+", rf"\g<1>{code}", new)
    if "versionName:" not in new:
        print(f"warning: add versionName/versionCode under settings.android in {ANDROID_MODULE_YAML}")
    elif new != text:
        ANDROID_MODULE_YAML.write_text(new)

def check_not_shared_firebase(env, project_id):
    """UAT and PROD must never point at the same Firebase project."""
    other = ROOT / "config" / ("prod" if env == "uat" else "uat")
    ids = set()
    if (other / "google-services.json").exists():
        ids.add(json.loads((other / "google-services.json").read_text())["project_info"]["project_id"])
    if (other / "GoogleService-Info.plist").exists():
        ids.add(plistlib.loads((other / "GoogleService-Info.plist").read_bytes())["PROJECT_ID"])
    if project_id in ids:
        sys.exit(f"Firebase project '{project_id}' is used by both UAT and PROD. Use separate projects.")

def check_supabase(env, props):
    """Publishable key only, and UAT and PROD on different Supabase projects."""
    url, key = props.get("SUPABASE_URL", ""), props.get("SUPABASE_PUBLISHABLE_KEY", "")
    if not url.startswith("https://"):
        sys.exit(f"SUPABASE_URL must be https (got {url})")
    if key.startswith("sb_secret_"):
        sys.exit("SUPABASE_PUBLISHABLE_KEY is a SECRET key. Use the publishable key; rotate the secret one if it was shared.")
    if key.count(".") == 2:                                 # legacy JWT key: read its role
        payload = key.split(".")[1]
        role = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4))).get("role")
        if role != "anon":
            sys.exit(f"SUPABASE_PUBLISHABLE_KEY has role '{role}'. Only the anon/publishable key may ship in the app.")
    other = ROOT / "config" / ("prod" if env == "uat" else "uat") / "supabase.properties"
    if other.exists() and read_props(other).get("SUPABASE_URL", "").rstrip("/") == url.rstrip("/"):
        sys.exit(f"Supabase project {url} is used by both UAT and PROD. Use separate projects.")

def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("uat", "prod"):
        sys.exit("usage: use_env.py uat|prod")
    env_dir = ROOT / "config" / sys.argv[1]
    props = read_props(env_dir / "env.properties")

    if props["ENV_NAME"].lower() != sys.argv[1]:
        sys.exit(f"{env_dir}/env.properties: ENV_NAME={props['ENV_NAME']} does not match '{sys.argv[1]}'")
    version = read_props(ROOT / "config/version.properties")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version["VERSION_NAME"]) or not version["VERSION_CODE"].isdigit():
        sys.exit("config/version.properties: VERSION_NAME must be like 1.1.1 and VERSION_CODE a whole number")
    base_url = props["BASE_URL"]
    if not base_url.startswith("https://") or not base_url.endswith("/"):
        sys.exit(f"BASE_URL must be https and end with '/' (got {base_url})")
    web_url = props.get("WEB_URL", base_url)
    if not web_url.startswith("https://") or not web_url.endswith("/"):
        sys.exit(f"WEB_URL must be https and end with '/' (got {web_url})")

    supa_path = env_dir / "supabase.properties"
    supa = read_props(supa_path) if supa_path.exists() else {}
    if supa:
        check_supabase(sys.argv[1], supa)
    else:
        print(f"warning: {supa_path} missing - Supabase disabled for this env")

    ENV_KT.parent.mkdir(parents=True, exist_ok=True)
    ENV_KT.write_text(f"""// GENERATED by scripts/use_env.py - do not edit, do not commit.
package {PKG}.common.env

object GeneratedEnvironment {{
    val current = AppEnvironment(
        name = EnvName.{props["ENV_NAME"]},
        baseUrl = {kt(props["BASE_URL"])},
        webUrl = {kt(web_url)},
        isDebug = {"true" if props.get("IS_DEBUG", "false").lower() == "true" else "false"},
        versionName = {kt(version["VERSION_NAME"])},
        versionCode = {int(version["VERSION_CODE"])},
        supabaseUrl = {kt(supa.get("SUPABASE_URL"))},
        supabasePublishableKey = {kt(supa.get("SUPABASE_PUBLISHABLE_KEY"))},
    )
}}
""")

    gs_path = env_dir / "google-services.json"
    if gs_path.exists():
        gs = json.loads(gs_path.read_text())
        app_id = props["ANDROID_APPLICATION_ID"]
        client = next(
            (c for c in gs["client"]
             if c["client_info"]["android_client_info"]["package_name"] == app_id),
            None,
        )
        if client is None:
            sys.exit(f"{gs_path}: no Android app with package {app_id}")
        info = gs["project_info"]
        check_not_shared_firebase(sys.argv[1], info["project_id"])
        FIREBASE_KT.write_text(f"""// GENERATED by scripts/use_env.py - do not edit, do not commit.
package {PKG}.kmp.core.firebase

val generatedFirebaseConfig: FirebaseConfig? = FirebaseConfig(
    applicationId = {kt(client["client_info"]["mobilesdk_app_id"])},
    apiKey = {kt(client["api_key"][0]["current_key"])},
    projectId = {kt(info["project_id"])},
    storageBucket = {kt(info.get("storage_bucket"))},
    gcmSenderId = {kt(info.get("project_number"))},
)
""")
    else:
        # never leave the other environment's config behind
        FIREBASE_KT.write_text(f"""// GENERATED by scripts/use_env.py - do not edit, do not commit.
package {PKG}.kmp.core.firebase

val generatedFirebaseConfig: FirebaseConfig? = null
""")
        print(f"warning: {gs_path} missing - Firebase disabled for this env")

    plist = env_dir / "GoogleService-Info.plist"
    if plist.exists():
        ios = plistlib.loads(plist.read_bytes())           # validate before copying
        if ios.get("BUNDLE_ID") != props["IOS_BUNDLE_ID"]:
            sys.exit(f"{plist}: BUNDLE_ID {ios.get('BUNDLE_ID')} != IOS_BUNDLE_ID {props['IOS_BUNDLE_ID']}")
        check_not_shared_firebase(sys.argv[1], ios["PROJECT_ID"])
        shutil.copyfile(plist, IOS_PLIST_TARGET)
    else:
        IOS_PLIST_TARGET.unlink(missing_ok=True)           # no stale plist from the other env

    # Launcher / home-screen name: "KMP Template (UAT)" in UAT, plain name in PROD
    display_name = props["APP_NAME"] + ("" if props["ENV_NAME"] == "PROD" else f" ({props['ENV_NAME']})")
    xml_name = display_name.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
    ANDROID_NAME_XML.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- GENERATED by scripts/use_env.py - do not edit, do not commit. -->\n"
        f'<resources>\n    <string name="app_name">{xml_name}</string>\n</resources>\n'
    )
    info = plistlib.loads(IOS_INFO_PLIST.read_bytes())
    wanted = {
        "CFBundleDisplayName": display_name,
        "CFBundleShortVersionString": version["VERSION_NAME"],
        "CFBundleVersion": version["VERSION_CODE"],
    }
    if any(info.get(k) != v for k, v in wanted.items()):
        info.update(wanted)
        IOS_INFO_PLIST.write_bytes(plistlib.dumps(info))
    set_android_version(version["VERSION_NAME"], version["VERSION_CODE"])

    # Crashlytics needs a mapping file id without its Gradle plugin (kmp-analytics-logging 8b).
    # Release builds replace it with `firebase crashlytics:mappingfile:generateid`.
    mapping_xml = ROOT / "app/androidApp/res/values/crashlytics_mapping_id.xml"
    if not mapping_xml.exists():
        mapping_xml.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- GENERATED by scripts/use_env.py (local placeholder) - do not commit. -->\n"
            f'<resources>\n    <string name="com.google.firebase.crashlytics.mapping_file_id" translatable="false">{uuid.uuid4().hex}</string>\n</resources>\n'
        )

    label = "v" + version["VERSION_NAME"] + ("" if props["ENV_NAME"] == "PROD" else f" ({props['ENV_NAME']})")
    print(f"Environment: {props['ENV_NAME']}  ->  {props['BASE_URL']}  ({display_name}, {label})")

if __name__ == "__main__":
    main()
```
Usage:
```bash
python3 scripts/use_env.py uat     # daily development and QA builds
python3 scripts/use_env.py prod    # release builds only
./kotlin build
```
If the generated files are missing, the build fails with an unresolved
`GeneratedEnvironment`. The fix is always "run the script". Say so in
`config/README.md`.

## 5. Wiring

**API (`app/shared`'s `appModule`)**
```kotlin
single<AppEnvironment> { GeneratedEnvironment.current }
single { NetworkConfig(baseUrl = get<AppEnvironment>().baseUrl, isDebug = get<AppEnvironment>().isDebug) }
single<HttpClient> { createHttpClient(get(), get()) }
single {
    val env = get<AppEnvironment>()
    SupabaseConfig(
        url = requireNotNull(env.supabaseUrl) { "Run scripts/use_env.py: supabase.properties missing" },
        publishableKey = requireNotNull(env.supabasePublishableKey),
        isDebug = env.isDebug,
        authScheme = "com.touhidapps.kmp",              // the deep link scheme registered in section 2b
    )
}
single { createAppSupabaseClient(get(), get(), get()) }  // kmp-supabase section 3
```
Delete `BaseApi.BASE_URL`. Nothing else may hold a base URL or a Supabase URL/key.

**Firebase on Android (`app/androidApp`)**
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        generatedFirebaseConfig?.let { androidInitializeFirebase(this, it) }   // UAT or PROD project
        startKoin { androidContext(this@MyApp); modules(androidKoinModule, appModule) }
    }
}
// androidKoinModule: bind the real implementation now that Firebase is configured
single<Analytics> { if (generatedFirebaseConfig != null) FirebaseAnalytics else NoOpAnalytics }
```

**Firebase on iOS:** the active `GoogleService-Info.plist` is copied into
`app/iosApp/src/`. The template's `core/firebase` is Android-only, because Amper
can't link the native Firebase iOS SDK. Until that is wired (SPM in the Xcode
project + `FirebaseApp.configure()` in `iosApp.swift`), iOS keeps `NoOpAnalytics`.
Tell the user this rather than pretending iOS Firebase works.

**App title shows "(UAT)"** so testers always know which build is installed:
- **Launcher (Android):** the script generates `res/values/env_strings.xml` with
  `app_name` = "KMP Template (UAT)" or "KMP Template". The manifest already uses
  `android:label="@string/app_name"`. **Remove `app_name` from
  `app/androidApp/res/values/strings.xml`**, or the build fails with a duplicate
  resource.
- **Home screen (iOS):** the script sets `CFBundleDisplayName` in
  `app/iosApp/src/Info.plist`. Commit `Info.plist` only with the PROD value, so
  run `use_env.py prod` before committing changes to that file.
- **In-app title:** the top bar title and the desktop window or web page title
  append `env.titleSuffix`:
  ```kotlin
  // AppScaffold / titleChangeByRoute
  Text(text = appTitle + env.titleSuffix)            // "Flower List (UAT)"
  // desktopApp main.kt
  Window(title = strings.appName + environment.titleSuffix, ...)
  ```
  Provide `AppEnvironment` to Compose once at the root (e.g. a
  `LocalAppEnvironment` in `core/ui`, set in `App.kt` from Koin) so `AppScaffold`
  can read it. `titleSuffix` is a technical label, so it isn't translated.
  PROD shows the plain name everywhere.

## 5b. App version label ("v1.1.1 (UAT)")

Every mobile build shows its version so testers and users can tell builds apart:

| Environment | Shown |
|---|---|
| UAT | `v1.1.1 (UAT)` |
| PROD | `v1.1.1` (no environment word means production) |

- **Where:** at the bottom of the navigation drawer if the app has one, otherwise
  on the About page (add it to Settings > About if there's no About page yet). If both exist,
  show it in both. Use `AppVersionText` from `core/ui` (`compose-multiplatform-ui`
  section 4.2), fed with `env.versionLabel`.
- **Source:** the number comes only from `config/version.properties`. The script
  writes it into `GeneratedEnvironment`, iOS `CFBundleShortVersionString` /
  `CFBundleVersion`, and Amper's `versionName` / `versionCode` in
  `app/androidApp/module.yaml`, so the label always matches what the store shows.
  Never hardcode a version string in a screen.
- `v` and `(UAT)` are technical labels, so they aren't translated. The digits stay
  ASCII even in the Bangla locale, so testers can read them out in bug reports.
- To release, bump `VERSION_NAME` and `VERSION_CODE` in one commit, then run the
  script.

**Optional extra marker:** in UAT you can also show a small "UAT" badge in `AppScaffold`
(e.g. a `labelSmall` chip in `tertiaryContainer` in the top bar). Hide it when
`env.isProduction`. Testers then always know which backend they are on. The
label text comes from `Strings`.

## 6. Rules

- Code reads `AppEnvironment` through DI only. No `if (BuildConfig.DEBUG)`-style
  URL switching, no hardcoded hosts in features, and `ApiUrls` holds paths only.
- Debug logging and dev tools follow `isDebug`. They are never on in PROD.
- UAT and PROD never share a Supabase project, a Firebase project, a database,
  storage buckets, edge function secrets or auth users.
- Never commit Firebase files, `supabase.properties`, keystores, tokens or real credentials. If one was
  committed by mistake, tell the user to rotate or restrict the key. Don't just
  delete the file.
- **Release safety:** before building a store release, run
  `python3 scripts/use_env.py prod` and verify:
  ```bash
  grep -q "EnvName.PROD" core/common/src/com/touhidapps/common/env/GeneratedEnvironment.kt \
    || { echo "Refusing to release: environment is not PROD"; exit 1; }
  ```
  Put the same check in CI before any upload step.
- **CI:** store each environment's `google-services.json`,
  `GoogleService-Info.plist` and `supabase.properties` as base64 secrets. Decode them into `config/<env>/`,
  then run the script. Never print them in logs.

## 7. Tests

- Unit-test the pieces that use the environment with a fake `AppEnvironment`
  (e.g. `NetworkConfig` built from it, the UAT badge visibility in `AppScaffold`
  content).
- In `kmp-networking-ktor` `MockEngine` tests, use `https://test.local`, never
  the real UAT or PROD hosts.
- Unit-test `versionLabel`: `"v1.1.1 (UAT)"` for UAT and `"v1.1.1"` for PROD.
- Add one `MockEngine` test with a path-based base URL (`https://test.local/uat/`)
  that asserts a call to `ApiUrls.PROFILE` hits `/uat/api/v1/profile`. It catches
  a leading `/` that would send UAT traffic to PROD.
- Take a screenshot of the "(UAT)" title (launcher and top bar) and of the version label as part of the feature screenshots
  (`kmp-feature-scaffold` section 12).

## 8. Checklist

- [ ] `config/uat` and `config/prod` each have `env.properties` (committed) plus their own `supabase.properties` and Firebase JSON/plist (git-ignored)
- [ ] Separate Supabase projects for UAT and PROD; `supabase.properties` git-ignored with the publishable key only; script refuses a secret key or a shared project; `SupabaseConfig` built only from `AppEnvironment`
- [ ] Separate Firebase projects for UAT and PROD, each with the Android app and the iOS app registered; script refuses a shared project id or a wrong bundle id
- [ ] UAT `BASE_URL` = `https://abc.com/uat/`, PROD = `https://abc.com/` (match `web-admin-landing` 12b), both ending in `/`; `ApiUrls` paths have no leading `/`
- [ ] `WEB_URL` set per env (defaults to `BASE_URL`), https with trailing `/`; legal links built only from `AppEnvironment.webUrl` (`kmp-legal-consent`)
- [ ] `scripts/use_env.py` generates `GeneratedEnvironment.kt` + `GeneratedFirebaseConfig.kt` and copies the plist; generated files ignored
- [ ] `HttpClient` base URL comes only from `AppEnvironment` → `NetworkConfig`; `BaseApi.BASE_URL` removed
- [ ] Android Firebase initialized from `generatedFirebaseConfig`; `FirebaseAnalytics` bound; iOS status stated honestly
- [ ] Launcher name (Android `app_name`, iOS `CFBundleDisplayName`), in-app top bar and desktop/web window titles show "(UAT)" in UAT and the plain name in PROD
- [ ] Version from `config/version.properties` only; drawer footer (or About page) shows `v1.1.1 (UAT)` in UAT and `v1.1.1` in PROD; iOS and Android store versions match it
- [ ] Release and CI guard refuses non-PROD builds; secrets come from CI, not git
- [ ] File report lists config, script, generated-file ignores and wiring changes (`kmp-feature-scaffold` section 13)
