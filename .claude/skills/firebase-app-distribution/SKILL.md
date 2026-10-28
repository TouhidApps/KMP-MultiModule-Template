---
name: firebase-app-distribution
description: Upload Android APK/AAB and iOS IPA test builds of a KMP + CMP app (KMP-MultiModule-Template / Amper) to Firebase App Distribution with the Firebase CLI, per environment (UAT and PROD Firebase projects), with tester groups, release notes labelled "v1.1.1 (UAT)", CI workflows and credentials kept out of the repo. Use when sending a build to testers, setting up App Distribution, adding tester groups, or automating test-build uploads.
---

# Firebase App Distribution (Android + iOS, UAT and PROD)

Testers get builds from Firebase App Distribution, not from files passed around in
chat. One command builds the app for an environment, checks that the binary really
is that environment and version, and uploads it with release notes to the right
tester groups:

```bash
python3 scripts/distribute.py uat android      # QA build of the UAT backend
python3 scripts/distribute.py uat ios
python3 scripts/distribute.py prod android --yes-prod   # release candidate on PROD data
```

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
Its build is Amper (the Kotlin Toolchain, `./kotlin`), which can't apply the
`com.google.firebase.appdistribution` Gradle plugin or the fastlane plugin's
Gradle hooks. So uploads go through the **Firebase CLI** (`firebase-tools`),
driven by a Python script next to `scripts/use_env.py`. This skill builds on
`kmp-environments`: read it first. Its rules stay in force (separate Firebase
project per environment, version only in `config/version.properties`, no
Firebase files in git).

The app does **not** need the App Distribution SDK, or even the Firebase SDK, to be
distributed. Uploading works for iOS today even though the template's
`core/firebase` is Android-only.

## 0. Before you start, ask once

- Which platforms go to testers: Android, iOS, or both?
- Who the testers are (QA team, client, internal staff), so groups can be named.
- Whether PROD builds should ever go to testers before the store (release
  candidates). The default is yes, to a small `release-candidates` group only.
- For iOS: the Apple Developer team ID, and whether the team has an
  App Store Connect API key for CI.

Don't ask for tester email addresses in the chat. They are added in the Firebase
console or from a local file (section 8).

## 1. How it fits together

```
config/version.properties ──┐
config/<env>/env.properties ─┼─> scripts/use_env.py <env> ──> ./kotlin package / xcodebuild
config/<env>/google-services.json, GoogleService-Info.plist      │
                                                                 ▼
                         scripts/distribute.py: verify env + version + bundle id
                                                                 │
                       firebase appdistribution:distribute ──> Firebase project of <env>
                                                                 │
                                    tester groups of that project (qa-team, uat-testers ...)
```

| | UAT | PROD |
|---|---|---|
| Firebase project | "MyApp UAT" | "MyApp Prod" |
| Firebase app id comes from | `config/uat/google-services.json` / plist | `config/prod/...` |
| Default groups (`FIREBASE_DIST_GROUPS`) | `qa-team,uat-testers` | `release-candidates` |
| Release-notes header | `v1.1.1 (UAT) · build 12 · a1b2c3d` | `v1.1.1 · build 12 · a1b2c3d` |
| Who uploads | any developer, CI on every merge to the UAT branch | CI only, after a manual approval |
| Extra guard | none | `--yes-prod`, clean git tree, release build only |

Groups and testers belong to a Firebase project, so UAT and PROD have **separate
groups**, even when the alias is the same. A UAT tester never receives a PROD build
by accident, and the other way round.

## 2. One-time Firebase setup (do it in both projects)

1. Firebase console → the project → **Release & Monitor → App Distribution** →
   Get started. Do this for the Android app and the iOS app.
2. **Groups:** create the groups listed in that environment's
   `FIREBASE_DIST_GROUPS`. Commands are in section 8.
3. **Service account for uploads** (one per project, never shared between UAT and
   PROD): Google Cloud console of that project → IAM → Service accounts → create
   `app-distribution-uploader`, give it only the role **Firebase App Distribution
   Admin** (`roles/firebaseappdistro.admin`), create a JSON key, download it outside
   the repo (section 9). `firebase login:ci` tokens are deprecated. Don't use them.
