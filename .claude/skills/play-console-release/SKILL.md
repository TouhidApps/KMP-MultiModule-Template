---
name: play-console-release
description: Upload PROD Android App Bundles of a KMP + CMP app (KMP-MultiModule-Template / Amper) to Google Play Console with the Play Developer API — internal, closed, open and production tracks, staged rollout (increase, halt, resume, complete), promotion between tracks, release notes from release-notes/<version>.txt, R8 mapping upload, CI and credentials kept out of the repo. Use when releasing to Google Play, setting up Play uploads, promoting a build or changing a rollout.
---

# Google Play Console Release (AAB, tracks, staged rollout)

Store releases go out with one script that builds the **PROD** app bundle, checks
that the bundle really is PROD and the version in `config/version.properties`,
and publishes it to a Play track through the Google Play Developer API:

```bash
python3 scripts/play_release.py upload --track internal --dry-run         # build, check, let Play validate; nothing published
python3 scripts/play_release.py upload --track internal --confirm 12      # publish version code 12
python3 scripts/play_release.py promote --from internal --to production --rollout 0.1 --confirm 12
python3 scripts/play_release.py rollout --track production --fraction 0.5 --confirm 12
python3 scripts/play_release.py status
```

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
The build is Amper (`./kotlin`), which can't apply the Gradle Play Publisher plugin,
so uploads use the official API client for Python, next to `scripts/use_env.py`.
No Ruby or fastlane is needed for Android. The "What's new" text comes from
`release-notes/<VERSION_NAME>.txt` at the repo root, the one release notes file
per version that `app-store-connect-release` and `firebase-app-distribution`
read too.

Related skills, read the first two before this one:
- `kmp-environments`: only PROD goes to a store; `scripts/use_env.py`; version
  only in `config/version.properties`.
- `firebase-app-distribution`: release keystore and `module.yaml` signing block
  (section 5), the `distribute.py` pattern this script follows. Testers get
  UAT builds from there, not from Play.
- `store-assets-generator`: screenshots, feature graphic and listing text in
  `fastlane/metadata/android/<locale>/`.
- `app-store-connect-release`: the iOS counterpart. Both share
  `scripts/release_common.py`.
- `kmp-update-maintenance-alerts`: raise the minimum version only after the
  release reached 100%.

## 0. Before you start, ask once

- Is the app already on Google Play (at least one release uploaded by hand)?
  If not, section 2 is done first, by the user, in Play Console.
- Is the developer account a **personal** account created after November 2023?
  Then production needs a closed test with at least 12 testers for 14 days first.
- Which tracks the team uses: usually `internal` → `production`, optionally a
  closed track (`alpha` or a named one) for a client or beta group.
- The default production rollout: staged (recommended, start at 10% or 20%) or
  100% at once.
- Whether Play Console has **managed publishing** turned on (changes wait until
  someone presses "Publish").

Never ask for the service account JSON or keystore passwords in the chat.

## 1. How it fits together

```
config/version.properties ──┐
config/prod/env.properties ──┼─> use_env.py prod ──> ./kotlin package -m androidApp ──> signed .aab + mapping.txt
                             │                                                              │
release-notes/<VERSION_NAME>[.<lang>].txt ──────────────┐                                    ▼
                                                        └──> play_release.py: verify PROD, package, version, not debuggable
                                                                                            │
                                         Play Developer API edit: upload bundle + mapping + track release ──> validate ──> commit
```

| Track (API name) | Play Console name | Who gets it | Review |
|---|---|---|---|
| `internal` | Internal testing | up to 100 listed testers | none, available in minutes |
| `alpha` or a named track | Closed testing | email lists / Google Groups | yes |
| `beta` | Open testing | anyone who joins | yes |
| `production` | Production | everyone, or a % with staged rollout | yes, hours to a few days |

A Play **edit** is a draft transaction: nothing is visible until it is committed.
`--dry-run` makes an edit, uploads into it, asks Play to validate it, then
deletes it. That proves credentials, signing key, version code and track rules
without publishing anything.

Release status per track release:

| Status | Meaning | Script option |
|---|---|---|
| `completed` | 100% of the track's users | default |
| `inProgress` + `userFraction` | staged rollout, e.g. 0.1 = 10% | `--rollout 0.1` |
| `halted` | rollout stopped; users who have it keep it | `rollout --halt` |
| `draft` | saved, not released (required while the app was never published) | `--draft` |

