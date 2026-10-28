---
name: kmp-security
description: Apply security to a KMP + CMP app by project type. HIGH tier (banking, remittance, payments, online transactions) mandates TLS pinning, root/jailbreak detection and encrypted storage; BASIC tier (social, money manager, editor, quiz) gets baseline security. Use when starting an app, adding auth, storage, networking, or reviewing security.
---

# Security by Project Type

The amount of security depends on what the app does. **Classify the app first,
then apply exactly the matching tier.** Under-protecting a payment app is
dangerous. Over-engineering a quiz app wastes time and hurts users (false root
alerts, blocked screenshots).

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
It follows the template's rules: interfaces in common code, platform implementations
in `src@android`/`src@ios`, and bindings in the app modules' `*KoinModules.kt`.
Related skills: `kmp-networking-ktor`, `kmp-supabase`, `kmp-environments`, `kmp-clean-architecture`.

## 0. Classify the project (always the first step)

| Tier | Project types | Examples |
|---|---|---|
| **HIGH** | Money moves, or regulated financial or identity data | banking, remittance / money transfer, mobile wallet, payment gateway, online transactions / checkout with stored cards, trading, loans, insurance claims, e-KYC |
| **BASIC** | No real money movement and no regulated data | social media, personal money manager / expense tracker (local notes only), image or video editor, quiz, news, notes, fitness log, catalog |

- Decide from the user's description. If the app is unclear or mixed (e.g. a
  social app with an in-app wallet, health records, a money manager that syncs
  bank accounts), **ask the user** before you build. A single feature that moves
  money makes the whole app **HIGH**.
- Record the decision in `docs/security-tier.md` (tier, reason, date). State it
  in your reply, so every later change follows it.
- Don't silently mix tiers. If the user wants a HIGH control in a BASIC app,
  add it on purpose and note it.

## 1. Where security code lives

```
core/security/                         # extra core module (copy core/preferences/module.yaml); depends on core/common only
  src/.../security/
    SecureStorage.kt                   # interface: put/get/remove/clear (secrets, tokens)
    DeviceIntegrity.kt                 # interface: check(): IntegrityResult  (HIGH)
    ScreenSecurity.kt                  # interface: protect(enabled)          (HIGH)
    BiometricAuth.kt                   # interface: authenticate(reason)      (HIGH, optional BASIC)
    SecurityPolicy.kt                  # data class from the tier: which controls are on
  src@android/...                      # Keystore, root checks, FLAG_SECURE, BiometricPrompt
  src@ios/...                          # Keychain, jailbreak checks, LocalAuthentication
  src@jvm/..., src@wasmJs/...          # limited / no-op implementations (see below)
```
- Features depend only on the **interfaces**. Bind the implementations in
  `app/androidApp`, `app/iosApp`, etc. (as the template does for `Analytics`).
- What goes where (`kmp-clean-architecture` section 2b): secrets, tokens,
  integrity, screen protection and biometrics in `core/security`; certificate
  pinning in `core/network` (it configures the engine); non-sensitive settings in
  `core/preferences`; the `TokenProvider` that reads `SecureStorage` is bound in
  `app/shared`, so `core/security` never depends on `core/network`. The
  Supabase session is stored by `SecureSessionManager`, also bound in
  `app/shared` (`kmp-supabase` section 5). Firebase App Check, if used, lives in
  `core/firebase`.
- Platform list: `app/shared` targets wasmJs. Either give `core/security` all
  platforms (with no-op jvm/wasm implementations), or bind it only in platform
  apps. For a **HIGH** app, don't offer money-moving flows on Web or Desktop
  unless the user explicitly accepts the weaker protection there. Browsers
  can't pin certificates or detect a compromised device.
- `SecurityPolicy` comes from the tier, so code asks the policy instead of
  hardcoding checks:
  ```kotlin
  data class SecurityPolicy(
      val tier: SecurityTier,
      val certificatePinning: Boolean,
      val deviceIntegrityCheck: Boolean,
      val blockScreenshots: Boolean,
      val biometricForSensitiveActions: Boolean,
      val sessionTimeoutMinutes: Int?,
  ) {
      companion object {
          val HIGH = SecurityPolicy(SecurityTier.HIGH, true, true, true, true, sessionTimeoutMinutes = 5)
          val BASIC = SecurityPolicy(SecurityTier.BASIC, false, false, false, false, sessionTimeoutMinutes = null)
      }
  }
  ```

## 2. BASIC tier (every app, including HIGH)