4. **Android AAB** (optional): App Distribution can only take an AAB when the
   Firebase project is linked to the app in Google Play Console. Testers then get a
   Play-signed build. A UAT project usually isn't linked, so **UAT always uploads
   APKs**. Use AAB for PROD only if the link exists. An APK upload has no such need.
5. **iOS:** App Distribution installs an **ad hoc** (Xcode: "Release Testing") or
   enterprise build. The tester's device UDID must be in the provisioning profile.
   When a new iOS tester accepts the invite, Firebase shows their device. Console →
   App Distribution → Testers & Groups → **Export Apple UDIDs**, add them in the
   Apple Developer portal → Devices, regenerate the ad hoc profile, rebuild. Tell
   the user this step is manual. Without it the new tester sees "unable to install".

## 3. Files

```
config/
  uat/env.properties              # + FIREBASE_DIST_GROUPS=qa-team,uat-testers
  prod/env.properties             # + FIREBASE_DIST_GROUPS=release-candidates
  ios/ExportOptions-distribution.plist   # committed (team id is not a secret)
scripts/
  use_env.py                      # from kmp-environments, unchanged
  distribute.py                   # section 7
  build_ipa.sh                    # section 6
.github/workflows/
  distribute-uat.yml              # section 10
  distribute-prod.yml
app/androidApp/keystore.properties   # NOT committed (section 5)
```

Add to `.gitignore` on top of the `kmp-environments` entries. The template already
ignores `*.apk`, `*.jks` and `*.keystore`:
```gitignore
*.aab
*.ipa
*.xcarchive
**/keystore.properties
*-sa.json
*.p8
*.mobileprovision
testers*.txt
```

Commit only group **aliases** in `env.properties`, never tester email addresses.
Emails are personal data.

## 4. env.properties additions

```properties
# config/uat/env.properties (add)
# Group aliases from the UAT Firebase project, comma-separated, no spaces
FIREBASE_DIST_GROUPS=qa-team,uat-testers
```
```properties
# config/prod/env.properties (add)
FIREBASE_DIST_GROUPS=release-candidates
```
`use_env.py` ignores keys it doesn't know, so it doesn't need a change.

## 5. Android build and signing (Amper)

Testers update a build in place, so **every distributed APK of an app must be
signed with the same key**. A debug key differs per machine and per CI runner, and
testers then get "App not installed" on update. Use a release keystore.

Turn on signing in `app/androidApp/module.yaml`:
```yaml
settings:
  android:
    namespace: com.touhidapps.kmp
    applicationId: com.touhidapps.kmp
    versionName: "1.1.1"        # written by use_env.py from config/version.properties
    versionCode: 12
    signing:
      enabled: true
      propertiesFile: ./keystore.properties   # git-ignored
```
```properties
# app/androidApp/keystore.properties (NOT committed)
storeFile=/home/me/.keystores/myapp-release.jks
storePassword=...
keyAlias=myapp
keyPassword=...
```

- Build: `./kotlin package -m androidApp` produces a signed release APK (and AAB)
  under `build/`. The script takes the newest `.apk` created by the build, so it
  doesn't depend on the exact output path. Toolchain versions differ in command
  names and flags. Check `./kotlin package --help` once and adjust
  `ANDROID_BUILD` at the top of `distribute.py`. Never guess a path in a doc.
- Keep the keystore outside the repo. Back it up somewhere safe (password
  manager). Losing it means every tester has to uninstall.
- UAT and PROD may share the keystore. With the same package name, testers can
  only have one of the two installed. For side-by-side installs, see
  `kmp-environments` section 2 (package suffix) and ask the user first.
- **Ask before each upload whether to bump** (section 11a). Usually
  `VERSION_CODE` goes up for every build that goes to testers: Android refuses a
  lower code, and two different builds with the same code look like one release
  in App Distribution. Re-uploading the same version and code is allowed when
  the user picks it (Android installs over an equal code). The `VERSION_NAME`
  only changes when the user decides.
