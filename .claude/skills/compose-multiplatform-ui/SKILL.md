---
name: compose-multiplatform-ui
description: Build modern Material 3 UI in Compose Multiplatform with one common theme, with fonts, images, colors and components in core/ui and text through the Strings localization interface. Use when building or restyling screens, theme, resources or translations.
---

# Compose Multiplatform UI and Common Theme

Make every screen look modern, consistent and standard across Android, iOS,
Desktop and Web by building it from **one shared theme and component set** in
`core/ui`. Features never style themselves.

Reference layout:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template)
(`core/ui/theme`, `core/ui/customUI`, `AppScaffold`, `ScreenEnv`). For feature
structure, MVI, DI and navigation, follow the `kmp-feature-scaffold` skill.

## 1. Golden rules

1. **One source of truth.** All colors, typography, shapes, spacing and reusable
   components live in `core/ui`. A feature only reads `MaterialTheme.*` and
   `AppTheme.*` tokens, and uses `core/ui` components.
2. **Zero hardcoded styling in features.** No `Color(0xFF...)`, `Color.White`,
   `Color.Gray`, raw `fontSize = 15.sp`, or ad-hoc `RoundedCornerShape(12.dp)`.
   If a token is missing, add it to the theme first.
3. **Material 3 only.** `androidx.compose.material3.*`, never M2 `material.*`.
4. **Light and dark are both first-class.** Every color is a semantic role, so
   dark mode works without per-screen code.
5. **Same look on every platform.** Branch on window size, never on platform, for
   layout. Platform code is only for things like dynamic color and system bars.

## 2. Theme structure in core/ui

Everything visual lives in **one place**, `core/ui`. Use the template's file names:

```
core/ui/
  composeResources/
    drawable/                // ALL images and icons for the whole app
    font/                    // ALL fonts (e.g. solaiman_lipi.ttf)
  src/com/<org>/ui/theme/
    AppColorsMaterial.kt     // raw palette (Red50..Red900, Blue..., white, black)
    AppColor.kt              // extra brand/custom raw colors
    AppColorScheme.kt        // full M3 light + dark ColorScheme built from the palette
    ExtendedColors.kt        // success / warning roles M3 doesn't have
    AppFont.kt               // FontFamily + full Typography (myFontFamily(), myTypography())
    Type.kt                  // (or merge into AppFont.kt; don't leave it empty)
    Shape.kt                 // M3 Shapes scale
    Spacing.kt               // spacing + size tokens
    Resources.kt             // the ONLY gateway to drawables and fonts (section 2.0)
    AppTheme.kt              // AppTheme { } + AppTheme.spacing / .extendedColors
    AppScaffold.kt, NavigationDrawerContent.kt
  src/com/<org>/ui/customUI/ // shared components (section 4)
  src/com/<org>/ui/LocalizationManager.kt   // Language -> Strings (section 2.6)
  src@android/.../theme/DynamicColor.android.kt   // optional Material You
core/common/src/com/<org>/common/localization/
  Strings.kt, StringsEn.kt, StringsBn.kt, Language.kt     // localization interface (section 2.6)
```

When improving the existing template, complete the partial pieces: color schemes
that set only `primary*`, an empty `Type.kt`, `Shape.kt` with two loose values,
and hardcoded gradients or `Color.White` in `AppScaffold`.

### 2.0 Resources: one gateway object

All drawables and fonts sit in `core/ui/composeResources`. Code reaches them
only through `Resources` in `core/ui/theme/Resources.kt`, never through `Res.*`
directly:

```kotlin
class Resources {
    object Drawable {
        val AppLogo = Res.drawable.app_logo
        val IcLanguage = Res.drawable.ic_language
        val FlagBd = Res.drawable.flag_bd
        val FlagEn = Res.drawable.flag_en
        // add new images here
    }
    object Fonts {
        val SolaimanLipi = Res.font.solaiman_lipi
        // add new fonts here
    }
}

// In a feature:
Image(painterResource(Resources.Drawable.AppLogo), contentDescription = null)
```

To add an image or font:
1. Put the file in `core/ui/composeResources/drawable/` or `font/` (snake_case name).
2. Add one line to `Resources.Drawable` or `Resources.Fonts`.
3. Use `Resources.Drawable.X` from the feature.