1. **HTTPS only.** Disable cleartext on Android (`android:usesCleartextTraffic="false"`
   or a network security config). Keep iOS ATS on, with no arbitrary loads.
2. **No secrets in the app.** No API secrets, signing keys or admin tokens in
   code, resources or `env.properties`. Keep real secrets on the server (for
   Supabase: edge function secrets). The app ships only the Supabase
   **publishable** key, never the secret / service-role key. Firebase and
   Supabase config stay git-ignored (`kmp-environments`).
2b. **Row Level Security on every Supabase table and bucket.** The publishable
   key is public, so RLS is the access control. A table without RLS is open to
   anyone. Prove it with pgTAP tests (`kmp-supabase` sections 7 and 13).
3. **Tokens in secure storage,** never in plain preferences or the database: use
   `SecureStorage` (this includes the Supabase session; replace supabase-kt's
   default settings-based `SessionManager`) (Keystore on Android, Keychain on iOS, section 3.3). Keep
   non-sensitive settings in `core/preferences` as today.
4. **No sensitive logs.** Ktor logging only in debug, with `Authorization`
   sanitized (`kmp-networking-ktor`). Never log tokens, passwords, OTPs or PII.
5. **Release hardening:** enable R8 minify and obfuscation for Android release
   (keep the template's `proguard-rules.pro` up to date for kotlinx.serialization
   and Koin). Disable debuggable in release.
6. **Backups:** exclude tokens and secure files from Android backup
   (`android:fullBackupContent` / `dataExtractionRules`). Keychain items use a
   `ThisDeviceOnly` accessibility.
7. **Input validation** in use cases (lengths, formats). Never build SQL or URLs
   by string concatenation. Use Room parameters and Ktor `parameter()`.
8. **Least permissions.** Request each permission only when the feature needs it.
9. **Password and OTP fields:** use `PasswordVisualTransformation()`,
   `KeyboardType.Password`/`NumberPassword`, and `autoCorrectEnabled = false`.
10. **Keep dependencies updated.** Don't add abandoned libraries. Check advisories
    for Ktor, supabase-kt, Coil and Firebase.
11. **Logout clears everything:** `supabase.auth.signOut()` (revokes the
    session on the server), clear tokens, `SecureStorage`, the in-memory cache,
    realtime channels, and Ktor `BearerAuthProvider.clearToken()`.

BASIC apps do **not** add pinning, root/jailbreak blocking or screenshot blocking.
These cause false positives and support load without matching risk.

## 3. HIGH tier (mandatory on top of BASIC)

### 3.1 SSL/TLS certificate pinning
Pin the server's public key (SPKI SHA-256) per environment, with at least **one
backup pin** (the next key, or the issuing intermediate). Put the pins in
`config/<env>/env.properties` (`PINS=sha256/AAA...,sha256/BBB...`), generated
into `AppEnvironment` by `scripts/use_env.py` (`kmp-environments`). When UAT is
path-based on the same host (`https://abc.com/uat/`), UAT and PROD share the host
and its pins. With a separate UAT host, each environment has its own pins.

Engine-specific configuration through an `expect` in `core/network`:
```kotlin
// core/network/src
expect fun createPlatformHttpClient(pinnedHost: String?, pins: List<String>, block: HttpClientConfig<*>.() -> Unit): HttpClient

// core/network/src@android (and src@jvm): OkHttp
actual fun createPlatformHttpClient(pinnedHost: String?, pins: List<String>, block: HttpClientConfig<*>.() -> Unit) =
    HttpClient(OkHttp) {
        engine {
            if (pinnedHost != null && pins.isNotEmpty()) config {
                certificatePinner(CertificatePinner.Builder().apply { pins.forEach { add(pinnedHost, it) } }.build())
            }
        }
        block()
    }

// core/network/src@ios: Darwin
actual fun createPlatformHttpClient(pinnedHost: String?, pins: List<String>, block: HttpClientConfig<*>.() -> Unit) =
    HttpClient(Darwin) {
        engine {
            if (pinnedHost != null && pins.isNotEmpty()) handleChallenge(
                io.ktor.client.engine.darwin.certificates.CertificatePinner.Builder()
                    .apply { pins.forEach { add(pinnedHost, it) } }.build()
            )
        }
        block()
    }

// core/network/src@wasmJs: the browser owns TLS. Pinning isn't possible, so no pins.
```
`createHttpClient()` calls `createPlatformHttpClient(...)` with pins only when
`SecurityPolicy.certificatePinning` is on, and applies the shared configuration in
`block`.