- **Crashlytics mapping** (`kmp-analytics-logging` 8b): before the build, run
  `firebase crashlytics:mappingfile:generateid --resource-file=app/androidApp/res/values/crashlytics_mapping_id.xml`;
  after it, upload the R8 `mapping.txt` with
  `firebase crashlytics:mappingfile:upload <mapping.txt> --app=<active Android app id> --resource-file=<same xml>`.
  Do both inside `distribute.py` for every UAT and PROD build, so testers'
  crashes are readable.

## 6. iOS IPA

### 6.1 Export options (committed)

```xml
<!-- config/ios/ExportOptions-distribution.plist -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key>
  <string>release-testing</string>   <!-- Xcode older than 15.3: ad-hoc -->
  <key>teamID</key>
  <string>ABCDE12345</string>
  <key>signingStyle</key>
  <string>automatic</string>
  <key>compileBitcode</key>
  <false/>
  <key>thinning</key>
  <string>&lt;none&gt;</string>
</dict>
</plist>
```
Never use `app-store` / `app-store-connect` here. Such an IPA can't be installed
from App Distribution.

### 6.2 Build script

The Kotlin Toolchain keeps an Xcode project for `app/iosApp` that calls the
toolchain from a build phase. The script finds it instead of hardcoding its name.
If your toolchain version can't archive from the command line, archive in Xcode
(Product → Archive → Distribute App → Release Testing), then upload with
`distribute.py uat ios --artifact path/to/App.ipa`. All the checks still run.

```bash
#!/usr/bin/env bash
# Archive app/iosApp and export an IPA for Firebase App Distribution.
# Run through scripts/distribute.py, which selects the environment first.
set -euo pipefail
cd "$(dirname "$0")/.."

PROJECT=$(find app/iosApp -maxdepth 2 -name "*.xcodeproj" | head -n 1)
[ -n "$PROJECT" ] || { echo "No Xcode project under app/iosApp. Open the module once in the IDE to generate it."; exit 1; }
SCHEME=${IOS_SCHEME:-$(xcodebuild -list -project "$PROJECT" | awk '/Schemes:/{getline; print $1; exit}')}
OUT=build/ios
rm -rf "$OUT"

# App Store Connect API key lets CI sign without an Apple ID login (section 6.3).
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
  -exportOptionsPlist config/ios/ExportOptions-distribution.plist \
  -exportPath "$OUT/ipa" ${AUTH[@]+"${AUTH[@]}"}

ls "$OUT"/ipa/*.ipa
```

### 6.3 Signing in CI

On a macOS runner there is no logged-in Apple ID. Use an **App Store Connect API
key** (`.p8`, role App Manager or Developer) with `-allowProvisioningUpdates`, as
`build_ipa.sh` does when `ASC_KEY_PATH` is set. Then Xcode fetches the certificate
and the ad hoc profile itself. The `.p8` is a secret (section 9).

## 7. The upload script

The script switches to the requested environment, builds unless `--artifact` is
given, refuses a binary whose package/bundle id or version doesn't match the
config, writes release notes, uploads, then **switches back** to the environment
that was active before. A developer who uploads PROD isn't left on PROD.