Rules:
- Features don't keep their own fonts, colors, or shared images. If a feature
  module has a `composeResources/` folder, move what is in it to `core/ui`.
- Fonts are used only inside the theme (`AppFont.kt` → `Typography`). Screens
  pick a `MaterialTheme.typography` role, never a `FontFamily`.
- Raw palette colors (`Red500`, `Blue900`...) are used only inside
  `AppColorScheme.kt`/`ExtendedColors.kt`. Screens use `MaterialTheme.colorScheme.*`.
- Text is never a resource file (`strings.xml`). It goes through the `Strings`
  interface (section 2.6).

### 2.0.1 No copyrighted images or content

Every asset and every piece of text you add must be free to ship in a commercial app.
- **Icons:** use Material Symbols / `material-icons-extended` (Apache 2.0)
  first. For anything else, draw a simple vector yourself, or use another
  clearly open-licensed set (e.g. Apache 2.0, MIT, CC0) and record its license.
- **Images and illustrations:** never download, copy or hotlink images from
  websites, Google Images, stock sites, apps, brands or games. Use the user's own
  assets, simple self-made vectors and shapes, gradients from theme colors, or
  CC0 / public-domain sources. If an image is needed and none is provided, add a
  neutral placeholder (an icon on a tonal `Box`) and tell the user it needs a
  real asset.
- **Fonts:** only fonts with an open license that allows bundling
  (SIL OFL, Apache 2.0), e.g. Google Fonts families. Note the license for any
  new font. Never bundle commercial fonts.
- **Text:** write original UI copy. Don't paste copyrighted text (articles,
  lyrics, book or movie quotes, other apps' copy or terms) into `StringsXx`. Use
  neutral sample data in previews and demo data (e.g. "Sample item 1").
- **Brands and logos:** don't use third-party logos, trademarks or brand names
  in UI or assets, unless the user supplies them and has the rights (e.g. their
  own logo, official "Sign in with ..." buttons used under the provider's
  guidelines).
- **Image URLs in demo data:** use placeholders the user controls, or none.
  Don't use links to random copyrighted images.
- When unsure about a license, don't add the asset. Use a placeholder and ask the user.
- If the project has a third-party licenses screen (`feature/more`, or
  `feature/about` in the template), add any new open-licensed asset or library there.

### 2.0.2 Generate original vector images, backgrounds and icons

When a screen needs an icon, illustration or background that Material Icons don't
cover and the user hasn't supplied one, **create it yourself as a vector**. Never
trace or imitate a copyrighted logo, character or artwork.

**Pick the right form**

| Need | Make it as | Where |
|---|---|---|
| Single-color icon | Android vector XML, 24×24 viewport, black fill, tinted at use | `core/ui/composeResources/drawable/ic_<name>.xml` |
| Multi-color illustration (empty state, onboarding, error) | `ImageVector` built in Kotlin with **theme colors** passed in, so it adapts to dark mode | `core/ui/.../customUI/illustration/<Name>Illustration.kt` |
| Screen or header background | a composable using `Brush` gradients + `Canvas` shapes from `colorScheme` | `core/ui/.../customUI/background/<Name>Background.kt` |
| App logo, launcher icons, splash | one `branding/logo/mark.svg` rendered to every platform | follow the `app-graphics-generator` skill |

Use Android vector XML (not SVG) in `composeResources`. Compose Multiplatform
renders it on every target. Register every new drawable in
`Resources.Drawable`. Prefer vectors to PNG/JPG, and never embed raster data in
a vector.

**Icon rules (match Material Rounded)**
- 24×24 viewport, with content inside the 20×20 live area (2 dp padding).
- Use rounded corners and caps, and keep stroke weight consistent with Material
  Rounded (about 2 dp).
- One path color `#FF000000`. Color comes from `tint = MaterialTheme.colorScheme.*`
  at the call site.
- Name files `ic_<meaning>.xml` (e.g. `ic_bus_ticket.xml`), not `ic_1.xml`.

```xml
<!-- core/ui/composeResources/drawable/ic_ticket.xml : original, self-drawn -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M4,6h16a2,2 0,0 1,2 2v2a2,2 0,0 0,0 4v2a2,2 0,0 1,-2 2H4a2,2 0,0 1,-2 -2v-2a2,2 0,0 0,0 -4V8A2,2 0,0 1,4 6z"/>
</vector>
```
```kotlin
Icon(painterResource(Resources.Drawable.IcTicket), contentDescription = env.strings.ticket,
     tint = MaterialTheme.colorScheme.primary)
```