**Supabase uses the same engine.** Split the factory into
`createPlatformEngine(pins: Map<String, List<String>>)` (host → pins, built with
the same OkHttp / Darwin code as above) and pass that one `HttpClientEngine` to
both `createHttpClient` and `createAppSupabaseClient` (`kmp-supabase` section 3).
`*.supabase.co` certificates are issued and rotated by Supabase, so never pin
its leaf key. Either pin the issuing CA keys with backups (re-check the chain
with `openssl s_client -connect <ref>.supabase.co:443 -showcerts` before each
release), or keep money-moving calls on a backend host you control and pin that.
Ask the user which, and record it in `docs/security-tier.md`.
- A pin mismatch must fail the request (map it to a dedicated
  `NetworkError.SecurityError`) and show a `MyAlertDialog` of type `Error`.
  Never fall back to unpinned.
- Write down how to rotate pins: ship the new backup pin **before** the
  certificate changes. Remind the user that a wrong pin bricks networking for
  installed apps.
- Test with `MockEngine` for logic, and manually with a proxy (e.g. mitmproxy):
  requests must fail when intercepted.

### 3.2 Root / jailbreak / tamper detection
Client checks can be bypassed, so treat them as **signals**. Combine them with
**server-side attestation**: Play Integrity API on Android, App Attest /
DeviceCheck on iOS. Verify the attestation token **on the backend** (with
Supabase: in an edge function, which then calls a `security definer` function)
before sensitive operations.

```kotlin
// core/security/src
sealed interface IntegrityResult {
    data object Trusted : IntegrityResult
    data class Compromised(val reasons: List<String>) : IntegrityResult
}
interface DeviceIntegrity { suspend fun check(): IntegrityResult }
```
- **Android signals:** `su` binaries (`/system/bin/su`, `/system/xbin/su`,
  `/sbin/su`), Magisk paths and packages, `Build.TAGS` contains `test-keys`, a
  writable `/system`, an emulator fingerprint (in release), an attached debugger
  (`Debug.isDebuggerConnected()`), Frida/Xposed artifacts, and a signing
  certificate that doesn't match the release certificate.
- **iOS signals:** jailbreak files (`/Applications/Cydia.app`,
  `/Library/MobileSubstrate/MobileSubstrate.dylib`, `/bin/bash`, `/usr/sbin/sshd`,
  `/etc/apt`), the ability to write outside the sandbox (`/private/jb_test`),
  suspicious dylibs loaded (`_dyld_image_count`/`_dyld_get_image_name`), and a
  debugger attached (`sysctl` `P_TRACED`). Use `NSFileManager` and POSIX through
  Kotlin/Native interop.
- **Response policy** (decide with the user, and default to this):
  - Check at app start and again before each sensitive action.
  - If compromised: show a blocking `MyAlertDialog` (type `Error`, not
    dismissable) that explains the app can't run on a modified device, block
    login and transactions, and report the signal to the backend. Don't crash
    and don't silently wipe data.
  - Never run these checks in BASIC apps.

### 3.3 Local storage protection
- **Secrets and tokens:** use `SecureStorage`.
  - **Android:** an AES-256-GCM key in the Android Keystore (hardware-backed
    when available). Store the ciphertext with its IV. Don't use the deprecated
    `EncryptedSharedPreferences`.
  - **iOS:** the Keychain, with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.
    multiplatform-settings' `KeychainSettings`, or direct `Security` framework
    interop, both work.
  ```kotlin
  // core/security/src@android
  class AndroidSecureStorage(context: Context) : SecureStorage {
      private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)
      private val alias = "app_secure_key"

      private fun secretKey(): SecretKey {
          val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
          (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
          return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
              init(
                  KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                      .setKeySize(256)
                      .build()
              )
          }.generateKey()
      }

      override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
          val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
          val payload = cipher.iv + cipher.doFinal(value.encodeToByteArray())
          prefs.edit().putString(key, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
      }

      override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
          val payload = prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return@withContext null
          val cipher = Cipher.getInstance("AES/GCM/NoPadding")
              .apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, payload, 0, 12)) }
          cipher.doFinal(payload, 12, payload.size - 12).decodeToString()
      }

      override suspend fun remove(key: String) = withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }
      override suspend fun clear() = withContext(Dispatchers.IO) { prefs.edit().clear().apply() }
  }
  ```