```python
#!/usr/bin/env python3
"""Build and upload a test build to Firebase App Distribution.

Usage:
  python3 scripts/distribute.py uat|prod android|ios [--artifact PATH]
      [--notes TEXT | --notes-file PATH] [--groups a,b] [--yes-prod] [--dry-run]

Notes: --notes, --notes-file, else release-notes/<VERSION_NAME>.txt, else commit subjects since the last tag.
"""
import argparse, json, os, pathlib, plistlib, re, shutil, subprocess, sys, tempfile, time, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
ENV_KT = ROOT / "core/common/src/com/touhidapps/common/env/GeneratedEnvironment.kt"   # same path as use_env.py
# Check `./kotlin package --help` for your toolchain version and adjust these two commands.
ANDROID_BUILD = ["./kotlin", "package", "-m", "androidApp"]
IOS_BUILD = ["bash", "scripts/build_ipa.sh"]
NOTES_LIMIT = 15000          # Firebase allows ~16k characters; keep a margin
RELEASE_NOTES = ROOT / "release-notes"   # one file per version, shared with the store release scripts


def read_props(path):
    out = {}
    for line in path.read_text().splitlines():
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
    """The environment that was active before this script, so it can be restored."""
    if not ENV_KT.exists():
        return None
    match = re.search(r"EnvName\.(UAT|PROD)", ENV_KT.read_text())
    return match.group(1).lower() if match else None


def check_credentials(dry_run):
    if shutil.which("firebase") is None and not dry_run:
        sys.exit("Firebase CLI not found. Install it: npm install -g firebase-tools")
    creds = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    if not creds:
        if dry_run:
            return
        sys.exit("Set GOOGLE_APPLICATION_CREDENTIALS to the service account JSON for this environment.")
    path = pathlib.Path(creds).resolve()
    if not path.is_file():
        sys.exit("GOOGLE_APPLICATION_CREDENTIALS does not point to a file.")
    if ROOT in path.parents:
        sys.exit("The service account JSON is inside the repository. Move it out (e.g. ~/.config/firebase-dist/).")


def firebase_app_id(env, platform, props):
    env_dir = ROOT / "config" / env
    if platform == "android":
        path = env_dir / "google-services.json"
        if not path.exists():
            sys.exit(f"{path} missing. Download it from the {env.upper()} Firebase project.")
        gs = json.loads(path.read_text())
        for client in gs["client"]:
            info = client["client_info"]
            if info["android_client_info"]["package_name"] == props["ANDROID_APPLICATION_ID"]:
                return info["mobilesdk_app_id"], gs["project_info"]["project_id"]
        sys.exit(f"{path}: no Android app with package {props['ANDROID_APPLICATION_ID']}")
    path = env_dir / "GoogleService-Info.plist"
    if not path.exists():
        sys.exit(f"{path} missing. Download it from the {env.upper()} Firebase project.")
    ios = plistlib.loads(path.read_bytes())
    return ios["GOOGLE_APP_ID"], ios["PROJECT_ID"]


def newest_artifact(suffixes, since):
    found = [p for s in suffixes for p in (ROOT / "build").rglob(f"*{s}")
             if p.stat().st_mtime >= since]
    if not found:
        sys.exit(f"Build finished but no {'/'.join(suffixes)} newer than the build start was found under build/.")
    return max(found, key=lambda p: p.stat().st_mtime)


def check_ipa(ipa, props, version):
    with zipfile.ZipFile(ipa) as z:
        name = next((n for n in z.namelist() if re.fullmatch(r"Payload/[^/]+\.app/Info\.plist", n)), None)
        if name is None:
            sys.exit(f"{ipa}: no Payload/*.app/Info.plist inside. Is this a real IPA?")
        info = plistlib.loads(z.read(name))
    expected = {
        "CFBundleIdentifier": props["IOS_BUNDLE_ID"],
        "CFBundleShortVersionString": version["VERSION_NAME"],
        "CFBundleVersion": version["VERSION_CODE"],
    }
    wrong = {k: info.get(k) for k, v in expected.items() if str(info.get(k)) != v}
    if wrong:
        sys.exit(f"{ipa} does not match config: {wrong}. Expected {expected}. Rebuild after use_env.py.")


def check_apk(apk, props, version):
    """Uses aapt2 from the Android SDK when available; warns otherwise."""
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    tools = sorted((pathlib.Path(sdk) / "build-tools").glob("*/aapt2")) if sdk else []
    if not tools:
        print("warning: aapt2 not found (set ANDROID_HOME); APK version not verified")
        return
    badging = subprocess.run([str(tools[-1]), "dump", "badging", str(apk)],
                             capture_output=True, text=True).stdout
    expected = (f"name='{props['ANDROID_APPLICATION_ID']}' versionCode='{version['VERSION_CODE']}' "
                f"versionName='{version['VERSION_NAME']}'")
    if expected not in badging:
        sys.exit(f"{apk} does not match config ({expected}). Rebuild after use_env.py.")
    if "application-debuggable" in badging and props["ENV_NAME"] == "PROD":
        sys.exit(f"{apk} is a debuggable build. PROD testers must get a release build.")


def release_notes(label, args):
    sha = git("rev-parse", "--short", "HEAD") or "no-git"
    header = f"{label} · build {args.version_code} · {sha}"
    version_notes = RELEASE_NOTES / f"{args.version_name}.txt"
    if args.notes:
        body = args.notes
    elif args.notes_file:
        body = pathlib.Path(args.notes_file).read_text()
    elif version_notes.is_file() and version_notes.read_text(encoding="utf-8").strip():
        body = version_notes.read_text(encoding="utf-8")
    else:
        print(f"note: no release-notes/{args.version_name}.txt; using commit subjects since the last tag")
        last_tag = git("describe", "--tags", "--abbrev=0")
        log_range = f"{last_tag}..HEAD" if last_tag else "HEAD"
        lines = git("log", "--no-merges", "--pretty=- %s", log_range, "-n", "30").splitlines()
        body = "\n".join(lines) or "- No changes listed."
    notes = f"{header}\n\n{body.strip()}\n"
    return notes[:NOTES_LIMIT]


def main():
    parser = argparse.ArgumentParser(description="Upload a build to Firebase App Distribution")
    parser.add_argument("env", choices=["uat", "prod"])
    parser.add_argument("platform", choices=["android", "ios"])
    parser.add_argument("--artifact", help="skip the build and upload this APK/AAB/IPA")
    notes = parser.add_mutually_exclusive_group()
    notes.add_argument("--notes", help="release notes text")
    notes.add_argument("--notes-file", help="file with release notes")
    parser.add_argument("--groups", help="comma-separated group aliases (default: FIREBASE_DIST_GROUPS)")
    parser.add_argument("--yes-prod", action="store_true", help="required to distribute a PROD build")
    parser.add_argument("--dry-run", action="store_true", help="print the upload command, do not upload")
    args = parser.parse_args()

    if args.env == "prod" and not args.yes_prod:
        sys.exit("PROD builds reach PROD data. Re-run with --yes-prod if that is intended.")
    check_credentials(args.dry_run)

    previous_env = active_env()
    try:
        run([sys.executable, "scripts/use_env.py", args.env])
        if f"EnvName.{args.env.upper()}" not in ENV_KT.read_text():
            sys.exit(f"Generated environment is not {args.env.upper()}. Refusing to upload.")
        if args.env == "prod" and git("status", "--porcelain"):
            sys.exit("Working tree has uncommitted changes. Commit them before a PROD distribution.")

        props = read_props(ROOT / "config" / args.env / "env.properties")
        version = read_props(ROOT / "config/version.properties")
        args.version_code, args.version_name = version["VERSION_CODE"], version["VERSION_NAME"]
        suffix = "" if props["ENV_NAME"] == "PROD" else f" ({props['ENV_NAME']})"
        label = f"v{version['VERSION_NAME']}{suffix}"
        app_id, project_id = firebase_app_id(args.env, args.platform, props)

        if args.artifact:
            artifact = pathlib.Path(args.artifact).resolve()
            if not artifact.is_file():
                sys.exit(f"{artifact} not found")
        else:
            started = time.time()
            run(ANDROID_BUILD if args.platform == "android" else IOS_BUILD)
            artifact = newest_artifact([".apk"] if args.platform == "android" else [".ipa"], started)

        allowed = {".apk", ".aab"} if args.platform == "android" else {".ipa"}
        if artifact.suffix not in allowed:
            sys.exit(f"{artifact.name} is not an {args.platform} build (expected {'/'.join(sorted(allowed))})")
        if artifact.suffix == ".ipa":
            check_ipa(artifact, props, version)
        elif artifact.suffix == ".apk":
            check_apk(artifact, props, version)

        groups = args.groups or props.get("FIREBASE_DIST_GROUPS", "")
        if not groups:
            sys.exit(f"No tester groups. Set FIREBASE_DIST_GROUPS in config/{args.env}/env.properties or pass --groups.")

        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
            f.write(release_notes(label, args))
            notes_path = f.name
        cmd = ["firebase", "appdistribution:distribute", str(artifact),
               "--app", app_id, "--release-notes-file", notes_path, "--groups", groups]
        print(f"Uploading {label} build {version['VERSION_CODE']} ({artifact.name}) "
              f"to Firebase project {project_id}, groups: {groups}")
        try:
            if args.dry_run:
                print("dry run:", " ".join(cmd))
                print(pathlib.Path(notes_path).read_text())
            else:
                run(cmd)
        finally:
            os.unlink(notes_path)
    finally:
        if previous_env and previous_env != args.env:
            run([sys.executable, "scripts/use_env.py", previous_env])


if __name__ == "__main__":
    main()
```