## 2. One-time setup (the user does this in the consoles)

Tell the user these steps are theirs and only needed once per app.

1. **Create the app** in Play Console with the package name from
   `config/prod/env.properties` (`ANDROID_APPLICATION_ID`). The UAT package never
   goes to Play.
2. **App content** (Policy → App content): privacy policy URL
   **`https://abc.com/privacy`** (the PROD page from `web-admin-landing` 6d, never
   a `/uat` URL), data safety (must match what `kmp-analytics-logging` and Supabase
   collect, and what the published privacy policy says), the **delete account
   URL** under data safety if the app has accounts (a web page where users can
   ask for deletion without reinstalling the app; see `kmp-legal-consent` 6),
   content rating, target audience, ads, app access with a demo login. Play
   rejects releases until these are done.
3. **Store listing**: from `store-assets-generator`.

   Play requires the privacy policy in **both** places: the URL above and a link
   inside the app (More and Settings > Privacy, `kmp-legal-consent`). The page must
   be public (no login, not geo-blocked), a normal web page (not a PDF or an
   editable document), name the app or developer as in the listing, and be
   **published** in the PROD admin portal before the first review. The placeholder
   template from `web-admin-landing` 6d can't be published until every
   placeholder is filled, and the user must have the text reviewed by someone
   qualified. Claude never fills in the legal content for them as if it were final.
4. **First AAB by hand**: build it with `python3 scripts/play_release.py upload
   --track internal --dry-run` (the bundle stays under `build/`) and upload it in
   Play Console → Test and release → Internal testing. This enrols **Play App
   Signing**: Google keeps the app signing key, and the keystore from
   `firebase-app-distribution` section 5 becomes the **upload key**. Every later
   bundle must be signed with that same upload key.
5. **API access**:
   - Google Cloud console (any project the team owns, not the Firebase projects) →
     APIs & Services → enable **Google Play Android Developer API**.
   - IAM → Service accounts → create `play-release-uploader`. It needs **no**
     Google Cloud roles. Create a JSON key and save it outside the repo
     (section 9).
   - Play Console → Users and permissions → Invite new users → the service
     account's email → **App permissions** → this app only:
     *View app information (read-only)*, *Release apps to testing tracks*,
     *Release to production, exclude devices, and use Play App Signing*.
     Not *Admin*, not account-wide.
   - New permissions can take a while to work. A `403` right after the invite
     usually means "wait and retry".
6. **Testers**: internal and closed tracks get their tester lists in Play
   Console (email lists or a Google Group). Never commit tester emails.

## 3. Files

```
config/
  version.properties                  # from kmp-environments: VERSION_NAME, VERSION_CODE
  prod/env.properties                 # ANDROID_APPLICATION_ID, + PLAY_DEFAULT_ROLLOUT=0.1 (optional)
release-notes/
  <VERSION_NAME>.txt                  # committed: "What's new" of that version (en-US), max 500 characters
  <VERSION_NAME>.bn.txt               # optional: Bangla (bn-BD), same limit
scripts/
  use_env.py                          # kmp-environments, unchanged
  release_common.py                   # section 5, shared with app-store-connect-release
  play_release.py                     # section 6
  requirements-release.txt            # google-api-python-client, google-auth
.github/workflows/release-play.yml    # section 10
app/androidApp/keystore.properties    # NOT committed (firebase-app-distribution section 5)
```

`scripts/requirements-release.txt`:
```text
google-api-python-client==2.*
google-auth==2.*
```
Install with `pip install -r scripts/requirements-release.txt` (in a venv locally).

`.gitignore` already covers `*.aab`, `*.jks`, `**/keystore.properties` and
`*-sa.json` from `firebase-app-distribution`. Add:
```gitignore
play-*.json
```

## 4. Build and version rules

- **PROD only.** The script runs `use_env.py prod` and refuses unless the
  generated environment says `EnvName.PROD`. It also refuses a dirty git tree, so
  every store build maps to a commit.
- **Signed with the upload key**: the `signing` block in
  `app/androidApp/module.yaml` and the git-ignored `keystore.properties`
  (`firebase-app-distribution` section 5). Play answers "signed with the wrong
  key" otherwise. A lost upload key can be reset by the account owner in Play
  Console → App integrity; it is not the end of the app, but it takes days.
