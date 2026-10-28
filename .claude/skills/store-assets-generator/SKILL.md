---
name: store-assets-generator
description: Generate Google Play and Apple App Store listing graphics for a KMP + CMP app (KMP-MultiModule-Template / Amper) — capture real PROD screenshots with a clean status bar, frame them with original captions at the exact store sizes (phone, tablet, iPhone 6.9", iPad 13"), make the Play feature graphic and store icon, validate them, and lay them out for fastlane. Use when preparing or updating store screenshots, store graphics or a store listing.
---

# Store Assets: App Store and Play Store Screenshots and Graphics

Store graphics are made from **real screenshots of the PROD build**, framed with
original captions on the brand gradient, at the exact sizes each store accepts.
One script composes and validates everything, and writes it in the folder layout
`fastlane` expects. Uploading is a separate step that the user must approve.

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
Related skills:
- `app-graphics-generator`: logo, `branding/brand.json`, and the Play 512 icon
  this skill uses. Run it first.
- `kmp-environments`: PROD vs UAT, `scripts/use_env.py`, the release guard.
- `kmp-feature-scaffold` section 12: screenshot commands. Store screenshots are
  the polished version of the same capture.
- `compose-multiplatform-ui` 2.0.1: copyright rules. They apply to every pixel and word here.

## 1. Rules (both stores)

- **Real app, PROD build.** Every screenshot shows the real UI of the PROD build.
  No mock-ups, no edited UI, no features the app doesn't have, no "(UAT)" title,
  UAT badge or debug banner. The capture scripts refuse to run unless the
  environment is PROD.
- **Original content only.** Captions are your own words. Demo data is invented
  and neutral (fictional names, initials avatars, sample items). No photos of
  real people, no real user data, no copyrighted images, songs, film titles,
  brands or logos inside the app's demo content.
- **No privacy leaks.** Use a dedicated demo account. Check every frame for
  emails, phone numbers, tokens, IDs, internal URLs or notifications before
  composing.
- **No store-policy traps** in images, captions or the feature graphic:
  - no rankings or awards ("#1", "Best", "Top", "App of the Day"), no prices or
    promotions ("Free", "50% off"), no calls like "Download now";
  - no store badges ("Get it on Google Play", "Download on the App Store"), no
    Apple or Google logos, no other platforms' devices (no Android phones in iOS
    screenshots, and vice versa);
  - no misleading claims, no fake ratings or testimonials.
- **Device frames are drawn by the script** (a plain rounded bezel). Don't paste
  vendor device artwork or frames from mock-up sites.
- **Fonts:** the app's own OFL/Apache font from `core/ui/composeResources/font/`.
  For Bangla, use an OFL font with Bengali glyphs (e.g. Noto Sans Bengali), and
  Pillow built with **libraqm**, or conjuncts break
  (`python3 -c "from PIL import features; print(features.check('raqm'))"` must print `True`).
- **Uploading is outward-facing.** Generate and show the result. Upload only
  after the user says so (section 8).

## 2. Store specifications

Store rules change. These were current in October 2026. Before a release, check
the Play Console and App Store Connect help pages, and update `TARGETS` in the
script if something has changed.

**Google Play**

| Asset | Size / format | Count |
|---|---|---|
| App icon | 512×512, 32-bit PNG (alpha ok), ≤ 1 MB, full square: Play adds the mask and shadow | 1 |
| Feature graphic | 1024×500, JPEG or 24-bit PNG, **no alpha** | 1 (required) |
| Phone screenshots | PNG/JPEG no alpha, each side 320–3840 px, long side ≤ 2× short side. We use **1080×1920** (9:16), which also qualifies for promotion | 2–8 (aim for 4+) |
| 7" tablet screenshots | we use 1200×1920 | 0–8 (needed to be listed as tablet-ready) |
| 10" tablet screenshots | we use 1600×2560 | 0–8 |

Raw emulator captures are often 20:9 (e.g. 1080×2400). That ratio is over
2:1, so Play rejects it. That is one more reason to always compose onto the
target canvas.

**Apple App Store**