Usage:
```bash
export GOOGLE_APPLICATION_CREDENTIALS=~/.config/firebase-dist/uat-sa.json
python3 scripts/distribute.py uat android
python3 scripts/distribute.py uat ios --notes-file notes.md
python3 scripts/distribute.py uat android --groups qa-team          # one group only
python3 scripts/distribute.py uat ios --artifact ~/Desktop/App.ipa  # built in Xcode
python3 scripts/distribute.py uat android --dry-run                 # see what would upload

export GOOGLE_APPLICATION_CREDENTIALS=~/.config/firebase-dist/prod-sa.json
python3 scripts/distribute.py prod android --yes-prod --notes "Release candidate 1 for 1.1.1"
```

What the script refuses, and why:

| Refusal | Protects against |
|---|---|
| PROD without `--yes-prod` | a release candidate on real user data sent by mistake |
| Generated environment isn't the requested one | a UAT binary uploaded to PROD testers, or the reverse |
| IPA bundle id or version differs from config | a stale or wrong build (checked by reading `Info.plist` inside the IPA) |
| APK package or version differs (needs `aapt2` from `ANDROID_HOME`) | the same, for Android. Without `aapt2` it only warns |
| Debuggable APK for PROD | a debug build reaching release-candidate testers |
| Dirty git tree for PROD | a build that can't be traced back to a commit |
| Service account JSON inside the repo | committing the upload key |
| No tester groups | an upload that nobody receives |