- **`VERSION_CODE` is higher than every code ever uploaded** to this app, on any
  track, including internal. Bump it in `config/version.properties` for every
  upload. `VERSION_NAME` changes only when the user decides.
- **Target API level:** Play requires a recent `targetSdk` every year (new apps
  and updates). The script prints the bundle's `targetSdkVersion`; compare it
  with Play Console → Policy status before a production release.
- `./kotlin package -m androidApp` builds the release bundle. Toolchain versions
  differ, so check `./kotlin package --help` once and adjust `ANDROID_BUILD` in
  `play_release.py`. The script takes the newest `.aab` and `mapping.txt` made by
  the build, so it doesn't hardcode output paths.
- **R8 mapping** goes to Play with the bundle, so Play Console's crash reports are
  readable. Crashlytics needs its own upload (`firebase-app-distribution`
  section 5).
- Install `bundletool` (or set `BUNDLETOOL_JAR`) so the script can read the
  bundle's manifest. Without it, it only warns.

## 5. Shared helpers: `scripts/release_common.py`

The same file is used by `app-store-connect-release`. If it already exists, keep
it.

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

## 6. The release script: `scripts/play_release.py`

`scripts/play_release.py`:

```python
#!/usr/bin/env python3
"""Publish PROD Android App Bundles to Google Play and manage track releases.

Usage:
  python3 scripts/play_release.py upload --track internal|alpha|beta|production|<closed track>
      [--rollout 0.1] [--draft] [--aab PATH] [--mapping PATH] [--not-sent-for-review] (--dry-run | --confirm CODE)
  python3 scripts/play_release.py promote --from internal --to production [--rollout 0.1]
      [--version-code N] [--not-sent-for-review] (--dry-run | --confirm CODE)
  python3 scripts/play_release.py rollout --track production (--fraction 0.5 | --complete | --halt | --resume)
      [--not-sent-for-review] (--dry-run | --confirm CODE)
  python3 scripts/play_release.py status

Credentials: PLAY_SA_JSON = path to the Play service account JSON, outside the repository.
"""
import argparse, json, os, shutil, subprocess, sys, time

sys.dont_write_bytecode = True                  # no scripts/__pycache__ to dirty the git tree
from release_common import (ROOT, add_write_flags, check_confirm, git, newest_artifact,
                            prod_config, prod_environment, release_notes, secret_file)

# Check `./kotlin package --help` for your toolchain version and adjust.
ANDROID_BUILD = ["./kotlin", "package", "-m", "androidApp"]
NOTES_LIMIT = 500                               # Play's limit per language
SCOPE = "https://www.googleapis.com/auth/androidpublisher"

HINTS = {
    "Only releases with status draft": "The app was never published. Use --draft, then release it in Play Console.",
    "cannot be sent for review automatically": "Managed publishing is on. Re-run with --not-sent-for-review, "
                                                "then send the changes from Play Console → Publishing overview.",
    "already been used": "Bump VERSION_CODE in config/version.properties.",
    "signed with the wrong key": "Sign with the upload key registered in Play Console → App integrity.",
    "The caller does not have permission": "Invite the service account in Play Console → Users and permissions, "
                                           "enable the Play Android Developer API, or wait for new permissions.",
}


def play_service():
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError:
        sys.exit("Missing packages: pip install -r scripts/requirements-release.txt")
    key = secret_file("PLAY_SA_JSON", "Play service account JSON")
    creds = service_account.Credentials.from_service_account_file(str(key), scopes=[SCOPE])
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


class Edit:
    """One Play edit. Nothing is visible until commit; an uncommitted edit is deleted."""

    def __init__(self, service, package):
        self.edits, self.package, self.id, self.committed = service.edits(), package, None, False

    def __enter__(self):
        self.id = self.edits.insert(packageName=self.package, body={}).execute()["id"]
        return self

    def __exit__(self, *exc):
        if self.id and not self.committed:
            try:
                self.edits.delete(packageName=self.package, editId=self.id).execute()
            except Exception:
                pass                                # an expired edit is gone anyway

    def get_track(self, track):
        return self.edits.tracks().get(packageName=self.package, editId=self.id, track=track).execute()

    def update_track(self, track, releases):
        body = {"track": track, "releases": releases}
        self.edits.tracks().update(packageName=self.package, editId=self.id, track=track, body=body).execute()

    def finish(self, dry_run, not_sent_for_review):
        self.edits.validate(packageName=self.package, editId=self.id).execute()
        if dry_run:
            print("Dry run: Play validated the edit. It is deleted now; nothing was published.")
            return
        self.edits.commit(packageName=self.package, editId=self.id,
                          changesNotSentForReview=not_sent_for_review).execute()
        self.committed = True
        print("Committed." + (" Send it for review in Play Console → Publishing overview."
                              if not_sent_for_review else ""))


def make_release(version_codes, name, notes, draft=False, rollout=None):
    release = {"name": name, "versionCodes": [str(c) for c in version_codes],
               "status": "draft" if draft else ("inProgress" if rollout else "completed")}
    if rollout:
        release["userFraction"] = rollout
    if notes:
        release["releaseNotes"] = notes
    return release


def check_rollout(value):
    if value is not None and not 0 < value < 1:
        sys.exit("--rollout/--fraction is a share of users between 0 and 1, e.g. 0.1 for 10%. Omit it for 100%.")


def check_aab(aab, props, version):
    """Reads the bundle manifest with bundletool; warns when bundletool is missing."""
    jar = os.environ.get("BUNDLETOOL_JAR")
    tool = ["java", "-jar", jar] if jar else (["bundletool"] if shutil.which("bundletool") else None)
    if tool is None:
        print("warning: bundletool not found (set BUNDLETOOL_JAR); bundle contents not verified")
        return

    def dump(xpath):
        result = subprocess.run([*tool, "dump", "manifest", f"--bundle={aab}", f"--xpath={xpath}"],
                                capture_output=True, text=True)
        return result.stdout.strip()

    found = {"package": dump("/manifest/@package"),
             "versionCode": dump("/manifest/@android:versionCode"),
             "versionName": dump("/manifest/@android:versionName")}
    expected = {"package": props["ANDROID_APPLICATION_ID"],
                "versionCode": version["VERSION_CODE"], "versionName": version["VERSION_NAME"]}
    if found != expected:
        sys.exit(f"{aab.name} does not match config: found {found}, expected {expected}. Rebuild after use_env.py prod.")
    if dump("/manifest/application/@android:debuggable") == "true":
        sys.exit(f"{aab.name} is debuggable. Play only takes release builds.")
    print(f"Bundle OK: {found['package']} {found['versionName']} ({found['versionCode']}), "
          f"targetSdkVersion {dump('/manifest/uses-sdk/@android:targetSdkVersion') or '?'}")


def cmd_upload(args):
    check_rollout(args.rollout)
    if args.draft and args.rollout:
        sys.exit("A draft has no rollout. Use --draft or --rollout, not both.")
    with prod_environment() as (props, version):
        code = version["VERSION_CODE"]
        check_confirm(args, code)
        notes = [{"language": locale, "text": text}       # fails here, before the build, when the file is missing
                 for locale, text in release_notes(version["VERSION_NAME"], "play", NOTES_LIMIT).items()]
        if args.aab:
            aab = (ROOT / args.aab).resolve()
            mapping = (ROOT / args.mapping).resolve() if args.mapping else None
        else:
            started = time.time()
            subprocess.run(ANDROID_BUILD, cwd=ROOT, check=True)
            aab = newest_artifact("*.aab", started)
            mapping = newest_artifact("mapping.txt", started, required=False)
        if not aab.is_file() or aab.suffix != ".aab":
            sys.exit(f"{aab} is not an .aab file. Play takes app bundles only.")
        check_aab(aab, props, version)
        if mapping is None:
            print("warning: no mapping.txt; Play crash reports will be obfuscated (is R8 on?)")

        rollout = args.rollout or (float(props["PLAY_DEFAULT_ROLLOUT"])
                                   if args.track == "production" and props.get("PLAY_DEFAULT_ROLLOUT") else None)
        check_rollout(rollout)
        name = f"{version['VERSION_NAME']} ({code})"
        package = props["ANDROID_APPLICATION_ID"]

        print(f"{'Validating' if args.dry_run else 'Publishing'} {package} {name} "
              f"commit {git('rev-parse', '--short', 'HEAD')} → track '{args.track}', "
              f"{'draft' if args.draft else f'{rollout:.0%} rollout' if rollout else '100%'}, "
              f"What's new: {', '.join(n['language'] for n in notes)}")
        with Edit(play_service(), package) as edit:
            media = _media(aab)
            uploaded = edit.edits.bundles().upload(packageName=package, editId=edit.id, media_body=media).execute()
            if str(uploaded["versionCode"]) != code:
                sys.exit(f"Play read version code {uploaded['versionCode']}, config says {code}.")
            if mapping:
                edit.edits.deobfuscationfiles().upload(
                    packageName=package, editId=edit.id, apkVersionCode=int(code),
                    deobfuscationFileType="proguard", media_body=_media(mapping)).execute()
            edit.update_track(args.track, [make_release([code], name, notes, args.draft, rollout)])
            edit.finish(args.dry_run, args.not_sent_for_review)


def cmd_promote(args):
    check_rollout(args.rollout)
    props, _ = prod_config()
    package = props["ANDROID_APPLICATION_ID"]
    with Edit(play_service(), package) as edit:
        releases = [r for r in edit.get_track(args.from_track).get("releases", []) if r.get("versionCodes")]
        if args.version_code:
            releases = [r for r in releases if str(args.version_code) in r["versionCodes"]]
        if not releases:
            sys.exit(f"No release{' with that version code' if args.version_code else ''} on track '{args.from_track}'.")
        source = max(releases, key=lambda r: max(int(c) for c in r["versionCodes"]))
        code = max(int(c) for c in source["versionCodes"])
        check_confirm(args, code)
        print(f"Promoting {source.get('name', code)} from '{args.from_track}' to '{args.to_track}', "
              f"{f'{args.rollout:.0%} rollout' if args.rollout else '100%'}")
        edit.update_track(args.to_track, [make_release(source["versionCodes"], source.get("name"),
                                                       source.get("releaseNotes"), rollout=args.rollout)])
        edit.finish(args.dry_run, args.not_sent_for_review)


def cmd_rollout(args):
    check_rollout(args.fraction)
    props, _ = prod_config()
    package = props["ANDROID_APPLICATION_ID"]
    with Edit(play_service(), package) as edit:
        releases = edit.get_track(args.track).get("releases", [])
        staged = next((r for r in releases if r.get("status") in ("inProgress", "halted")), None)
        if staged is None:
            sys.exit(f"No staged rollout on track '{args.track}'. Run status.")
        code = max(int(c) for c in staged["versionCodes"])
        check_confirm(args, code)
        before = f"{staged['status']} {staged.get('userFraction', 1):.0%}"
        if args.complete:
            staged["status"] = "completed"
            staged.pop("userFraction", None)
            releases = [staged]                      # the new release replaces the old one for everybody
        elif args.halt:
            staged["status"] = "halted"
        elif args.resume:
            staged["status"] = "inProgress"
        else:
            if args.fraction <= staged.get("userFraction", 0):
                sys.exit("A staged rollout can only grow. Use --halt to stop it.")
            staged["status"], staged["userFraction"] = "inProgress", args.fraction
        print(f"{staged.get('name', code)} on '{args.track}': {before} → {staged['status']} "
              f"{staged.get('userFraction', 1):.0%}")
        edit.update_track(args.track, releases)
        edit.finish(args.dry_run, args.not_sent_for_review)


def cmd_status(args):
    props, _ = prod_config()
    package = props["ANDROID_APPLICATION_ID"]
    with Edit(play_service(), package) as edit:                # read-only: the edit is deleted
        tracks = edit.edits.tracks().list(packageName=package, editId=edit.id).execute().get("tracks", [])
    for track in tracks:
        for r in track.get("releases", []):
            share = f" {r['userFraction']:.0%}" if "userFraction" in r else ""
            print(f"{track['track']:<12} {r.get('name', '-'):<16} codes {','.join(r.get('versionCodes', []))}"
                  f"  {r['status']}{share}")


def _media(path):
    from googleapiclient.http import MediaFileUpload
    return MediaFileUpload(str(path), mimetype="application/octet-stream", resumable=True)


def main():
    parser = argparse.ArgumentParser(description="Google Play releases (PROD only)")
    sub = parser.add_subparsers(dest="command", required=True)

    up = sub.add_parser("upload", help="build, verify and publish a bundle to a track")
    up.add_argument("--track", required=True)
    up.add_argument("--rollout", type=float, help="staged rollout share, e.g. 0.1")
    up.add_argument("--draft", action="store_true", help="save as draft (required before the first publish)")
    up.add_argument("--aab", help="skip the build and use this bundle")
    up.add_argument("--mapping", help="mapping.txt for --aab")

    pr = sub.add_parser("promote", help="put a release of one track on another track")
    pr.add_argument("--from", dest="from_track", required=True)
    pr.add_argument("--to", dest="to_track", required=True)
    pr.add_argument("--rollout", type=float)
    pr.add_argument("--version-code", type=int, help="default: the newest release on --from")

    ro = sub.add_parser("rollout", help="grow, halt, resume or complete a staged rollout")
    ro.add_argument("--track", default="production")
    change = ro.add_mutually_exclusive_group(required=True)
    change.add_argument("--fraction", type=float)
    change.add_argument("--complete", action="store_true")
    change.add_argument("--halt", action="store_true")
    change.add_argument("--resume", action="store_true")

    for p in (up, pr, ro):
        p.add_argument("--not-sent-for-review", action="store_true",
                       help="commit without sending for review (managed publishing)")
        add_write_flags(p)
    sub.add_parser("status", help="list releases on all tracks")

    args = parser.parse_args()
    commands = {"upload": cmd_upload, "promote": cmd_promote, "rollout": cmd_rollout, "status": cmd_status}
    try:
        from googleapiclient.errors import HttpError
    except ImportError:
        HttpError = ()                              # play_service() explains the missing package
    try:
        commands[args.command](args)
    except HttpError as error:
        text = error.content.decode(errors="replace")
        hint = next((h for k, h in HINTS.items() if k in text), None)
        try:
            text = json.loads(text)["error"]["message"]
        except (ValueError, KeyError, TypeError):
            pass
        sys.exit(f"Play API error: {text}" + (f"\nHint: {hint}" if hint else ""))


if __name__ == "__main__":
    main()
```