**Illustration rules**
- Use a flat, geometric style with 3–4 colors taken from the theme (`primary`,
  `primaryContainer`, `secondaryContainer`, `surfaceVariant`) and no fine
  detail. Make it look intentional at 120–240 dp.
- Build it as a function that takes colors, so light and dark both look right:
```kotlin
@Composable
fun EmptyBoxIllustration(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val image = remember(c) {
        ImageVector.Builder("EmptyBox", 160.dp, 160.dp, 160f, 160f).apply {
            path(fill = SolidColor(c.surfaceVariant)) {            // soft backdrop circle
                moveTo(80f, 8f); arcToRelative(72f, 72f, 0f, true, true, 0f, 144f)
                arcToRelative(72f, 72f, 0f, true, true, 0f, -144f); close()
            }
            path(fill = SolidColor(c.primaryContainer)) {          // box body
                moveTo(40f, 70f); lineTo(120f, 70f); lineTo(120f, 120f); lineTo(40f, 120f); close()
            }
            path(fill = SolidColor(c.primary)) {                   // open lid
                moveTo(36f, 58f); lineTo(80f, 70f); lineTo(124f, 58f); lineTo(80f, 46f); close()
            }
        }.build()
    }
    Image(image, contentDescription = null, modifier = modifier)
}
```