| Asset | Size / format | Count |
|---|---|---|
| iPhone 6.9" screenshots | **1320×2868** portrait (1290×2796 also accepted), PNG/JPEG, RGB, no alpha. App Store Connect scales them for smaller iPhones | 1–10 per locale (required) |
| iPad 13" screenshots | **2064×2752** portrait (2048×2732 also accepted) | 1–10, required only if the app runs on iPad |
| App icon | not uploaded: it comes from the build's `AppIcon` (`app-graphics-generator`) | — |
| App preview video | optional, 15–30 s, recorded from the real app | 0–3 |

Check whether the iOS app supports iPad (`UIDeviceFamily` / targeted device
family). If it is iPhone-only, skip the iPad target, and don't produce iPad
screenshots that show a stretched phone UI.

## 3. Layout

```
store/
  store.json                      # committed: locales, targets, fonts
  captions/<locale>.json          # committed: original caption copy per locale
  raw/<target>/<locale>/NN_<name>.png   # NOT committed (git-ignore): captured from PROD
  preview.png                     # NOT committed: contact sheet for review
scripts/store/
  capture_android.sh              # PROD check, clean status bar, screencap per screen
  capture_ios.sh                  # PROD check, status bar override, simctl screenshot
  compose_store_assets.py         # frame + caption + feature graphic + icon + validate
fastlane/
  metadata/android/<play-locale>/images/{icon.png, featureGraphic.png, phoneScreenshots/, sevenInchScreenshots/, tenInchScreenshots/}
  screenshots/<ios-locale>/       # deliver picks the device from the pixel size
```
Commit `fastlane/metadata` and `fastlane/screenshots` (the approved assets), so
the listing is versioned with the app. Never commit store credentials.

`store/store.json`:
```json
{
  "locales": {
    "en": { "play": "en-US", "ios": "en-US" },
    "bn": { "play": "bn-BD" }
  },
  "targets": ["android-phone", "android-tablet10", "ios-phone-6.9"],
  "fonts": {
    "default": { "bold": "core/ui/composeResources/font/inter_bold.ttf",
                 "regular": "core/ui/composeResources/font/inter_regular.ttf" },
    "bn":      { "bold": "core/ui/composeResources/font/noto_sans_bengali_bold.ttf",
                 "regular": "core/ui/composeResources/font/noto_sans_bengali_regular.ttf" }
  }
}
```
- Locale keys match the app's languages (`StringsEn`, `StringsBn`). Leave out a
  store for a locale when that store doesn't offer the language, or the listing
  isn't localized there. Check App Store Connect's language list before adding
  an `ios` code. Missing locales fall back to the store's primary language.
- Use the font file names that actually exist in `core/ui/composeResources/font/`.

`store/captions/en.json`, one entry per raw file name (without `.png`):
```json
{
  "featureGraphic": { "tagline": "Plan your day in one calm place" },
  "screenshots": {
    "01_home":     { "title": "Everything at a glance", "subtitle": "Your day, sorted the moment you open the app" },
    "02_detail":   { "title": "Details without clutter" },
    "03_chat":     { "title": "Talk to your team" },
    "04_settings": { "title": "Light, dark and your language" }
  }
}
```
Caption rules: a title of up to about 30 characters that states one benefit;
an optional subtitle under about 60 characters; the same order and story in every locale;
translations written properly (in `bn.json`, not machine-literal), and no
policy words from section 1.

## 4. Plan the set