What the script refuses, and why:

| Refusal | Protects against |
|---|---|
| No `--dry-run` and no `--confirm <version code>` | publishing by accident; the code typed must be the one released |
| Generated environment isn't PROD, or the tree is dirty | a UAT build in the store, or a build that can't be traced to a commit |
| Bundle package, version name or code differs from config | a stale bundle or the UAT package (needs `bundletool`) |
| Debuggable bundle | a debug build on Play |
| Version code Play reads differs from config | uploading a different bundle than intended |
| Rollout outside 0–1, a shrinking rollout, rollout on a draft | invalid or unintended rollouts |
| `release-notes/<VERSION_NAME>.txt` missing or empty | a release without "What's new", or last version's text |
| Release notes over 500 characters | Play rejecting the edit at the end |
| Service account JSON inside the repo | committing the upload credential |

## 7. Release notes ("What's new")

- One file per version, at the repo root: `release-notes/<VERSION_NAME>.txt`
  (e.g. `release-notes/1.1.1.txt`), named by version name, not version code.
  Every upload of that version (internal, then production, a rebuilt code)
  sends the same text. It is the `en-US` "What's new".
- Optional translation next to it: `release-notes/1.1.1.bn.txt` becomes the
  `bn-BD` "What's new". Other languages: add the key and its Play locale to
  `NOTES_LOCALES` in `release_common.py`; the script refuses a file it can't map.
  Languages without a file show the default language's text.