Keep the paths at the top in sync with `use_env.py` (`ENV_KT` uses the project's
package directory).

Before every upload Claude shows the current version and asks (section 11a).

## 8. Tester groups

Create and fill groups with the CLI, once per Firebase project. Group aliases are
lowercase with dashes. The display name is free text.

```bash
P=myapp-uat                                   # Firebase project id of UAT
firebase appdistribution:group:create "QA team" qa-team --project $P
firebase appdistribution:group:create "UAT testers" uat-testers --project $P
# testers.txt: one email per line, kept outside git (testers*.txt is ignored)
firebase appdistribution:testers:add --file testers.txt --group-alias qa-team --project $P
firebase appdistribution:testers:remove someone@example.com --project $P
```

Suggested groups:

| Group alias | Project | Who |
|---|---|---|
| `qa-team` | UAT | QA engineers. Get every UAT build |
| `uat-testers` | UAT | client or business testers. Same builds, or only tagged ones |
| `developers` | UAT | the team, for quick checks with `--groups developers` |
| `release-candidates` | PROD | a few trusted people who sign off before the store |

Remove testers who leave the project. Their invite link stays valid otherwise.

## 9. Credentials (never in the repo)

| Secret | Local place | CI secret |
|---|---|---|
| UAT upload service account | `~/.config/firebase-dist/uat-sa.json` | `FIREBASE_SA_UAT` (base64) |
| PROD upload service account | `~/.config/firebase-dist/prod-sa.json` | `FIREBASE_SA_PROD` (base64), PROD environment only |
| Android keystore + passwords | `~/.keystores/`, `keystore.properties` | `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PROPERTIES` |
| App Store Connect API key | `~/.appstoreconnect/AuthKey_XXXX.p8` | `ASC_KEY_P8_B64`, `ASC_KEY_ID`, `ASC_ISSUER_ID` |
| Firebase config files | `config/<env>/` (git-ignored) | `GOOGLE_SERVICES_UAT`, `GOOGLE_SERVICE_INFO_UAT`, … (base64, from `kmp-environments`) |

Rules:
- The upload key of one environment never works on the other project: one service
  account per project, with the App Distribution Admin role only.
- Never print a secret: no `cat` of a decoded file, no `set -x` in steps that
  touch them. Write decoded files to `$RUNNER_TEMP`, not into the checkout.
- If a key was committed or pasted anywhere, tell the user to **delete the key in
  Google Cloud and create a new one** (or revoke the `.p8`). Deleting the file from
  git isn't enough.
