---
name: app-store-connect-release
description: Upload PROD iOS builds (IPA) of a KMP + CMP app (KMP-MultiModule-Template / Amper) to App Store Connect — archive and export for the App Store, verify bundle id, version and App Store profile, upload to TestFlight for internal and external groups, then submit the version for App Review with release notes, manual or automatic release and phased release, using fastlane and an App Store Connect API key kept out of the repo. Use when releasing to the App Store, sending a build to TestFlight, or setting up iOS store uploads.
---

# App Store Connect Release (IPA, TestFlight, App Review)

iOS store releases go out with one script in two steps. `testflight` builds the
**PROD** app, checks the IPA, and uploads it to TestFlight. `submit` takes a
build that TestFlight already processed and sends that version to App Review:

```bash
python3 scripts/appstore_release.py testflight --dry-run            # build, check, Apple validates; nothing uploaded
python3 scripts/appstore_release.py testflight --confirm 12         # upload build 12 to TestFlight
python3 scripts/appstore_release.py testflight --groups "Public beta" --external --confirm 12
python3 scripts/appstore_release.py submit --dry-run                # show what would be submitted
python3 scripts/appstore_release.py submit --phased --confirm 12    # send 1.1.1 (12) to App Review
```

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
Builds use `xcodebuild` on the Xcode project the Kotlin Toolchain keeps for
`app/iosApp` (the same `scripts/build_ipa.sh` as `firebase-app-distribution`, with
App Store export options). Uploads and submission use **fastlane** (`pilot` and
`deliver`) with an App Store Connect API key. The release notes ("What's New"
and TestFlight's "What to Test") come from `release-notes/<VERSION_NAME>.txt` at
the repo root, the one release notes file per version that
`play-console-release` and `firebase-app-distribution` read too.

Related skills, read the first two before this one:
- `kmp-environments`: only PROD goes to a store; `scripts/use_env.py`; version
  only in `config/version.properties`.
- `firebase-app-distribution`: `build_ipa.sh`, CI signing with the API key
  (section 6). Ad hoc builds for testers come from there; this skill makes App
  Store builds, which can't be installed from Firebase.
- `store-assets-generator`: screenshots and listing text in
  `fastlane/screenshots/<locale>/` and `fastlane/metadata/<locale>/`.
- `play-console-release`: the Android counterpart. Both share
  `scripts/release_common.py`.
- `kmp-update-maintenance-alerts`: raise the minimum version only after the
  release is live for everybody.

## 0. Before you start, ask once

- Is the app already in App Store Connect, and was a version ever submitted? The
  first submission's app privacy, age rating and review details are filled in by
  the user (section 2).
- Apple Developer **team ID**, and whether an App Store Connect API key with the
  **App Manager** role exists.
- TestFlight groups: an internal group (team members) and, if wanted, an
  external group (beta users, needs Beta App Review).
- Release after approval: **manual** (default, the user presses "Release"),
  automatic, and whether to use **phased release** (7 days, 1% → 100%).
- Does the app use encryption beyond HTTPS and Apple's system frameworks? If not,
  the export compliance key is set to "no" (section 4).

Never ask for the `.p8` key, passwords or an Apple ID login in the chat.

## 1. How it fits together

```
config/version.properties ──┐
config/prod/env.properties ──┼─> use_env.py prod ──> build_ipa.sh (EXPORT_OPTIONS=appstore) ──> App.ipa
                             │                                                                  │
                             │        appstore_release.py testflight: verify PROD, bundle id, version, App Store profile
                             │                                                                  │
                             │                    fastlane pilot upload ──> TestFlight (processing, then groups)
                             │
release-notes/<VERSION_NAME>.txt ──> appstore_release.py submit ──> fastlane deliver ──> App Review
```

| Stage | Who gets it | Review | Limit |
|---|---|---|---|
| TestFlight internal group | App Store Connect users of the team | none | 100 testers |
| TestFlight external group | anyone invited by email or public link | Beta App Review for the first build of a version | 10,000 testers |
| App Store | everyone (phased: a growing share of automatic updates) | App Review, usually 1–2 days | |

A TestFlight build expires after 90 days. The build number (`VERSION_CODE`) must
be unique for a version, and the version (`VERSION_NAME`) must be higher than the
last one released on the App Store.

## 2. One-time setup (the user does this in the consoles)

1. **Bundle id**: Apple Developer → Certificates, Identifiers & Profiles →
   Identifiers: register `IOS_BUNDLE_ID` from `config/prod/env.properties`, with
   the capabilities the app uses. The UAT bundle id never goes to the store.
2. **The app**: App Store Connect → Apps → + → New App: iOS, name, primary
   language, the PROD bundle id, an SKU.
3. **App information** the API can't fill reliably, done by the user before the
   first submission:
   - App Privacy (data collection labels). They must match what
     `kmp-analytics-logging`, Supabase and any SDK collect.
   - Age rating, category, pricing and availability, content rights.
   - **Privacy Policy URL** (App Privacy, per language): the PROD page
     `https://abc.com/privacy` (`web-admin-landing` 6d), or `https://abc.com/bn/privacy`
     for the Bangla listing. Never a `/uat` URL. Apple also needs a **Support
     URL** per language (the landing contact page). The page must be published
     before submission; the placeholder template can't be published until it is
     filled in and reviewed by someone qualified, which is the user's call.
   - **License Agreement** (App Information): Apple's standard EULA applies
     unless the user wants their Terms & Conditions as a custom EULA. Ask. If the
     app sells **auto-renewable subscriptions**, App Review needs a working link
     to the Terms of Use and the privacy policy both in the app (the paywall and
     More, `kmp-legal-consent`) and in the listing: put the terms URL
     `https://abc.com/terms` in the description or set the custom EULA.
   - App Review Information: contact, and a **demo account** if the app needs a
     login. Invented data only.
4. **API key**: App Store Connect → Users and Access → Integrations → App Store
   Connect API → Team Keys → generate a key with role **App Manager** (Developer
   can upload but can't submit for review). Download the `.p8` once, keep it as
   `AuthKey_<KEY_ID>.p8` outside the repo, note the Key ID and Issuer ID
   (section 9).
5. **TestFlight**: create the internal group (e.g. "Internal QA") with
   **automatic distribution** on, so every processed build reaches it. For an
   external group, fill TestFlight → Test Information (beta description, feedback
   email, contact, demo account) once.
6. **Signing**: automatic signing with the API key (`-allowProvisioningUpdates`)
   creates the distribution certificate and App Store profile. A team that manages
   certificates by hand uses manual signing in `ExportOptions-appstore.plist`
   instead.

## 3. Files

```
config/
  version.properties                    # kmp-environments: VERSION_NAME, VERSION_CODE
  prod/env.properties                   # IOS_BUNDLE_ID
  ios/ExportOptions-distribution.plist  # firebase-app-distribution (ad hoc), unchanged
  ios/ExportOptions-appstore.plist      # section 5, committed
  ios/review_submission.json            # section 7, committed: answers fastlane sends with the submission
release-notes/
  <VERSION_NAME>.txt                    # committed: "What's New" (en-US) and "What to Test" of that version
fastlane/metadata/review_information/   # optional, store-assets-generator / user
scripts/
  use_env.py                            # kmp-environments, unchanged
  build_ipa.sh                          # section 5 (EXPORT_OPTIONS variable added)
  release_common.py                     # section 6, shared with play-console-release
  appstore_release.py                   # section 6
.github/workflows/release-appstore.yml  # section 10
```

`.gitignore` already covers `*.ipa`, `*.xcarchive`, `*.p8` and
`*.mobileprovision` from `firebase-app-distribution`. Add:
```gitignore
fastlane/report.xml
fastlane/README.md
asc_api_key.json
```

Install fastlane on the Mac that uploads (`brew install fastlane`, or a
`Gemfile` with `gem "fastlane"` and `bundle exec`). Use a recent version, since
App Store Connect changes often.

## 4. Info.plist and privacy manifest

Set these once in the app's `Info.plist` under `app/iosApp` (find it with
`find app/iosApp -name Info.plist -not -path "*/build/*"`):

```xml
<!-- Only if the app uses no encryption beyond HTTPS / Apple's frameworks (ask the user) -->
<key>ITSAppUsesNonExemptEncryption</key>
<false/>
```
Without it, every build waits in TestFlight on the "Missing Compliance"
question. Ktor's Darwin engine, Supabase over HTTPS and Keychain storage
(`kmp-security`) are exempt.

Add `PrivacyInfo.xcprivacy` to the iOS app when Apple's upload email lists
"ITMS-91053: Missing API declaration". Declare exactly the reasons the email
names (for example `NSPrivacyAccessedAPICategoryUserDefaults` with `CA92.1`
for settings storage). Third-party SDKs ship their own manifests.

Apple raises the minimum Xcode and iOS SDK for uploads every year. An old one is
rejected at upload (`ITMS-90725`). Build with the current Xcode release, also on
CI.

## 5. App Store build

`config/ios/ExportOptions-appstore.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key>
  <string>app-store-connect</string>  <!-- Xcode older than 15.3: app-store -->
  <key>destination</key>
  <string>export</string>             <!-- export the IPA so the script can check it before upload -->
  <key>teamID</key>
  <string>ABCDE12345</string>
  <key>signingStyle</key>
  <string>automatic</string>
  <key>uploadSymbols</key>
  <true/>                             <!-- crash reports in Xcode Organizer are symbolicated -->
  <key>manageAppVersionAndBuildNumber</key>
  <false/>                            <!-- the version comes from config/version.properties only -->
</dict>
</plist>
```

`scripts/build_ipa.sh` is the one from `firebase-app-distribution` section 6.2
with the export options as a variable. If it already exists, change only the
`EXPORT_OPTIONS` lines.

`scripts/build_ipa.sh`:

```bash
#!/usr/bin/env bash
# Archive app/iosApp and export an IPA.
# EXPORT_OPTIONS picks ad hoc (Firebase, default) or App Store (appstore_release.py).
set -euo pipefail
cd "$(dirname "$0")/.."

EXPORT_OPTIONS=${EXPORT_OPTIONS:-config/ios/ExportOptions-distribution.plist}
PROJECT=$(find app/iosApp -maxdepth 2 -name "*.xcodeproj" | head -n 1)
[ -n "$PROJECT" ] || { echo "No Xcode project under app/iosApp. Open the module once in the IDE to generate it."; exit 1; }
SCHEME=${IOS_SCHEME:-$(xcodebuild -list -project "$PROJECT" | awk '/Schemes:/{getline; print $1; exit}')}
OUT=build/ios
rm -rf "$OUT"

# App Store Connect API key lets CI sign without an Apple ID login.
AUTH=()
if [ -n "${ASC_KEY_PATH:-}" ]; then
  AUTH=(-allowProvisioningUpdates
        -authenticationKeyPath "$ASC_KEY_PATH"
        -authenticationKeyID "$ASC_KEY_ID"
        -authenticationKeyIssuerID "$ASC_ISSUER_ID")
fi

xcodebuild -project "$PROJECT" -scheme "$SCHEME" -configuration Release \
  -destination "generic/platform=iOS" -archivePath "$OUT/App.xcarchive" \
  ${AUTH[@]+"${AUTH[@]}"} archive

xcodebuild -exportArchive -archivePath "$OUT/App.xcarchive" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -exportPath "$OUT/ipa" ${AUTH[@]+"${AUTH[@]}"}

ls "$OUT"/ipa/*.ipa
```

If the toolchain version can't archive from the command line, archive in Xcode
(Product → Archive → Distribute App → App Store Connect → Export) and pass the
IPA with `--ipa`. All checks still run.

## 6. The scripts

### 6.1 `scripts/release_common.py`

The same file as `play-console-release` section 5. If it already exists, keep it.

`scripts/release_common.py`:

```python
"""Shared checks and release notes for store releases (play_release.py, appstore_release.py)."""
import contextlib, os, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ENV_KT = ROOT / "core/common/src/com/touhidapps/common/env/GeneratedEnvironment.kt"   # same path as use_env.py


def read_props(path):
    out = {}
    for line in pathlib.Path(path).read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            out[key.strip()] = value.strip()
    return out


def run(cmd, **kwargs):
    return subprocess.run(cmd, cwd=ROOT, check=True, text=True, **kwargs)


def git(*args):
    result = subprocess.run(["git", *args], cwd=ROOT, text=True, capture_output=True)
    return result.stdout.strip() if result.returncode == 0 else ""


def active_env():
    if not ENV_KT.exists():
        return None
    match = re.search(r"EnvName\.(UAT|PROD)", ENV_KT.read_text())
    return match.group(1).lower() if match else None


def prod_config():
    return read_props(ROOT / "config/prod/env.properties"), read_props(ROOT / "config/version.properties")


@contextlib.contextmanager
def prod_environment():
    """Select PROD for a store build, refuse a dirty tree, restore the previous env afterwards."""
    if git("status", "--porcelain"):
        sys.exit("Working tree has uncommitted changes. Store builds come from a committed state.")
    previous = active_env()
    run([sys.executable, "scripts/use_env.py", "prod"])
    try:
        if "EnvName.PROD" not in ENV_KT.read_text():
            sys.exit("Generated environment is not PROD. Refusing to release.")
        yield prod_config()
    finally:
        if previous and previous != "prod":
            run([sys.executable, "scripts/use_env.py", previous])


def secret_file(env_var, what):
    value = os.environ.get(env_var)
    if not value:
        sys.exit(f"Set {env_var} to the path of the {what}.")
    path = pathlib.Path(value).expanduser().resolve()
    if not path.is_file():
        sys.exit(f"{env_var} does not point to a file.")
    if ROOT in path.parents:
        sys.exit(f"The {what} is inside the repository. Move it out (e.g. ~/.config/store-release/).")
    return path


def newest_artifact(pattern, since, required=True):
    found = [p for p in (ROOT / "build").rglob(pattern) if p.stat().st_mtime >= since]
    if not found:
        if required:
            sys.exit(f"Build finished but no {pattern} newer than the build start was found under build/.")
        return None
    return max(found, key=lambda p: p.stat().st_mtime)


def check_confirm(args, version_code):
    """A store write needs --confirm <the version code it publishes>; --dry-run needs nothing."""
    if args.dry_run:
        return
    if args.confirm != str(version_code):
        sys.exit(f"This publishes version code {version_code} to a store. "
                 f"Run with --dry-run first, then re-run with --confirm {version_code}.")


def add_write_flags(parser):
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--dry-run", action="store_true", help="check everything, publish nothing")
    mode.add_argument("--confirm", metavar="VERSION_CODE", help="publish; must equal the version code")


RELEASE_NOTES = ROOT / "release-notes"
# release-notes/<VERSION_NAME>.txt is the default language; <VERSION_NAME>.<key>.txt adds one.
# Store locale per key; None means that store has no such locale (the App Store has no Bangla).
NOTES_LOCALES = {
    "": {"play": "en-US", "appstore": "en-US"},
    "bn": {"play": "bn-BD", "appstore": None},
}


def release_notes(version_name, store, limit):
    """{store locale: text} from release-notes/<VERSION_NAME>.txt and its <VERSION_NAME>.<key>.txt variants."""
    base = RELEASE_NOTES / f"{version_name}.txt"
    if not base.is_file():
        sys.exit(f"release-notes/{version_name}.txt is missing. Write what is new in {version_name}, "
                 "commit it, then run again.")
    files = {"": base}
    for path in sorted(RELEASE_NOTES.glob("*.txt")):
        match = re.fullmatch(re.escape(version_name) + r"\.([a-z]{2,3}(?:-[A-Za-z]{2,4})?)\.txt", path.name)
        if match:
            files[match.group(1)] = path
    notes = {}
    for key, path in files.items():
        if key not in NOTES_LOCALES:
            sys.exit(f"release-notes/{path.name}: no store locale for '{key}'. "
                     "Add it to NOTES_LOCALES in scripts/release_common.py.")
        locale = NOTES_LOCALES[key][store]
        if locale is None:
            print(f"note: release-notes/{path.name} skipped, {store} has no locale for '{key}'")
            continue
        text = path.read_text(encoding="utf-8").strip()
        if not text:
            sys.exit(f"release-notes/{path.name} is empty.")
        if len(text) > limit:
            sys.exit(f"release-notes/{path.name}: {len(text)} characters, {store} allows {limit}.")
        notes[locale] = text
    return notes
```

### 6.2 `scripts/appstore_release.py`

`scripts/appstore_release.py`:

```python
#!/usr/bin/env python3
"""Upload PROD iOS builds to TestFlight and submit them for App Review (fastlane + API key).

Usage:
  python3 scripts/appstore_release.py testflight [--ipa PATH] [--groups "A,B"] [--external]
      [--notes TEXT | --notes-file PATH] (--dry-run | --confirm CODE)     # notes default: release-notes/
  python3 scripts/appstore_release.py submit [--release manual|auto] [--phased] [--no-release-notes]
      [--with-listing] (--dry-run | --confirm CODE)

Credentials: ASC_KEY_PATH (AuthKey_<id>.p8, outside the repository), ASC_KEY_ID, ASC_ISSUER_ID.
"""
import argparse, contextlib, json, os, pathlib, plistlib, re, shutil, subprocess, sys, tempfile, time, zipfile

sys.dont_write_bytecode = True                  # no scripts/__pycache__ to dirty the git tree
from release_common import (NOTES_LOCALES, ROOT, add_write_flags, check_confirm, git, newest_artifact,
                            prod_config, prod_environment, release_notes, run, secret_file)

IOS_BUILD = ["bash", "scripts/build_ipa.sh"]
EXPORT_OPTIONS = "config/ios/ExportOptions-appstore.plist"
METADATA = ROOT / "fastlane/metadata"
SCREENSHOTS = ROOT / "fastlane/screenshots"
SUBMISSION_INFO = ROOT / "config/ios/review_submission.json"
TESTFLIGHT_NOTES_LIMIT = 4000
RELEASE_NOTES_LIMIT = 4000


def asc_key():
    p8 = secret_file("ASC_KEY_PATH", "App Store Connect API key (.p8)")
    key_id, issuer = os.environ.get("ASC_KEY_ID"), os.environ.get("ASC_ISSUER_ID")
    if not key_id or not issuer:
        sys.exit("Set ASC_KEY_ID and ASC_ISSUER_ID (App Store Connect → Users and Access → Integrations).")
    if p8.name != f"AuthKey_{key_id}.p8":
        sys.exit(f"Name the key file AuthKey_{key_id}.p8 (altool looks it up by that name).")
    return p8, key_id, issuer


@contextlib.contextmanager
def fastlane_key(p8, key_id, issuer):
    """fastlane reads the API key from a JSON file: write it to a private temp dir, delete it afterwards."""
    folder = pathlib.Path(tempfile.mkdtemp())       # mode 0700
    path = folder / "asc_api_key.json"
    path.write_text(json.dumps({"key_id": key_id, "issuer_id": issuer, "key": p8.read_text(), "in_house": False}))
    path.chmod(0o600)
    try:
        yield str(path)
    finally:
        shutil.rmtree(folder, ignore_errors=True)


def require_fastlane():
    if shutil.which("fastlane") is None:
        sys.exit("fastlane not found. Install it: brew install fastlane")


def embedded_profile(raw):
    """The provisioning profile is a signed plist; the XML sits between <?xml and </plist>."""
    start, end = raw.find(b"<?xml"), raw.find(b"</plist>")
    return plistlib.loads(raw[start:end + len(b"</plist>")]) if 0 <= start < end else {}


def check_ipa(ipa, props, version):
    with zipfile.ZipFile(ipa) as z:
        names = z.namelist()
        info_name = next((n for n in names if re.fullmatch(r"Payload/[^/]+\.app/Info\.plist", n)), None)
        if info_name is None:
            sys.exit(f"{ipa.name}: no Payload/*.app/Info.plist inside. Is this a real IPA?")
        info = plistlib.loads(z.read(info_name))
        profile_name = info_name.replace("Info.plist", "embedded.mobileprovision")
        profile = embedded_profile(z.read(profile_name)) if profile_name in names else {}
    expected = {"CFBundleIdentifier": props["IOS_BUNDLE_ID"],
                "CFBundleShortVersionString": version["VERSION_NAME"],
                "CFBundleVersion": version["VERSION_CODE"]}
    wrong = {k: info.get(k) for k, v in expected.items() if str(info.get(k)) != v}
    if wrong:
        sys.exit(f"{ipa.name} does not match config: {wrong}. Expected {expected}. Rebuild after use_env.py prod.")
    if not profile:
        sys.exit(f"{ipa.name} has no embedded provisioning profile. Export it with ExportOptions-appstore.plist.")
    if "ProvisionedDevices" in profile or profile.get("ProvisionsAllDevices"):
        sys.exit(f"{ipa.name} is an ad hoc or enterprise build ({profile.get('Name')}). "
                 "Export with config/ios/ExportOptions-appstore.plist.")
    if profile.get("Entitlements", {}).get("get-task-allow"):
        sys.exit(f"{ipa.name} is signed for development. Export with the App Store method.")
    if "ITSAppUsesNonExemptEncryption" not in info:
        print("warning: ITSAppUsesNonExemptEncryption missing in Info.plist; TestFlight will ask about export compliance")
    print(f"IPA OK: {expected['CFBundleIdentifier']} {expected['CFBundleShortVersionString']} "
          f"({expected['CFBundleVersion']}), profile '{profile.get('Name')}'")


def testflight_notes(args, version):
    """'What to Test': header line + given text, or release-notes/<VERSION_NAME>.txt."""
    header = f"v{version['VERSION_NAME']} · build {version['VERSION_CODE']} · {git('rev-parse', '--short', 'HEAD')}"
    if args.notes:
        body = args.notes
    elif args.notes_file:
        body = pathlib.Path(args.notes_file).read_text()
    else:
        limit = TESTFLIGHT_NOTES_LIMIT - len(header) - 2
        body = release_notes(version["VERSION_NAME"], "appstore", limit)[NOTES_LOCALES[""]["appstore"]]
    return f"{header}\n\n{(body or '- No changes listed.').strip()}"[:TESTFLIGHT_NOTES_LIMIT]


def cmd_testflight(args):
    with prod_environment() as (props, version):
        code = version["VERSION_CODE"]
        check_confirm(args, code)
        p8, key_id, issuer = asc_key()
        if args.ipa:
            ipa = (ROOT / args.ipa).resolve()
            if not ipa.is_file() or ipa.suffix != ".ipa":
                sys.exit(f"{ipa} is not an .ipa file")
        else:
            started = time.time()
            run(IOS_BUILD, env={**os.environ, "EXPORT_OPTIONS": EXPORT_OPTIONS})
            ipa = newest_artifact("*.ipa", started)
        check_ipa(ipa, props, version)
        notes = testflight_notes(args, version)
        groups = args.groups or ""
        print(f"{'Validating' if args.dry_run else 'Uploading'} {ipa.name} → TestFlight"
              f"{f', groups: {groups}' if groups else ''}{' (external, Beta App Review)' if args.external else ''}")

        if args.dry_run:                            # Apple checks the IPA without storing it
            run(["xcrun", "altool", "--validate-app", "-f", str(ipa), "-t", "ios",
                 "--apiKey", key_id, "--apiIssuer", issuer],
                env={**os.environ, "API_PRIVATE_KEYS_DIR": str(p8.parent)})
            print("Dry run: Apple validated the IPA; nothing was uploaded.\n\nWhat to Test:\n" + notes)
            return

        require_fastlane()
        with fastlane_key(p8, key_id, issuer) as key_json:
            cmd = ["fastlane", "pilot", "upload", "--api_key_path", key_json,
                   "--app_identifier", props["IOS_BUNDLE_ID"], "--ipa", str(ipa), "--changelog", notes,
                   "--app_platform", "ios"]
            if groups:
                cmd += ["--groups", groups]
            if args.external:
                cmd += ["--distribute_external", "true", "--notify_external_testers", "true"]
            run(cmd)                                # waits until App Store Connect has processed the build
    print("Uploaded and processed. Internal groups with automatic distribution have it now.")


def metadata_dir(with_listing, notes):
    """A temporary metadata folder for deliver: the listing only with_listing, plus
    <locale>/release_notes.txt written from release-notes/ (the only source of "What's New")."""
    folder = pathlib.Path(tempfile.mkdtemp())
    if with_listing and METADATA.is_dir():
        for source in sorted(p for p in METADATA.iterdir() if p.is_dir() and p.name != "android"):
            shutil.copytree(source, folder / source.name)
        for stale in folder.glob("*/release_notes.txt"):
            stale.unlink()
    for locale, text in notes.items():
        (folder / locale).mkdir(exist_ok=True)
        (folder / locale / "release_notes.txt").write_text(text + "\n", encoding="utf-8")
    for url_file in [*folder.glob("*/privacy_url.txt"), *folder.glob("*/support_url.txt")]:
        url = url_file.read_text().strip()
        if not url.startswith("https://") or "/uat/" in url:
            sys.exit(f"{url_file.parent.name}/{url_file.name} must be a PROD https URL (got {url!r}).")
    return folder


def cmd_submit(args):
    props, version = prod_config()
    code, name = version["VERSION_CODE"], version["VERSION_NAME"]
    check_confirm(args, code)
    p8, key_id, issuer = asc_key()
    require_fastlane()
    if not SUBMISSION_INFO.is_file():
        sys.exit(f"{SUBMISSION_INFO.relative_to(ROOT)} missing (section 7).")
    notes = {} if args.no_release_notes else release_notes(name, "appstore", RELEASE_NOTES_LIMIT)
    metadata = metadata_dir(args.with_listing, notes)
    try:
        submit_version(args, props, code, name, metadata, p8, key_id, issuer)
    finally:
        shutil.rmtree(metadata, ignore_errors=True)


def submit_version(args, props, code, name, metadata, p8, key_id, issuer):
    locales = sorted(p.name for p in metadata.iterdir() if (p / "release_notes.txt").is_file())
    if not locales and not args.no_release_notes:           # release_notes() already exits; a last guard
        sys.exit(f"No release notes for {name}. Write release-notes/{name}.txt, or pass --no-release-notes "
                 "(only for the first version, which has no 'What's New').")
    print(f"{'Would submit' if args.dry_run else 'Submitting'} {props['IOS_BUNDLE_ID']} {name} ({code}) for App Review: "
          f"release {args.release}{', phased over 7 days' if args.phased else ''}; "
          f"release notes: {', '.join(locales) or 'none'}; listing {'updated' if args.with_listing else 'untouched'}")

    with fastlane_key(p8, key_id, issuer) as key_json:
        if args.dry_run:                            # read-only: list the builds App Store Connect has
            run(["fastlane", "pilot", "builds", "--api_key_path", key_json,
                 "--app_identifier", props["IOS_BUNDLE_ID"]])
            print(f"Dry run: check that build {code} of {name} is in the list above and processed. Nothing was submitted.")
            return
        cmd = ["fastlane", "deliver", "--api_key_path", key_json, "--app_identifier", props["IOS_BUNDLE_ID"],
               "--platform", "ios", "--app_version", name, "--build_number", code,
               "--skip_binary_upload", "true", "--metadata_path", str(metadata),
               "--submit_for_review", "true", "--automatic_release", str(args.release == "auto").lower(),
               "--phased_release", str(args.phased).lower(),
               "--submission_information", json.dumps(json.loads(SUBMISSION_INFO.read_text())),
               "--run_precheck_before_submit", "false", "--force", "true"]
        if args.with_listing and SCREENSHOTS.is_dir():
            cmd += ["--screenshots_path", str(SCREENSHOTS), "--overwrite_screenshots", "true"]
        else:
            cmd += ["--skip_screenshots", "true"]
        run(cmd)
    print("Submitted. App Review usually answers within 1–2 days; App Store Connect emails the result.")


def main():
    parser = argparse.ArgumentParser(description="App Store Connect releases (PROD only)")
    sub = parser.add_subparsers(dest="command", required=True)

    tf = sub.add_parser("testflight", help="build, verify and upload to TestFlight")
    tf.add_argument("--ipa", help="skip the build and upload this IPA")
    tf.add_argument("--groups", help="comma-separated TestFlight group names")
    tf.add_argument("--external", action="store_true", help="--groups are external; submit for Beta App Review")
    notes = tf.add_mutually_exclusive_group()
    notes.add_argument("--notes", help="'What to Test' text")
    notes.add_argument("--notes-file")
    add_write_flags(tf)

    sm = sub.add_parser("submit", help="submit a processed build of VERSION_NAME for App Review")
    sm.add_argument("--release", choices=["manual", "auto"], default="manual",
                    help="manual: press Release in App Store Connect; auto: release when approved")
    sm.add_argument("--phased", action="store_true", help="phased release over 7 days")
    sm.add_argument("--no-release-notes", action="store_true", help="first version only")
    sm.add_argument("--with-listing", action="store_true",
                    help="also upload fastlane/metadata listing text and fastlane/screenshots")
    add_write_flags(sm)

    args = parser.parse_args()
    if getattr(args, "external", False) and not args.groups:
        sys.exit("--external needs --groups with the external group names.")
    {"testflight": cmd_testflight, "submit": cmd_submit}[args.command](args)


if __name__ == "__main__":
    main()
```

What the script refuses, and why:

| Refusal | Protects against |
|---|---|
| No `--dry-run` and no `--confirm <build number>` | uploading or submitting by accident |
| Generated environment isn't PROD, or the tree is dirty | a UAT build on TestFlight, or a build that can't be traced to a commit |
| IPA bundle id, version or build differs from config | a stale build or the UAT bundle id |
| Ad hoc, enterprise or development profile in the IPA | uploading the Firebase tester build, which Apple rejects late |
| `.p8` inside the repo or named differently from `AuthKey_<KEY_ID>.p8` | committing the key; altool not finding it |
| `release-notes/<VERSION_NAME>.txt` missing or empty (`testflight` without `--notes`, `submit`) | an update without "What's New" (allowed only for the first version, with `--no-release-notes`) |
| Release notes over 4000 characters | deliver failing after half the work |

`submit` never uploads a binary. It picks the build with `VERSION_CODE` that
TestFlight already processed. By default it uploads only the release notes,
written from `release-notes/` into a temporary metadata folder, so the listing
in App Store Connect isn't overwritten. A `release_notes.txt` left in
`fastlane/metadata/<locale>/` is ignored. With
`--with-listing` it also sends the `store-assets-generator` listing text and
screenshots; use that only when the user asked for a listing update in this
release.

The legal URLs travel with the listing text, one file per App Store locale:
`fastlane/metadata/<locale>/privacy_url.txt` (`https://abc.com/privacy`, or the
`/bn/privacy` page for Bangla) and `support_url.txt`. They are uploaded only
with `--with-listing`; on a first submission without it, the user sets them in
App Store Connect by hand. `metadata_dir` refuses a
`privacy_url.txt` or `support_url.txt` that contains `/uat/` or isn't `https`.

## 7. Submission answers: `config/ios/review_submission.json`

Committed. fastlane sends these with the review submission. Ask the user once
and write their answers, don't guess:

```json
{
  "add_id_info_uses_idfa": false,
  "content_rights_contains_third_party_content": false,
  "content_rights_has_rights": true
}
```
- `add_id_info_uses_idfa: true` only if the app or an SDK uses the advertising
  identifier (then App Tracking Transparency is needed too).
- Export compliance comes from `ITSAppUsesNonExemptEncryption` in Info.plist
  (section 4), not from this file.

## 8. Release notes and typical flow

Release notes live in one file per version at the repo root:
`release-notes/<VERSION_NAME>.txt` (e.g. `release-notes/1.1.1.txt`), named by
version name, not build number. It is the `en-US` "What's New". Written for
users, in plain words, no ticket numbers, the same rules as the listing. Max
4000 characters, but keep it under 500 so the same file also fits Google Play.

- A translation sits next to it (`release-notes/1.1.1.bn.txt`) and maps to a
  locale through `NOTES_LOCALES` in `release_common.py`. The App Store has no
  Bangla locale, so the `bn` file goes to Play only and `submit` says it skipped
  it. For an App Store locale such as `ar-SA`, add `"ar": {"play": "ar",
  "appstore": "ar-SA"}`.
- `testflight` (without `--notes`) and `submit` fail when the file is missing.
  The first version of an app has no "What's New": use `--no-release-notes`
  on `submit` then.
- TestFlight's "What to Test" is the same text under the line
  `v1.1.1 · build 12 · a1b2c3d`, like the Firebase notes. `--notes` replaces it
  for one upload.
- Lines are added while the version is developed: each feature or user-visible
  fix appends one line (`kmp-feature-scaffold` section 14). Before the upload,
  Claude shortens and orders them for users.

Flow:
1. Show the user the current `VERSION_NAME (VERSION_CODE)` and ask: new
   version or same version (section 11). Bump `VERSION_CODE` either way, and
   `VERSION_NAME` for a new version. Check `release-notes/<VERSION_NAME>.txt`
   is final, commit.
2. `testflight --dry-run`, show the user the summary and the "What to Test" text.
3. With the user's go-ahead: `testflight --confirm <code>`. It waits until the
   build is processed (usually 5–30 minutes). Internal testers get it
   automatically.
4. Optional external beta, on the user's word: upload with
   `--groups "Public beta" --external` from the start, or add the processed
   build to the external group in App Store Connect → TestFlight.
5. When testing passed and the user says to submit: `submit --dry-run`, then
   `submit --confirm <code>` with the release mode they chose.
6. After approval: with `--release manual`, the user presses "Release This
   Version" in App Store Connect. With `--phased`, automatic updates reach
   1%, 2%, 5%, 10%, 20%, 50%, 100% over 7 days. It can be paused for up to 30 days
   in App Store Connect, and anyone can still download it from the store.
7. Rejected: read the message in App Store Connect → App Review, fix, bump
   `VERSION_CODE`, upload a new build and submit again. Reply to the reviewer in
   App Store Connect when it's a misunderstanding, not a code issue.
8. When it is live for everyone: tag the commit (`git tag v1.1.1`), then raise
   the minimum version in the admin portal if needed
   (`kmp-update-maintenance-alerts`). There is no rollback on the App Store; a
   fix is a new version.

## 9. Credentials (never in the repo)

| Secret | Local place | CI secret |
|---|---|---|
| App Store Connect API key | `~/.config/store-release/AuthKey_<KEY_ID>.p8` | `ASC_KEY_P8_B64`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `production` environment only |
| Firebase / Supabase PROD config | `config/prod/` (git-ignored) | `*_PROD` secrets from `kmp-environments` |

- The key used for store releases has the **App Manager** role. It can be the
  same key `firebase-app-distribution` uses for CI signing, but a separate key
  for releases is easier to revoke.
- The script writes fastlane's key JSON into a private temp folder and deletes it
  afterwards. Never write the key into `fastlane/`, `Appfile` or env files.
- Never print a secret: no `cat` of decoded files, no `set -x` near them. Decode
  into `$RUNNER_TEMP`.
- A key that leaked: tell the user to revoke it in App Store Connect →
  Integrations and create a new one. Removing it from git isn't enough.
- No Apple ID password or app-specific password anywhere. The API key replaces
  them.

## 10. CI (GitHub Actions)

Manual only, in the GitHub environment `production` with required reviewers.
The person who starts the run types the build number as confirmation.

`.github/workflows/release-appstore.yml`:

```yaml
name: Release to App Store Connect
on:
  workflow_dispatch:
    inputs:
      command:
        description: "testflight or submit"
        type: choice
        options: [testflight, submit]
        default: testflight
      args:
        description: 'e.g. --groups "Internal QA" | --release manual --phased'
        required: false
      confirm:
        description: "Build number to publish (empty = dry run)"
        required: false

concurrency: release-appstore

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - run: ./kotlin test

  ios:
    needs: test
    runs-on: macos-latest          # check its Xcode is the current release, or select one with xcode-select
    environment: production
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - run: brew install fastlane
      - name: Secrets to files
        env:
          GSI: ${{ secrets.GOOGLE_SERVICE_INFO_PROD }}
          GS: ${{ secrets.GOOGLE_SERVICES_PROD }}
          P8: ${{ secrets.ASC_KEY_P8_B64 }}
          KEY_ID: ${{ secrets.ASC_KEY_ID }}
        run: |
          echo "$GSI" | base64 -d > config/prod/GoogleService-Info.plist
          echo "$GS" | base64 -d > config/prod/google-services.json   # use_env.py generates Android config too
          echo "$P8" | base64 -d > "$RUNNER_TEMP/AuthKey_$KEY_ID.p8"
      - name: Release
        env:
          ASC_KEY_ID: ${{ secrets.ASC_KEY_ID }}
          ASC_ISSUER_ID: ${{ secrets.ASC_ISSUER_ID }}
          COMMAND: ${{ inputs.command }}
          ARGS: ${{ inputs.args }}
          CONFIRM: ${{ inputs.confirm }}
        run: |
          export ASC_KEY_PATH="$RUNNER_TEMP/AuthKey_$ASC_KEY_ID.p8"     # build_ipa.sh signs with it too
          if [ -n "$CONFIRM" ]; then MODE="--confirm $CONFIRM"; else MODE="--dry-run"; fi
          eval "set -- $ARGS"                                           # keeps quoted group names together
          python3 scripts/appstore_release.py "$COMMAND" "$@" $MODE
```

`ARGS` comes from a person with access to the `production` environment, which
is why `eval` is acceptable here; don't copy that pattern into workflows that
take input from pull requests.

## 11. Rules for Claude

- Claude may build, run `--dry-run` and show the result on its own.
- **Before every upload, show the version and ask.** Read
  `config/version.properties` and say e.g. "Current version is 1.1.1 (12). New
  version (1.1.2), or same version 1.1.1 with a new build (13)?" App Store
  Connect never takes a build number twice for a version, so "same version"
  means the same `VERSION_NAME` with `VERSION_CODE` + 1; reusing build 12 is
  not offered. Same version only works while 1.1.1 isn't approved yet; after
  release Apple closes that version, so offer only a new one. A new version
  starts `release-notes/<new VERSION_NAME>.txt`; if the old version never
  reached a store, move its lines over. `submit` uses an uploaded build and
  needs no bump.
- `testflight --confirm` sends the build to Apple and to testers; `submit
  --confirm` sends the version to App Review and, with `--release auto`, to every
  user once approved. Run each only after the user, in this task, said so for
  **that version and step** ("upload 1.1.1 (12) to TestFlight", "submit 1.1.1 for
  review, manual release"). A TestFlight go-ahead isn't a submit go-ahead.
- Never press "Release", pause a phased release, reply to App Review or change
  app privacy, pricing or availability for the user. Tell them where it is.
- After a write, report: version, build, groups or release mode, commit, and the
  expected waiting time (processing 5–30 minutes, review 1–2 days).

## 12. Verification

- `testflight --dry-run` ends with "Apple validated the IPA". Check the printed
  bundle id, version, build, profile name and "What to Test".
- Refusals: no `--confirm`, wrong `--confirm`, a dirty tree, `--ipa` with the
  Firebase ad hoc IPA, `ASC_KEY_PATH` inside the repo, a release note over 4000
  characters.
- `submit --dry-run` lists the uploaded builds; the new build shows as processed.
- Before the first submission, check `https://abc.com/privacy` and
  `https://abc.com/terms` return `200` without a login and without
  `X-Robots-Tag: noindex` (the same `curl` loop as `play-console-release` 12),
  and that the App Privacy labels match the published policy.
- After the first TestFlight upload, install it from the TestFlight app and take
  a screenshot of the About page or drawer showing `v1.1.1` without "(UAT)"
  (`kmp-feature-scaffold` section 12).
- File report (`kmp-feature-scaffold` section 13): scripts, export options,
  `review_submission.json`, `Info.plist` keys, release notes, workflow,
  `.gitignore`.

## 13. Checklist

- [ ] PROD bundle id registered; app created in App Store Connect; privacy labels, age rating, review info and demo account filled by the user
- [ ] Privacy Policy URL (`https://abc.com/privacy`, per language) and Support URL set, page published and reviewed; `privacy_url.txt` / `support_url.txt` in `fastlane/metadata/<locale>/`; Terms linked in the listing when the app has subscriptions
- [ ] API key with App Manager role saved as `AuthKey_<KEY_ID>.p8` outside the repo
- [ ] `ITSAppUsesNonExemptEncryption` set after asking; privacy manifest added when Apple asks for it
- [ ] `ExportOptions-appstore.plist` (app-store-connect, export, uploadSymbols, no version management); `build_ipa.sh` takes `EXPORT_OPTIONS`
- [ ] `release_common.py` + `appstore_release.py` added; fastlane installed and recent
- [ ] Every upload: PROD env, clean tree, new build number, IPA verified (bundle id, version, App Store profile)
- [ ] `release-notes/<VERSION_NAME>.txt` committed (translations mapped in `NOTES_LOCALES`); no `release_notes.txt` left in `fastlane/metadata/`; `review_submission.json` answered by the user
- [ ] Current version shown and "new or same version" asked before each upload
- [ ] `--dry-run` shown to the user; `--confirm <code>` only after their go-ahead for that step
- [ ] Release mode (manual / auto, phased) chosen by the user
- [ ] CI: manual workflow, `production` environment with reviewers, tests gate the job, key decoded to `$RUNNER_TEMP`, current Xcode
- [ ] Minimum version raised only when the release is live for everyone
- [ ] File report lists every added or changed file