- Max **500 characters** per file (Play's limit, counted as characters, so
  Bangla fits about as much as English). The script refuses longer text before
  it builds anything.
- `upload` fails when `release-notes/<VERSION_NAME>.txt` is missing or empty.
  The `fastlane/metadata/android/<locale>/changelogs/` folder isn't used; delete
  old files there so there is one source.
- Lines are added while the version is developed: each feature or user-visible
  fix appends one line (`kmp-feature-scaffold` section 14). Before the upload,
  Claude shortens and orders them for users and checks the length.
- Written for users, not testers: what is new or fixed, in plain words, no
  ticket numbers, no internal names. The Bangla file is written properly, not
  machine-literal. The same rules as the listing: no rankings, prices or claims.
- Write the file in the release commit, before the upload, so the tree is clean.
- Promotion copies the release notes of the source track's release, so they
  don't change between internal and production.

## 8. Typical release flow

1. Show the user the current `VERSION_NAME (VERSION_CODE)` and ask: new
   version or same version (section 11). Bump `VERSION_CODE` either way, and
   `VERSION_NAME` for a new version. Check `release-notes/<VERSION_NAME>.txt`
   is final and under 500 characters, commit.
2. `upload --track internal --dry-run`. Show the user the printed summary.
3. With the user's go-ahead: `upload --track internal --confirm <code>`. Internal
   testers get it in minutes from the Play Store.