- **Database:** don't store card numbers, PINs, full account numbers or OTPs
  locally at all. If sensitive records must be cached, encrypt the Room database
  (an SQLCipher-backed driver on Android/iOS, keyed from `SecureStorage`), or
  encrypt the sensitive columns with the Keystore/Keychain key. Tell the user
  which option you chose and what it costs (size, setup).
- **Cache and files:** no sensitive data in the HTTP cache, image cache or logs.
  Clear them on logout.
- Set `android:allowBackup="false"` for HIGH apps. The template currently has
  `true`.

### 3.4 Other mandatory HIGH controls
- **Screenshot and screen-recording protection** on sensitive screens (or the
  whole app): set `WindowManager.LayoutParams.FLAG_SECURE` on Android. On iOS,
  blur or cover the content when `UIApplication.userDidTakeScreenshotNotification` /
  `UIScreen.capturedDidChangeNotification` fire, and in the app switcher
  (`applicationWillResignActive`). Expose this through `ScreenSecurity`, driven by
  `SecurityPolicy.blockScreenshots`.
- **Biometric or PIN gate** for app open (after timeout) and for each sensitive
  action (send money, add payee, view card): `BiometricPrompt` (androidx.biometric)
  and `LocalAuthentication` (`LAContext`), with the app PIN as a fallback.
- **Session management:** auto-logout after `sessionTimeoutMinutes` of inactivity
  and on app background beyond the timeout, short-lived access tokens (Supabase
  JWT expiry 15 minutes, set per project), and a server-side session revoke on
  logout (`signOut()`, or `signOut(SignOutScope.GLOBAL)` to end every device).
- **Transaction safety (with the backend):** step-up OTP or biometric
  confirmation, idempotency keys on payment requests, server-side amount and
  payee validation, and replay protection. The client never decides
  authorization.
- **Clipboard:** don't allow copying OTPs or full account numbers. Clear any
  sensitive copy after a short delay.
- **Secure input:** PIN and OTP fields with `KeyboardType.NumberPassword`, no
  suggestions, and masking. Use a custom in-app PIN pad if the bank requires
  it (prevents third-party keyboards from logging input).
- **Obfuscation is mandatory** (R8 full mode) and the debuggable flag is off. On
  iOS, strip symbols in release.
- **Compliance pointer:** align with OWASP MASVS L2 (+R for resilience), and
  PCI DSS / local central-bank guidelines where they apply. Tell the user which
  items need backend or legal work outside the app.

## 4. Testing (TDD)

- Unit-test `SecurityPolicy` selection, the session timeout logic, the pin-mismatch
  → `NetworkError.SecurityError` mapping, the logout-clears-everything behavior,
  and the integrity response policy (Compromised → blocking state), using fakes
  for `DeviceIntegrity`, `SecureStorage` and `BiometricAuth`.
- Instrumented or manual checks for HIGH: interception with mitmproxy fails, a
  rooted emulator / jailbroken simulator shows the block dialog, screenshots
  are black or blurred, and tokens are unreadable in the app's data directory.
- Screenshots for the feature report: the block dialog, the biometric prompt
  and the session-timeout screen (`kmp-feature-scaffold` section 12).

## 5. Checklist

**Always**
- [ ] Tier decided (HIGH / BASIC), asked if unclear, recorded in `docs/security-tier.md`
- [ ] HTTPS only; no secrets in the app (Supabase publishable key only); tokens and the Supabase session in `SecureStorage`
- [ ] RLS on every Supabase table and bucket, proven by pgTAP tests
- [ ] No sensitive logs; R8 + not debuggable in release; backups exclude secrets
- [ ] Input validation in use cases; least permissions; logout clears everything

**HIGH only**
- [ ] TLS public-key pinning per environment, with a backup pin, on OkHttp and Darwin; mismatch → `SecurityError`, no fallback
- [ ] Root/jailbreak/debugger/tamper signals + server-verified Play Integrity / App Attest; blocking response policy
- [ ] Keystore/Keychain `SecureStorage`; no card/PIN/OTP at rest; DB or column encryption if needed; `allowBackup=false`
- [ ] Screenshot/recording protection; biometric/PIN gate; session timeout; clipboard and secure-input rules
- [ ] Web/Desktop money flows disabled or explicitly accepted by the user
- [ ] Backend items (attestation verification, OTP, idempotency, revoke) listed for the user

**BASIC only**
- [ ] No pinning, root blocking or screenshot blocking added unless the user asked