- Don't put Firebase or signing secrets in `env.properties`, `module.yaml`,
  release notes or code.

## 10. CI (GitHub Actions)

UAT uploads on every push to the `uat` branch (or by hand). PROD only by hand, in
a GitHub **environment** named `production` with required reviewers, so a person
approves each release-candidate upload. Pin actions to a major version you have
checked.

```yaml
# .github/workflows/distribute-uat.yml
name: Distribute UAT
on:
  push:
    branches: [uat]
  workflow_dispatch:

concurrency: distribute-uat

jobs:
  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }                 # release notes need tags and history
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - run: npm install -g firebase-tools
      - name: Secrets to files
        env:
          GS: ${{ secrets.GOOGLE_SERVICES_UAT }}
          SA: ${{ secrets.FIREBASE_SA_UAT }}
          KS: ${{ secrets.ANDROID_KEYSTORE_B64 }}
          KP: ${{ secrets.ANDROID_KEYSTORE_PROPERTIES }}
        run: |
          echo "$GS" | base64 -d > config/uat/google-services.json
          echo "$SA" | base64 -d > "$RUNNER_TEMP/sa.json"
          echo "$KS" | base64 -d > "$RUNNER_TEMP/release.jks"
          printf '%s\n' "$KP" | sed "s|^storeFile=.*|storeFile=$RUNNER_TEMP/release.jks|" \
            > app/androidApp/keystore.properties
      - name: Build and upload
        env:
          GOOGLE_APPLICATION_CREDENTIALS: ${{ runner.temp }}/sa.json
        run: python3 scripts/distribute.py uat android

  ios:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - run: npm install -g firebase-tools
      - name: Secrets to files
        env:
          GSI: ${{ secrets.GOOGLE_SERVICE_INFO_UAT }}
          GS: ${{ secrets.GOOGLE_SERVICES_UAT }}
          SA: ${{ secrets.FIREBASE_SA_UAT }}
          P8: ${{ secrets.ASC_KEY_P8_B64 }}
        run: |
          echo "$GSI" | base64 -d > config/uat/GoogleService-Info.plist
          echo "$GS" | base64 -d > config/uat/google-services.json   # use_env.py generates Android config too
          echo "$SA" | base64 -d > "$RUNNER_TEMP/sa.json"
          echo "$P8" | base64 -d > "$RUNNER_TEMP/AuthKey.p8"
      - name: Build and upload
        env:
          GOOGLE_APPLICATION_CREDENTIALS: ${{ runner.temp }}/sa.json
          ASC_KEY_PATH: ${{ runner.temp }}/AuthKey.p8
          ASC_KEY_ID: ${{ secrets.ASC_KEY_ID }}
          ASC_ISSUER_ID: ${{ secrets.ASC_ISSUER_ID }}
        run: python3 scripts/distribute.py uat ios
```

`distribute-prod.yml` is the same with `on: workflow_dispatch` only,
`environment: production` on both jobs, the `*_PROD` secrets, `config/prod/`, and
`python3 scripts/distribute.py prod <platform> --yes-prod`. Put the release-safety
grep from `kmp-environments` section 6 before the upload step as well.

Run unit tests (`./kotlin test`) in a job the distribute jobs `needs:`, so a red
build never reaches testers.

## 11. Release notes

Testers read release notes on their phone, so they say what to test, in plain
words:

- The first line is always written by the script: version label, build number and
  short commit, e.g. `v1.1.1 (UAT) · build 12 · a1b2c3d`. Testers quote it in bug
  reports, and it matches the label in the app's drawer or About page
  (`kmp-environments` section 5b).
- The body is `release-notes/<VERSION_NAME>.txt` at the repo root (e.g.
  `release-notes/1.1.1.txt`), the same file Google Play and the App Store use
  for "What's new". Each feature or fix appends a line to it while the version
  is developed (`kmp-feature-scaffold` section 14), so testers see what changed
  in this version. Only the default-language file is used; `1.1.1.bn.txt` is for
  the stores.