4. When internal testing passed and the user says so:
   `promote --from internal --to production --rollout 0.1 --confirm <code>`.
   The same bundle is promoted, nothing is rebuilt.
5. Watch Play Console → Android vitals and Crashlytics for a day or two, then
   `rollout --fraction 0.5`, then `rollout --complete`, each with the user's
   go-ahead.
6. Bad crash rate: `rollout --halt` stops new installs of that version. Users
   who already updated keep it. Fix, bump `VERSION_CODE`, ship a new release.
   Play can't roll back to a lower version code.
7. After 100%: tag the commit (`git tag v1.1.1`), and only then raise the minimum
   version in the admin portal if needed (`kmp-update-maintenance-alerts`).

Review: production and closed/open tracks are reviewed. A release shows
"In review" in Play Console; the app update reaches users after approval, which
usually takes hours, sometimes days. Say this to the user after each commit.

## 9. Credentials (never in the repo)

| Secret | Local place | CI secret |
|---|---|---|
| Play service account JSON | `~/.config/store-release/play-sa.json` | `PLAY_SA_JSON_B64`, `production` environment only |
| Upload keystore + passwords | `~/.keystores/`, `keystore.properties` | `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PROPERTIES` (same as `firebase-app-distribution`) |
| Firebase / Supabase PROD config | `config/prod/` (git-ignored) | `*_PROD` secrets from `kmp-environments` |