Before capturing, agree on the list (it is the listing's story), 4–6 screens:
1. The main value screen (usually `feature/home` with content).
2. The core action or detail screen.
3. A differentiator (chat, AI assistant, offline, notifications…).
4. Personalization: settings with dark mode and language (shows Bangla support).
5. Optional: profile, search or onboarding. **Never** a splash, login form, empty
   state, error or permission dialog as a store screenshot.

Use one theme for the whole set (light, unless the brand is dark), the same demo
account and data across platforms, and the same order on Android and iOS.

## 5. Capture (PROD, clean status bar)

Prepare:
```bash
python3 scripts/use_env.py prod      # store assets come from PROD only
./kotlin build                       # then install the PROD build on the emulator/simulator
```
Log in with the demo account and switch the language inside the app (the
template's `LocalizationManager`, Settings > Language) for each locale.

| Target | Device to use | Raw size |
|---|---|---|
| `android-phone` | Pixel phone emulator | any (e.g. 1080×2400), composed to 1080×1920 |
| `android-tablet7` / `android-tablet10` | Pixel Tablet emulator, or a 7"/10" AVD | any, composed to target |
| `ios-phone-6.9` | iPhone Pro Max simulator, 6.9" class (1320×2868) | 1320×2868 |
| `ios-ipad-13` | iPad Pro 13-inch simulator (2064×2752) | 2064×2752 |

```bash
#!/usr/bin/env bash
# Captures store screenshots from the PROD build on a connected Android emulator/device.
# Usage: scripts/store/capture_android.sh <target> <locale> <name>...
#   e.g. scripts/store/capture_android.sh android-phone en 01_home 02_detail 03_chat 04_settings
set -euo pipefail
target=$1 locale=$2; shift 2

grep -rqs "EnvName.PROD" --include=GeneratedEnvironment.kt core/common \
  || { echo "Refusing: run 'python3 scripts/use_env.py prod' and install the PROD build first"; exit 1; }

out="store/raw/$target/$locale"
mkdir -p "$out"

demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false
trap 'demo exit' EXIT                                  # always restore the real status bar

for name in "$@"; do
  read -rp "Open the '$name' screen ($locale), then press Enter "
  adb exec-out screencap -p > "$out/$name.png"
  echo "  saved $out/$name.png"
done
```
```bash
#!/usr/bin/env bash
# Captures store screenshots from the PROD build on the booted iOS simulator.
# Usage: scripts/store/capture_ios.sh <target> <locale> <name>...
#   e.g. scripts/store/capture_ios.sh ios-phone-6.9 en 01_home 02_detail 03_chat 04_settings
set -euo pipefail
target=$1 locale=$2; shift 2

grep -rqs "EnvName.PROD" --include=GeneratedEnvironment.kt core/common \
  || { echo "Refusing: run 'python3 scripts/use_env.py prod' and install the PROD build first"; exit 1; }

out="store/raw/$target/$locale"
mkdir -p "$out"

xcrun simctl status_bar booted override --time "9:41" --dataNetwork wifi --wifiMode active \
  --wifiBars 3 --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100
trap 'xcrun simctl status_bar booted clear' EXIT       # always restore the real status bar

for name in "$@"; do
  read -rp "Open the '$name' screen ($locale), then press Enter "
  xcrun simctl io booted screenshot --type=png "$out/$name.png" > /dev/null
  echo "  saved $out/$name.png"
done
```

After capturing, open every raw file and check it: correct screen, real content
loaded (no spinners), no personal data, no UAT marks, clean status bar, no
keyboard or toasts unless they are the point.

## 6. Compose and validate: `scripts/store/compose_store_assets.py`

```bash
pip install pillow                  # needs libraqm for Bangla (brew install libraqm / apt install libraqm0)
python3 scripts/branding/make_icons.py        # app-graphics-generator: Play icon + brand colors
python3 scripts/store/compose_store_assets.py
```
For every locale and target it puts the brand gradient (`brand.json`
`background`) on the exact canvas, adds the caption title and subtitle in white,
and places the raw screenshot in a drawn rounded bezel. It saves without alpha,
creates the Play feature graphic (icon + app name + tagline) and copies the Play
512 icon. It then **validates** sizes, alpha, file sizes and per-store counts, and
exits with a list of problems if anything fails. It also writes
`store/preview.png`, a contact sheet of everything.

```python
#!/usr/bin/env python3
"""Turns raw PROD screenshots into store-ready assets, then validates them.

Input:  store/raw/<target>/<locale>/NN_<name>.png   (captured from the real PROD build)
        store/captions/<locale>.json                  (original marketing copy)
        branding/brand.json, branding/generated/prod/store/play-icon-512.png
Output: fastlane/metadata/android/<play-locale>/images/...  and  fastlane/screenshots/<ios-locale>/...
Usage:  python3 scripts/store/compose_store_assets.py
Needs:  pip install pillow   (with libraqm for Bangla and other complex scripts)
"""
import json
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, features

ROOT = Path(__file__).resolve().parents[2]
BRAND = json.loads((ROOT / "branding/brand.json").read_text())
STORE = json.loads((ROOT / "store/store.json").read_text())
RAW = ROOT / "store/raw"
PLAY_ICON = ROOT / "branding/generated/prod/store/play-icon-512.png"
PLAY_OUT = ROOT / "fastlane/metadata/android"
IOS_OUT = ROOT / "fastlane/screenshots"

# target: (width, height, store, fastlane folder). Sizes checked against the consoles in Oct 2026.
TARGETS = {
    "android-phone":    (1080, 1920, "play", "phoneScreenshots"),
    "android-tablet7":  (1200, 1920, "play", "sevenInchScreenshots"),
    "android-tablet10": (1600, 2560, "play", "tenInchScreenshots"),
    "ios-phone-6.9":    (1320, 2868, "ios", None),
    "ios-ipad-13":      (2064, 2752, "ios", None),
}
LIMITS = {"play": (2, 8), "ios": (1, 10)}          # screenshots per device type and locale
MAX_BYTES = 8 * 1024 * 1024


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def gradient(width, height):
    start, end = hex_rgb(BRAND["background"]["start"]), hex_rgb(BRAND["background"]["end"])
    column = Image.new("RGB", (1, height))
    for y in range(height):
        t = y / max(height - 1, 1)
        column.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(start, end)))
    return column.resize((width, height))


def font(locale, size, bold=True):
    fonts = STORE["fonts"].get(locale, STORE["fonts"]["default"])
    engine = ImageFont.Layout.RAQM if features.check("raqm") else ImageFont.Layout.BASIC
    return ImageFont.truetype(str(ROOT / fonts["bold" if bold else "regular"]), size, layout_engine=engine)


def wrap(draw, text, face, max_width):
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if draw.textlength(candidate, font=face) <= max_width or not line:
            line = candidate
        else:
            lines.append(line)
            line = word
    return lines + [line] if line else lines


def draw_text_block(draw, lines, face, x, top, color, spacing, anchor="ma"):
    for line in lines:
        draw.text((x, top), line, font=face, fill=color, anchor=anchor)
        top += face.size + spacing
    return top


def framed(raw, max_w, max_h):
    """The screenshot inside a plain rounded bezel drawn here (no vendor device artwork)."""
    bezel = round(max_w * 0.025)
    scale = min((max_w - 2 * bezel) / raw.width, (max_h - 2 * bezel) / raw.height)
    screen = raw.convert("RGB").resize((round(raw.width * scale), round(raw.height * scale)), Image.LANCZOS)
    w, h = screen.width + 2 * bezel, screen.height + 2 * bezel
    radius = round(w * 0.09)
    frame = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=(18, 18, 22, 255))
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen.width - 1, screen.height - 1), radius - bezel, fill=255)
    frame.paste(screen, (bezel, bezel), mask)
    return frame


def compose_screenshot(raw_path, caption, locale, width, height):
    canvas = gradient(width, height).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    title_face, sub_face = font(locale, round(width * 0.065)), font(locale, round(width * 0.036), bold=False)
    top = round(height * 0.05)
    top = draw_text_block(draw, wrap(draw, caption["title"], title_face, width * 0.86), title_face,
                          width // 2, top, "white", round(title_face.size * 0.25))
    if caption.get("subtitle"):
        top = draw_text_block(draw, wrap(draw, caption["subtitle"], sub_face, width * 0.80), sub_face,
                              width // 2, top + round(height * 0.012), (255, 255, 255, 220), round(sub_face.size * 0.3))
    top += round(height * 0.03)
    device = framed(Image.open(raw_path), round(width * 0.80), height - top - round(height * 0.04))
    canvas.alpha_composite(device, ((width - device.width) // 2, top))
    return canvas.convert("RGB")                      # stores reject alpha in screenshots


def compose_feature_graphic(locale, tagline, out_path):
    width, height = 1024, 500
    canvas = gradient(width, height).convert("RGBA")
    icon = Image.open(PLAY_ICON).convert("RGBA").resize((220, 220), Image.LANCZOS)
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 219, 219), 48, fill=255)
    canvas.paste(icon, (96, (height - 220) // 2), mask)
    draw = ImageDraw.Draw(canvas)
    name_face, tag_face = font(locale, 64), font(locale, 34, bold=False)
    draw.text((360, 170), BRAND["appName"], font=name_face, fill="white")
    draw_text_block(draw, wrap(draw, tagline, tag_face, 600), tag_face, 360, 260, (255, 255, 255, 230), 10, anchor="la")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(out_path, optimize=True)


def contact_sheet(paths, out_path, height=640):
    """One PNG with every generated asset side by side, for the user's review."""
    thumbs = [Image.open(p).convert("RGB") for p in paths]
    thumbs = [t.resize((round(t.width * height / t.height), height)) for t in thumbs]
    sheet = Image.new("RGB", (sum(t.width for t in thumbs) + 24 * (len(thumbs) + 1), height + 48), "#E6E6EA")
    x = 24
    for thumb in thumbs:
        sheet.paste(thumb, (x, 24))
        x += thumb.width + 24
    sheet.save(out_path, optimize=True)


def validate(path, size, alpha_allowed=False):
    with Image.open(path) as image:
        problems = []
        if image.size != size:
            problems.append(f"size {image.size} != {size}")
        if not alpha_allowed and image.mode not in ("RGB", "L"):
            problems.append(f"mode {image.mode} has alpha")
    if path.stat().st_size > (1024 * 1024 if alpha_allowed else MAX_BYTES):
        problems.append(f"{path.stat().st_size} bytes is too large")
    return [f"{path.relative_to(ROOT)}: {p}" for p in problems]


def main():
    if not PLAY_ICON.exists():
        sys.exit("Run scripts/branding/make_icons.py first")
    problems, outputs = [], []
    for locale, codes in STORE["locales"].items():
        captions = json.loads((ROOT / f"store/captions/{locale}.json").read_text())
        for target, (width, height, store, folder) in TARGETS.items():
            if store not in codes or target not in STORE["targets"]:
                continue
            raws = sorted((RAW / target / locale).glob("*.png"))
            low, high = LIMITS[store]
            if not low <= len(raws) <= high:
                problems.append(f"{target}/{locale}: {len(raws)} screenshots, need {low}-{high}")
                continue
            if store == "play":
                out_dir = PLAY_OUT / codes["play"] / "images" / folder
            else:
                out_dir = IOS_OUT / codes["ios"]
            out_dir.mkdir(parents=True, exist_ok=True)
            for raw in raws:
                caption = captions["screenshots"][raw.stem]
                out = out_dir / f"{target}_{raw.stem}.png" if store == "ios" else out_dir / f"{raw.stem}.png"
                compose_screenshot(raw, caption, locale, width, height).save(out, optimize=True)
                outputs.append(out)
                problems += validate(out, (width, height))
        if "play" in codes:
            images = PLAY_OUT / codes["play"] / "images"
            compose_feature_graphic(locale, captions["featureGraphic"]["tagline"], images / "featureGraphic.png")
            shutil.copyfile(PLAY_ICON, images / "icon.png")
            problems += validate(images / "featureGraphic.png", (1024, 500))
            outputs.append(images / "featureGraphic.png")
            problems += validate(images / "icon.png", (512, 512), alpha_allowed=True)
    if problems:
        sys.exit("Store assets NOT ready:\n  " + "\n  ".join(problems))
    contact_sheet(outputs, ROOT / "store/preview.png")
    print("Store assets ready in fastlane/. Show store/preview.png to the user before any upload.")


if __name__ == "__main__":
    main()
```

Adjusting the look:
- Keep all layout numbers as fractions of the canvas width or height, so every
  target scales the same way.
- Text must stay readable at store thumbnail size (about 1/4 scale). Don't go
  below the default caption sizes, and don't add more than a title and a subtitle.
- The feature graphic keeps its important content in the middle 80%, because
  Play may crop the edges or overlay a play button when there is a promo video.
  Don't put screenshots or small text in it.
- A different style (dark background, device tilted, two screens per image) is
  fine as long as the section 1 rules hold and the validator passes.

## 7. Review with the user

1. Show `store/preview.png` and list what is in each folder: count per
   target and locale, plus the feature graphic and icon.
2. Say what you checked: PROD build, no personal data, policy words, validator
   passed.
3. Say which targets were skipped and why (e.g. "no iPad screenshots: the app
   is iPhone-only").
4. Apply caption or order changes, and run the compose script again (it is idempotent).

Also add the store images to the delivery screenshots
(`kmp-feature-scaffold` section 12) when the listing changes as part of a release.

## 8. Upload (only with the user's explicit go-ahead)

Uploading changes the public listing, so wait for the user to say so in this
task. A "looks good" on the preview is not an approval to publish. Credentials
come from the user or CI secrets, never from git or chat logs.

```bash
# Google Play: images only (service-account JSON from Play Console > Setup > API access)
fastlane supply --package_name com.touhidapps.kmp --json_key "$PLAY_JSON_KEY" \
  --skip_upload_apk --skip_upload_aab --skip_upload_metadata --skip_upload_changelogs

# App Store: screenshots only (App Store Connect API key)
fastlane deliver --api_key_path "$ASC_API_KEY_JSON" --app_identifier com.touhidapps.kmp \
  --skip_binary_upload --skip_metadata --skip_app_version_update --overwrite_screenshots
```
- Use the bundle ID and package name from `config/prod/env.properties`.
- Use a recent `fastlane`. If `deliver` doesn't recognize the 6.9" or 13" sizes,
  upload those images by hand in App Store Connect instead of resizing them to an
  older size.
- Play sends new graphics through review with the next listing change. Apple
  attaches screenshots to the version being prepared. Say both of these to the user.
- If the user prefers to upload by hand, give them the folder paths and stop there.
- App binaries and release notes go up with `play-console-release` and
  `app-store-connect-release`, which read the same `fastlane/metadata` folders.

## 9. Listing text (optional, if the user asks)

If the user asks for listing copy, write it originally into
`fastlane/metadata/android/<locale>/{title,short_description,full_description}.txt`
and `fastlane/metadata/<ios-locale>/{name,subtitle,keywords,description,promotional_text}.txt`,
within the limits:

| Play | Limit | App Store | Limit |
|---|---|---|---|
| title | 30 | name | 30 |
| short description | 80 | subtitle | 30 |
| full description | 4000 | keywords (comma-separated) | 100 |
| | | description | 4000 |
| | | promotional text | 170 |

The same section 1 rules apply: no rankings, prices, competitor names or
keyword stuffing, and every feature mentioned must exist in the build.

Also write the legal URLs for the App Store, one line each, from the PROD pages
of `web-admin-landing` 6d: `fastlane/metadata/<ios-locale>/privacy_url.txt`
(`https://abc.com/privacy`, `https://abc.com/bn/privacy` for Bangla) and
`support_url.txt`. Play takes the privacy URL in Play Console → App content, not
from these files (`play-console-release` 2). Never a `/uat` URL.

## 10. Checklist

- [ ] Ran after `app-graphics-generator`; brand colors and Play icon come from `branding/`
- [ ] Captured from the **PROD** build only (scripts refuse otherwise); no UAT badge, "(UAT)" title or debug UI
- [ ] Clean status bar (Android demo mode / `simctl status_bar`), demo account with invented data, no personal data
- [ ] 4–6 screens that tell the app's story, in the same order on both stores; no splash, login, empty or error screens
- [ ] Captions are original, in every listing locale, free of rankings, prices, store badges and claims; Bangla rendered with an OFL Bengali font and raqm
- [ ] Sizes: Play phone 1080×1920 (2–8), tablets if supported, feature graphic 1024×500, icon 512; App Store iPhone 6.9" 1320×2868 (1–10), iPad 13" 2064×2752 only if the app runs on iPad
- [ ] `compose_store_assets.py` validator passes (size, no alpha, file size, counts); `store/preview.png` shown to the user
- [ ] Assets in `fastlane/metadata/android/...` and `fastlane/screenshots/...`; raw captures git-ignored; no credentials committed
- [ ] Upload only after the user's explicit go-ahead; review timing explained
- [ ] File report lists store config, captions, scripts and generated assets (`kmp-feature-scaffold` section 13)