- `--notes` or `--notes-file` replace it for one upload ("Retest the login fix
  only").
- Without that file, the body falls back to the commit subjects since the last
  git tag (up to 30), and the script says so. Good commit subjects make good
  notes (`kotlin-readable-code`). Store uploads have no fallback: they fail
  without the file.
- For a client or business group, write the notes by hand: what's new, what to
  test, known issues. No internal ticket links that testers can't open, no
  secrets, no customer data.
- Tag each PROD release-candidate commit (`v1.1.1-rc1`) so the next notes start
  from it.

### 11a. New version or same version? (ask before every upload)

Before running `distribute.py` (not for `--dry-run`), Claude reads
`config/version.properties` and asks, e.g.:

> Current version is **1.1.1 (12)**. Upload as:
> 1. same version and build, 1.1.1 (12): replaces what testers have, no version change
> 2. same version, new build, 1.1.1 (13): recommended, testers can tell the builds apart
> 3. new version, 1.1.2 (13): starts `release-notes/1.1.2.txt`

- Firebase App Distribution accepts the same version and build again; it shows
  up as a new release with the same label (an identical binary updates the
  existing release instead). Offer option 1 only here: Google Play and the App
  Store never take a version code twice, so a build that is meant to go to a
  store later needs a fresh `VERSION_CODE`.
- Option 1 changes no file. Options 2 and 3 edit `config/version.properties`
  (and for PROD, commit it, since the tree must be clean).
- For a new version, if `release-notes/<old>.txt` was never sent to a store, ask
  whether its lines move to the new file.
- In CI there is nobody to ask: the workflow uploads whatever version is
  committed.

## 12. Optional: in-app "new build available" for testers

The App Distribution Android SDK (`firebase-appdistribution`) can prompt testers
to update inside the app. Don't add it by default:
- It is Android-only, it needs `core/firebase` wiring, and it must **never** be in
  a build that goes to Google Play (Play policy). With Amper that means a
  separate dependency set per environment, which the template doesn't have.
- The email from App Distribution and the Firebase App Tester app already notify
  testers.
Add it only if the user asks, behind UAT only, and say why the PROD build doesn't
have it. Store updates for real users are handled by
`kmp-update-maintenance-alerts`.

## 13. Tests and verification

- Run `python3 scripts/distribute.py uat android --dry-run` and
  `... uat ios --dry-run` after setting things up. Check the Firebase project id,
  app id, groups and the release-notes header in the output.
- Check that the refusals work: PROD without `--yes-prod`, an IPA from an older
  version via `--artifact`, a service account path inside the repo.
- After the first real upload, open the release in the Firebase console and
  install it on one Android and one iOS device from the tester invite. Take a
  screenshot of the installed app showing `v1.1.1 (UAT)` (feature screenshots,
  `kmp-feature-scaffold` section 12).
- List the added files in the file report (`kmp-feature-scaffold` section 13):
  scripts, export options, workflows, `.gitignore`, `module.yaml` signing block,
  `env.properties` keys.

## 14. Checklist

- [ ] App Distribution enabled in **both** Firebase projects, for Android and iOS
- [ ] Groups created per project; `FIREBASE_DIST_GROUPS` in each `env.properties` holds aliases only, no emails
- [ ] One upload service account per project with only App Distribution Admin; JSON keys outside the repo
- [ ] Android release keystore used for every distributed APK; `keystore.properties` and `*.jks` git-ignored; `signing` block in `app/androidApp/module.yaml`
- [ ] UAT uploads APK; AAB only for a Play-linked PROD project
- [ ] iOS exports with `release-testing` / `ad-hoc`; tester UDIDs registered; CI signs with an App Store Connect API key
- [ ] `scripts/distribute.py` switches env, verifies package/bundle id and version, restores the previous env; `--dry-run` output checked
- [ ] PROD needs `--yes-prod`, a clean tree, a release build and a reviewer-approved CI environment
- [ ] Current version shown and "same version, new build, or new version" asked before each upload
- [ ] Release notes start with `v1.1.1 (UAT) · build 12 · <sha>`, body from `release-notes/<VERSION_NAME>.txt` (commit subjects only as fallback)
- [ ] CI secrets decoded to `$RUNNER_TEMP`, never printed; tests gate the upload jobs
- [ ] No App Distribution SDK in any Play or App Store build
- [ ] File report lists every added or changed file