- The Play service account is separate from the Firebase App Distribution
  service accounts and has only this app's release permissions.
- Never print a secret: no `cat` of decoded files, no `set -x` near them. Decode
  into `$RUNNER_TEMP`, not into the checkout.
- A key that was committed or pasted anywhere: tell the user to delete that key
  in Google Cloud → Service accounts → Keys and create a new one. Removing it from
  git isn't enough.

## 10. CI (GitHub Actions)

Manual only, in a GitHub **environment** named `production` with required
reviewers, so a person approves every store write. The person who starts the run
types the version code as confirmation.

`.github/workflows/release-play.yml`:

```yaml
name: Release to Google Play
on:
  workflow_dispatch:
    inputs:
      command:
        description: "upload, promote or rollout"
        type: choice
        options: [upload, promote, rollout]
        default: upload
      args:
        description: "e.g. --track internal | --from internal --to production --rollout 0.1 | --fraction 0.5"
        required: true
      confirm:
        description: "Version code to publish (empty = dry run)"
        required: false

concurrency: release-play

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - run: ./kotlin test

  play:
    needs: test
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - run: pip install -r scripts/requirements-release.txt
      - name: Secrets to files
        env:
          GS: ${{ secrets.GOOGLE_SERVICES_PROD }}
          SA: ${{ secrets.PLAY_SA_JSON_B64 }}
          KS: ${{ secrets.ANDROID_KEYSTORE_B64 }}
          KP: ${{ secrets.ANDROID_KEYSTORE_PROPERTIES }}
        run: |
          echo "$GS" | base64 -d > config/prod/google-services.json
          echo "$SA" | base64 -d > "$RUNNER_TEMP/play-sa.json"
          echo "$KS" | base64 -d > "$RUNNER_TEMP/release.jks"
          printf '%s\n' "$KP" | sed "s|^storeFile=.*|storeFile=$RUNNER_TEMP/release.jks|" \
            > app/androidApp/keystore.properties
      - name: Release
        env:
          PLAY_SA_JSON: ${{ runner.temp }}/play-sa.json
          COMMAND: ${{ inputs.command }}
          ARGS: ${{ inputs.args }}
          CONFIRM: ${{ inputs.confirm }}
        run: |
          if [ -n "$CONFIRM" ]; then MODE="--confirm $CONFIRM"; else MODE="--dry-run"; fi
          # ARGS is split on spaces on purpose; track names have none
          python3 scripts/play_release.py "$COMMAND" $ARGS $MODE
```