**Background rules**
- Draw backgrounds in code from theme colors, not from bitmaps
  (the template's `bg.png` should become a composable like this):
```kotlin
@Composable
fun AppHeaderBackground(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(c.primaryContainer, c.surface)))
        drawCircle(c.primary.copy(alpha = 0.08f), radius = size.minDimension * 0.6f,
                   center = Offset(size.width * 0.9f, 0f))
        drawCircle(c.tertiary.copy(alpha = 0.06f), radius = size.minDimension * 0.4f,
                   center = Offset(0f, size.height))
    }
}
```
- Keep text contrast. Put content on `surface`-based areas, and keep decorative
  shapes at low alpha.

**Quality checks for anything generated**
- XML is well-formed, `pathData` is valid, and the file size is small (a few KB).
  Open a `@Preview` of it in light and dark.
- It reads clearly at the smallest size it is used at (24 dp for icons).
- Give decorative illustrations and backgrounds `contentDescription = null`.
  Meaningful icons get a `Strings` description.
- Tell the user which assets you generated, so they can replace them with
  brand art later.

### 2.1 Colors: a complete scheme from one brand seed

Generate the palette from one brand color with the Material Theme Builder
(or `material-color-utilities`), and fill **every** role for light and dark.
A scheme that sets only `primary` leaves surfaces, containers and outlines on M3
defaults, which looks unfinished.

```kotlin
// AppColorsMaterial.kt / AppColor.kt: raw tones, used only by the theme
internal val Brand40 = Color(0xFF3557C8)
internal val Brand80 = Color(0xFFB8C4FF)
// ... the full tonal set produced by the builder

// AppColorScheme.kt
internal val LightColors = lightColorScheme(
    primary = Brand40, onPrimary = Color.White,
    primaryContainer = Brand90, onPrimaryContainer = Brand10,
    secondary = ..., onSecondary = ..., secondaryContainer = ..., onSecondaryContainer = ...,
    tertiary = ..., onTertiary = ..., tertiaryContainer = ..., onTertiaryContainer = ...,
    error = ..., onError = ..., errorContainer = ..., onErrorContainer = ...,
    background = ..., onBackground = ...,
    surface = ..., onSurface = ..., surfaceVariant = ..., onSurfaceVariant = ...,
    surfaceContainerLowest = ..., surfaceContainerLow = ..., surfaceContainer = ...,
    surfaceContainerHigh = ..., surfaceContainerHighest = ...,
    outline = ..., outlineVariant = ..., inverseSurface = ..., inverseOnSurface = ...,
    inversePrimary = ..., scrim = ...,
)
internal val DarkColors = darkColorScheme(/* same roles, dark tones */)
```

Extra semantic colors go through a `CompositionLocal`, not loose `val`s:
```kotlin
@Immutable
data class ExtendedColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
)
internal val LightExtendedColors = ExtendedColors(/* ... */)
internal val DarkExtendedColors = ExtendedColors(/* ... */)
internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
```

### 2.2 Typography: the full scale with the app font
```kotlin
@Composable
internal fun appTypography(): Typography {
    val font = FontFamily(
        Font(Resources.Fonts.AppRegular, FontWeight.Normal),
        Font(Resources.Fonts.AppMedium, FontWeight.Medium),
        Font(Resources.Fonts.AppBold, FontWeight.Bold),
    )
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = font),
        // ... every style: display, headline, title, body, label (L/M/S)
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.Medium),
    )
}
```
- Keep M3's default sizes and line heights unless the design says otherwise.
- If the app supports a script like Bangla, make sure the font covers it
  (the template ships `solaiman_lipi`). Test long translated strings.
- Use roles by meaning: `headlineSmall` for screen headers, `titleMedium` for
  card titles, `bodyMedium` for content, `labelLarge` for buttons and chips,
  `labelSmall` for metadata.

### 2.3 Shapes
```kotlin
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // chips, small badges
    small = RoundedCornerShape(8.dp),        // text fields, small cards
    medium = RoundedCornerShape(12.dp),      // cards, dialogs content
    large = RoundedCornerShape(16.dp),       // sheets, large cards
    extraLarge = RoundedCornerShape(28.dp),  // dialogs, FAB-sized surfaces
)
```
Use `MaterialTheme.shapes.medium` etc. in components, never inline shapes.

### 2.4 Spacing and sizes (8 dp grid)
```kotlin
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp, val xs: Dp = 4.dp, val sm: Dp = 8.dp, val md: Dp = 12.dp,
    val lg: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val xxxl: Dp = 48.dp,
    val screenPadding: Dp = 16.dp,
    val minTouchTarget: Dp = 48.dp,
    val maxContentWidth: Dp = 600.dp,     // readable width on tablets, desktop and web
)
internal val LocalSpacing = staticCompositionLocalOf { Spacing() }
```

### 2.5 AppTheme: one entry point, wired to the user's setting
```kotlin
enum class ThemeMode { System, Light, Dark }

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,          // Material You on Android 12+, if the brand allows it
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = (if (dynamicColor) platformDynamicColorScheme(dark) else null)
        ?: if (dark) DarkColors else LightColors

    CompositionLocalProvider(
        LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}

object AppTheme {
    val spacing: Spacing @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val extendedColors: ExtendedColors @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
}

// src: common
@Composable internal expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?
// src@android: dynamicDark/LightColorScheme(LocalContext.current) on API 31+, else null
// src@ios, src@jvm, src@wasmJs: null
```
- Read the theme mode from `core/preferences` (e.g. a `darkMode`/`themeMode` flow)
  at the app root (`app/shared`), and pass it into `AppTheme`. `core/ui` itself
  never depends on `core/preferences`. A dark-mode toggle in settings
  must actually change `AppTheme`. Verify this end to end.
- Wrap the app **once**, at the root (`App.kt` / `MainNavigation`). Never call
  `MaterialTheme(...)` or `AppTheme` inside a feature.
- Store the mode as an enum (`System/Light/Dark`), not as a magic index.

### 2.6 Localization through the Strings interface

Follow the template: an interface in `core/common`, one `object` per language,
and a manager in `core/ui`.

```kotlin
// core/common/.../localization/Strings.kt
interface Strings {
    val appName: String
    val settings: String
    val darkMode: String
    fun totalItems(count: Int): String          // parameterized text is a function
}

// core/common/.../localization/StringsEn.kt
object StringsEn : Strings {
    override val appName: String get() = "KMP Template MultiModule"
    override val settings: String get() = "Settings"
    override val darkMode: String get() = "Dark mode"
    override fun totalItems(count: Int) = "Total items: $count"
}

// core/common/.../localization/StringsBn.kt
object StringsBn : Strings { /* same members, Bangla text */ }

// core/common/.../localization/Language.kt
enum class Language { EN, BN }

// core/ui/.../LocalizationManager.kt
object LocalizationManager {
    var currentLanguage by mutableStateOf(Language.EN)
        private set
    private val localizedStrings = mapOf(Language.EN to StringsEn, Language.BN to StringsBn)
    val strings: Strings @Composable get() = localizedStrings[currentLanguage] ?: StringsEn
    fun updateLanguage(language: Language) { currentLanguage = language }
}
```

Using strings in a screen:
```kotlin
val env = rememberScreenEnv()
Text(env.strings.settings)
Text(env.strings.totalItems(state.items.size).localizeNumber())   // Bangla digits when BN
```

Rules:
- **Add a string:** add the member to `Strings`, then implement it in **every**
  `StringsXx` object. The compiler fails if one is missing, which is the point of
  the interface. Never add a default implementation in the interface.
- **Add a language:** add an enum entry to `Language`, create `StringsXx : Strings`,
  register it in `LocalizationManager.localizedStrings`, and add its flag and
  label to the language screen. Check that the app font covers the script.
- **Change language:** the language feature persists the choice in
  `core/preferences` and calls `LocalizationManager.updateLanguage(...)`. `App.kt`
  restores it at startup. Because `currentLanguage` is Compose state, the whole UI
  recomposes. Don't restart the app.
- **No hardcoded user-facing text** anywhere: screens, dialogs,
  `contentDescription`, titles in `titleChangeByRoute`, snackbars.
- **ViewModels don't read `Strings`.** It is `@Composable` and UI-only. A
  ViewModel exposes typed values (an error type, an enum, a count), and the
  screen turns them into text. For example, map `NetworkError` to
  `env.strings.noInternet` and so on in the UI, instead of showing the English
  `errMessage`.
- Use `localizeNumber()` for any number shown to the user.
- Keep member names descriptive and grouped by screen
  (`settingsTitle`, `settingsDarkMode`) as the list grows.

## 3. Modern, standard design guidelines

**Layout and spacing**
- Use a screen edge padding of `spacing.screenPadding` (16 dp), `lg` between
  sections, and `sm`/`md` inside cards. Stay on the 4/8 dp grid.
- On wide windows, center content with `Modifier.widthIn(max = spacing.maxContentWidth)`,
  or switch to two panes (`TwoPaneScene` / M3 adaptive). Never stretch a form
  across a 1400 dp desktop window.
- Draw edge to edge. Call `enableEdgeToEdge()` on Android, and respect insets on
  every platform with `Scaffold` padding, `WindowInsets.safeDrawing` and
  `imePadding()`.

**Surfaces and depth**
- Separate layers with tonal surfaces (`surfaceContainerLow` → `surfaceContainerHigh`),
  not heavy shadows or gradients. Prefer `Card` with default elevation, or an
  `OutlinedCard` for dense lists.
- The top app bar uses theme colors (`TopAppBarDefaults.topAppBarColors()` or
  `surface`/`surfaceContainer`). Don't use hardcoded gray gradients or white text.
  For long screens, use `LargeTopAppBar`/`MediumTopAppBar` with
  `exitUntilCollapsedScrollBehavior`.

**Hierarchy and content**
- Give each screen one primary action (`Button` or FAB). Secondary actions use
  `FilledTonalButton`/`OutlinedButton`, and tertiary actions use `TextButton`.
- Use `ListItem` for settings and menu rows, with leading icons from
  `Icons.Rounded`/`Outlined`, using **one icon style app-wide**.
- Truncate with `maxLines` + `TextOverflow.Ellipsis`. Never let text overlap or
  get clipped at a large font scale.

**Every data screen has four states.** Render them with shared components:
```kotlin
when {
    state.isLoading && state.items.isEmpty() -> AppLoading()          // or skeleton placeholders
    state.errorMsg != null && state.items.isEmpty() ->
        AppErrorState(message = state.errorMsg, onRetry = { onEvent(Events.Retry) })
    state.items.isEmpty() -> AppEmptyState(title = env.strings.noItems, icon = Icons.Outlined.Inbox)
    else -> Content(state.items)
}
```
Use `PullToRefreshBox` for refreshable lists. Show transient feedback through a
snackbar driven by an MVI `Action`, not through a dialog.

**Motion**
- Add small, purposeful motion: `AnimatedContent` between the four states,
  `animateContentSize()` for expanding cards, `animateItem()` in lazy lists, and
  Navigation3 transitions for screens. Keep durations about 150–300 ms. Avoid
  1000 ms slides.

**Accessibility (part of "standard")**
- Keep touch targets at least 48 dp. Give icon-only buttons a
  `contentDescription` from `Strings`, and decorative images `null`.
- Use only theme color pairs (`onX` on `X`), so contrast holds in both themes.
- Check the screen at 200% font scale and in RTL if the app is localized.

## 4. Shared component set (core/ui/components)

Ad slots are not here: `AdBanner` lives in `core/ads`, and a screen places it
like any other composable. Everything else shared and visual lives in `core/ui`.

Build these once and use them everywhere. Each takes `modifier: Modifier = Modifier`
as its first optional parameter, reads only theme tokens, and has light and dark
`@Preview`s.

| Component | Wraps / purpose |
|---|---|
| `AppScaffold` | `Scaffold` + top bar + snackbar host + drawer, using theme colors |
| `AppTopBar` | `TopAppBar` with a back or menu icon and a scroll behavior |
| `AppButton` / `AppTonalButton` / `AppTextButton` | M3 buttons with loading state and full-width option |
| `AppTextField` | `OutlinedTextField` with label, error text, `singleLine`, and IME action defaults (see the keyboard section of `kmp-feature-scaffold`) |
| `AppCard` | `Card` with standard padding and shape |
| `AppListItem` | `ListItem` for settings and menu rows |
| `AppLoading` / skeleton | centered progress, or shimmer placeholders |
| `AppEmptyState` / `AppErrorState` | icon + title + message + optional action |
| `MyAlertDialog` | custom themed dialog with type icon, tonal header and stacked buttons (section 4.1) |
| `AppAsyncImage` | Coil `SubcomposeAsyncImage` with placeholder and error (the template's `MyAsyncImage`) |
| `Modifier.clearFocusOnTapOutside()` | hides the keyboard on outside tap |
| `AppVersionText` | "v1.1.1 (UAT)" / "v1.1.1" at the drawer bottom or on the About page (section 4.2) |

Example:
```kotlin
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = colors,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.xl, vertical = AppTheme.spacing.md),
        modifier = modifier.heightIn(min = AppTheme.spacing.minTouchTarget),
    ) {
        AnimatedContent(targetState = isLoading, label = "AppButtonLoading") { loading ->
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
```
### 4.1 Alert dialog: a custom themed design, not the default look

The app uses **one** dialog component, `MyAlertDialog` in `core/ui/customUI/MyAlert.kt`
(keep the template's name). Never use a raw `AlertDialog` or a bare `Dialog` in a
feature. Upgrade the template version: it paints `Color.White` (breaks dark mode),
ignores its `okText`/`cancelText` parameters with hardcoded `"Cancel"`/`"Continue"`,
and sets `fontFamily` on every `Text` instead of relying on the theme.

**Design spec**
- Container: `Surface` with `MaterialTheme.shapes.extraLarge` (28 dp),
  `color = colorScheme.surfaceContainerHigh`, `tonalElevation = 6.dp`, inner
  padding `spacing.xl`, and `widthIn(min = 280.dp, max = 400.dp)`, so it is never
  full-width on tablets, desktop or web.
- Header: a 56–64 dp circle with a tonal background holding the type icon,
  centered. Below it, a centered title in `headlineSmall`, then a centered message
  in `bodyMedium` with the `onSurfaceVariant` color, then an optional custom
  content slot (a text field, a list, a checkbox).
- Types set the icon and accent through theme roles only:

  | `AlertType` | Icon (Rounded) | Circle bg / icon tint | Confirm button |
  |---|---|---|---|
  | `Info` | `Info` | `primaryContainer` / `onPrimaryContainer` | filled `primary` |
  | `Success` | `CheckCircle` | `extendedColors.successContainer` / `onSuccessContainer` | filled `success` |
  | `Warning` | `WarningAmber` | `extendedColors.warningContainer` / `onWarningContainer` | filled `primary` |
  | `Error` | `ErrorOutline` | `errorContainer` / `onErrorContainer` | filled `error` |
  | `Destructive` (delete, logout) | `DeleteOutline` | `errorContainer` / `onErrorContainer` | filled `error` |

- Buttons: full width and stacked, with the confirm button (filled, `AppButton`
  style, 48 dp min height) **above** the dismiss button (`OutlinedButton` or
  `TextButton`), separated by `spacing.sm`. On widths above about 360 dp they can
  sit side by side with equal weight. A single-button dialog shows only the
  confirm button.
- The confirm button supports `isLoading` (spinner, both buttons disabled) for
  dialogs that run an action.
- Motion: scale from 0.9 to 1 plus a fade in over about 200 ms. Use a scrim from
  the theme (`colorScheme.scrim` at about 32% alpha).
- Behavior: `dismissOnClickOutside`/`dismissOnBackPress` are parameters. They
  default to `true`, and must be `false` for destructive or blocking dialogs and
  while loading.
- All text comes from `env.strings` (`strings.ok`, `strings.cancel`,
  `strings.delete`...). The component itself has no default English text.

**API**
```kotlin
enum class AlertType { Info, Success, Warning, Error, Destructive }

@Composable
fun MyAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    type: AlertType = AlertType.Info,
    dismissText: String? = null,            // null = single-button dialog
    isLoading: Boolean = false,
    dismissOnClickOutside: Boolean = type != AlertType.Destructive,
    icon: ImageVector? = null,               // override the type's icon
    content: (@Composable ColumnScope.() -> Unit)? = null,
)
```

**Skeleton**
```kotlin
Dialog(
    onDismissRequest = { if (!isLoading) onDismiss() },
    properties = DialogProperties(
        dismissOnClickOutside = dismissOnClickOutside && !isLoading,
        dismissOnBackPress = !isLoading,
        usePlatformDefaultWidth = false,
    ),
) {
    val visible = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f)) {
        Surface(
            modifier = modifier.padding(AppTheme.spacing.xl).widthIn(min = 280.dp, max = 400.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
            ) {
                val style = alertStyle(type)          // icon + container/content/confirm colors from theme roles
                Box(
                    Modifier.size(64.dp).background(style.iconContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon ?: style.icon, contentDescription = null, tint = style.iconTint, modifier = Modifier.size(32.dp)) }

                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                content?.invoke(this)

                Spacer(Modifier.height(AppTheme.spacing.sm))
                AppButton(
                    text = confirmText, onClick = onConfirm, isLoading = isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = style.confirmContainer, contentColor = style.onConfirm),
                    modifier = Modifier.fillMaxWidth(),
                )
                dismissText?.let {
                    OutlinedButton(onClick = onDismiss, enabled = !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(it, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
```

**Showing a dialog the MVI way**
- If the dialog must survive recomposition and rotation (a confirm delete or a
  blocking error), keep it in `State`, e.g. `val dialog: DialogUi? = null`.
  Show it with `state.dialog?.let { MyAlertDialog(...) }`. Confirm and dismiss
  send `Events`, and the ViewModel clears it.
- For fire-and-forget feedback, prefer a snackbar (`Action`) over a dialog. Use a
  dialog only when the user must decide or acknowledge something.
- Never stack two dialogs. The next one replaces the current one in state.

**Previews:** one per `AlertType`, in light and dark, plus a long-text case and a
loading case.

Before creating a component, check whether `core/ui` already has one (e.g.
`ProgressUi`, `ErrorMessageWithIcon`, `MyAlertDialog`, `MyAsyncImage`). Extend or
rename it rather than duplicating it.

### 4.2 App version label

Show the app version at the **bottom of the navigation drawer** if the app has
one, otherwise on the **About page**. If both exist, show it in both. UAT
builds read `v1.1.1 (UAT)`, and PROD reads plain `v1.1.1`. The text is
`AppEnvironment.versionLabel` (`kmp-environments` section 5b). Never build it in
the screen.

```kotlin
// core/ui/components/AppVersionText.kt
@Composable
fun AppVersionText(
    versionLabel: String,                         // env.versionLabel
    modifier: Modifier = Modifier,
) {
    Text(
        text = versionLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(AppTheme.spacing.md)
            .semantics { contentDescription = versionLabel },
    )
}

// NavigationDrawerContent.kt: pin it to the bottom, below the scrolling items
ModalDrawerSheet {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { /* drawer items */ }
    HorizontalDivider()
    AppVersionText(LocalAppEnvironment.current.versionLabel, Modifier.navigationBarsPadding())
}
```
On the About page, put it under the app name and logo, or as the last
`AppListItem`. Previews cover both the UAT and the PROD label.

## 5. Writing a screen

1. Use the stateful `FooScreen(contract)` → stateless `FooContent(state, onEvent)`
   split from `kmp-feature-scaffold`.
2. Lay out with `AppScaffold` padding (or the scaffold the app root already
   provides), `spacing.screenPadding`, and a `LazyColumn` with
   `contentPadding` + `verticalArrangement = Arrangement.spacedBy(spacing.md)`.
3. Handle all four states with the shared components.
4. Build only from `core/ui` components and M3 primitives that read theme tokens.
5. Add previews for `FooContent`, wrapped in `AppTheme`, in light and dark:
   ```kotlin
   @Preview @Composable
   private fun FooContentPreview() = AppTheme(themeMode = ThemeMode.Light) {
       FooContent(state = FooState(items = sampleItems), onEvent = {})
   }
   @Preview @Composable
   private fun FooContentDarkPreview() = AppTheme(themeMode = ThemeMode.Dark) {
       FooContent(state = FooState(items = sampleItems), onEvent = {})
   }
   ```

## 5b. UI tests (test-driven, like the rest)

Follow the TDD flow from `kmp-feature-scaffold` (section 10). For UI, write the
test for the stateless `FooContent` **before** building it, using the
multiplatform Compose test API in `test/`:

```kotlin
@OptIn(ExperimentalTestApi::class)
class ProfileContentTest {
    @Test
    fun showsErrorStateWithRetryAndSendsRetryEvent() = runComposeUiTest {
        var sent: ProfileEvents? = null
        setContent {
            AppTheme {
                ProfileContent(state = ProfileState(errorMsg = "x"), onEvent = { sent = it })
            }
        }
        onNodeWithTag("retry").assertIsDisplayed().performClick()
        assertEquals(ProfileEvents.Retry, sent)
    }
}
```
- Test each of the four states (loading, empty, error, content) and the key
  interactions (clicks send the right `Event`, IME `Done` submits).
- Find nodes with `Modifier.testTag("...")` or with semantics, not with
  hardcoded display text. Text changes with `Strings` and language.
- Shared components in `core/ui` (`MyAlertDialog`, `AppButton`, `AppTextField`)
  get their own tests: types render the right icon, loading disables buttons, and
  dismissal rules hold.
- Add `$compose.uiTest` (or the catalog's `compose-ui-test` entry) under
  `test-dependencies:`.

## 6. Review checklist

- [ ] No hardcoded colors, font sizes, shapes or dp spacing in the feature (grep for `Color(0x`, `Color.White`, `.sp`, `RoundedCornerShape(`)
- [ ] Images and fonts only in `core/ui/composeResources`, accessed only via `Resources.Drawable` / `Resources.Fonts`
- [ ] All text via the `Strings` interface; new strings implemented in every `StringsXx`; numbers use `localizeNumber()`
- [ ] No copyrighted images, fonts, text, logos or image links; only own, Material Icons, OFL/Apache/CC0 assets, or placeholders
- [ ] Missing icons, illustrations and backgrounds generated as original vectors (XML icons, theme-colored `ImageVector`s, `Canvas` backgrounds), registered in `Resources.Drawable`, previewed in light and dark
- [ ] No `Strings` access in ViewModels
- [ ] Dialogs use `MyAlertDialog` only (themed, typed, no raw `AlertDialog`, no `Color.White`); dialog state lives in MVI `State`
- [ ] Colors are M3 roles or `AppTheme.extendedColors`; full light and dark schemes exist
- [ ] Typography uses roles (`titleMedium`, `bodyMedium`...), not custom `TextStyle`s
- [ ] Spacing from `AppTheme.spacing`; content width capped on large windows
- [ ] Loading / empty / error / content states all handled with shared components
- [ ] Edge to edge with insets handled; `imePadding()` on forms
- [ ] Touch targets ≥ 48 dp; content descriptions; works at 200% font scale
- [ ] App version shown via `AppVersionText` at the drawer bottom (or About page): `v1.1.1 (UAT)` in UAT, `v1.1.1` in PROD
- [ ] The dark-mode setting actually switches `AppTheme`
- [ ] Light and dark previews for every new content composable
- [ ] UI tests written first for `FooContent` states and interactions, plus shared component tests
- [ ] File report given: added / changed / deleted files grouped by module (`kmp-feature-scaffold` section 13), including generated assets
- [ ] Screenshots of every state (light + dark, phone + wide) and a short flow recording delivered to the user. The commands and the headless `ImageComposeScene` fallback are in `kmp-feature-scaffold` section 12.
- [ ] Ran at least one Android and one non-Android target (desktop or iOS) and compared