`keystore.properties` lands in the checkout because Amper reads it from the
module, and `prod_environment()` checks for a dirty tree first. It is
git-ignored, so `git status` stays clean. Add the release-safety grep from
`kmp-environments` section 6 if the team wants it visible in the log as well.

## 11. Rules for Claude

- Claude may build, run `--dry-run` and `status` on its own, and show the
  result.
- **Before every upload, show the version and ask.** Read
  `config/version.properties` and say e.g. "Current version is 1.1.1 (12). New
  version (1.1.2), or same version 1.1.1 with a new build (13)?" Play never
  takes a version code twice, so "same version" means the same `VERSION_NAME`
  with `VERSION_CODE` + 1; reusing code 12 is not offered. A new version
  starts `release-notes/<new VERSION_NAME>.txt`; if the old version never
  reached a store, move its lines over. Promote and rollout reuse an uploaded
  build and need no bump.
- A command with `--confirm` (or a CI run with `confirm` filled in) changes what
  real users get. Run it only after the user, in this task, said to publish
  **that version to that track** ("upload 1.1.1 (12) to internal"). A go-ahead for
  internal is not one for production. Each promote, rollout change, complete or
  halt needs its own go-ahead. For a halt during a crash spike, recommend it at
  once and wait for the word.
- Never change Play Console settings, tester lists, the listing or app content
  through the API without being asked. This skill only touches track releases.
- After a write, report: track, version, rollout share, commit, and that review
  may take hours to days.

## 12. Verification

- `status` works: credentials and permissions are right.
- `upload --track internal --dry-run` ends with "Play validated the edit".
  Check the printed package, version, commit, track and rollout.
- Refusals: no `--confirm`, wrong `--confirm`, a dirty tree, `--aab` with an
  older bundle, `PLAY_SA_JSON` inside the repo, `--rollout 2`.
- Before the first review (and each time data collection changes), check the
  legal pages are live in PROD and indexable:
  ```bash
  for page in privacy terms; do
    curl -sS -o /dev/null -w "%{http_code} $page\n" "https://abc.com/$page" -L
    curl -sSI -L "https://abc.com/$page" | grep -i "x-robots-tag" && echo "noindex on $page: fix before submitting"
  done
  ```
  Both must print `200`. Then compare the privacy policy's data list with the
  data safety form.
- After the first internal release, install it from the Play Store on a test
  device and take a screenshot of the About page or drawer showing `v1.1.1`
  without "(UAT)" (`kmp-feature-scaffold` section 12).
- File report (`kmp-feature-scaffold` section 13): scripts, requirements,
  release notes, workflow, `.gitignore`, `env.properties` keys.

## 13. Checklist

- [ ] App created in Play Console with the PROD package; app content, data safety and listing complete
- [ ] Privacy policy URL = PROD `https://abc.com/privacy`, published (no placeholders, reviewed by the user's side) and linked inside the app; delete account URL set if the app has accounts; data safety matches the policy
- [ ] First bundle uploaded by hand; Play App Signing on; the template keystore is the upload key
- [ ] Personal account: closed test with 12+ testers for 14 days done before production
- [ ] Play Android Developer API enabled; service account with this app's release permissions only; JSON outside the repo
- [ ] `scripts/release_common.py` + `scripts/play_release.py` + `requirements-release.txt` added; `ANDROID_BUILD` checked against `./kotlin package --help`
- [ ] Every upload: PROD env, clean tree, higher `VERSION_CODE`, bundle verified with bundletool, mapping uploaded
- [ ] `release-notes/<VERSION_NAME>.txt` committed (optional `.bn.txt`), under 500 characters each; no old `changelogs/` folder
- [ ] Current version shown and "new or same version" asked before each upload
- [ ] `--dry-run` shown to the user; `--confirm <code>` only after their go-ahead for that track
- [ ] Production with staged rollout; rollout grown, halted or completed only on the user's word
- [ ] CI: manual workflow, `production` environment with reviewers, tests gate the job, secrets in `$RUNNER_TEMP`
- [ ] Minimum version in the admin portal raised only after 100% rollout
- [ ] File report lists every added or changed file
