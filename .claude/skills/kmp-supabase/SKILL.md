---
name: kmp-supabase
description: Use Supabase (supabase-kt) as the backend of a KMP + CMP app (KMP-MultiModule-Template / Amper / Koin) for auth, Postgres database, storage, realtime and edge functions, with Firebase kept only for its free products (Analytics, Crashlytics, App Distribution, FCM, Remote Config). Use when adding login, remote data, file uploads, live updates, server logic, or deciding between Supabase and Firebase.
---

# Supabase as the App Backend (KMP + CMP)

The app uses **Supabase** for everything that stores or serves user data, and
**Firebase only for its free products**. One shared `SupabaseClient` lives in
`core/supabase`. Features reach it only from their `data` layer, wrap every call
in `safeSupabaseCall`, and return `ResultNet<T, NetworkError>` like any other
remote call.

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template).
Library: [supabase-kt](https://github.com/supabase-community/supabase-kt)
(Kotlin Multiplatform, built on Ktor, so it runs on android, iOS, jvm and
wasmJs). Related skills: layering in `kmp-clean-architecture`, the shared Ktor
engine and `ResultNet` in `kmp-networking-ktor`, UAT/PROD projects in
`kmp-environments`, session storage and pinning in `kmp-security`, events and
crash reports in `kmp-analytics-logging`, the feature workflow (TDD,
screenshots, file report) in `kmp-feature-scaffold`.

## 0. Which service does what

| Need | Use | Why |
|---|---|---|
| Sign up, login, OTP, Google/Apple sign-in, sessions | **Supabase Auth** | free up to 50k monthly active users, JWTs that Postgres RLS understands |
| Remote data (profiles, orders, posts, chat) | **Supabase Postgres** (PostgREST) | relational, SQL migrations, Row Level Security |
| Images, avatars, documents | **Supabase Storage** | buckets with RLS policies, signed URLs |
| Live updates (chat, order status) | **Supabase Realtime** | Postgres changes, broadcast, presence |
| Server logic and secrets (payments, AI keys, sending push) | **Supabase Edge Functions** | Deno/TypeScript, secrets stay on the server |
| Offline cache | **Room** in `core/database` | unchanged; Room caches, Supabase is the source of truth |
| Analytics, screen views, clicks | **Firebase Analytics** (`core/firebase`) | free |
| Crash reports | **Firebase Crashlytics** (`core/firebase`) | free (`kmp-analytics-logging` section 8) |
| Tester builds | **Firebase App Distribution** | free (`firebase-app-distribution`) |
| Push notifications | **FCM** (`core/firebase`, receive) + edge function (send) | free |
| Feature flags, kill switches | **Firebase Remote Config** (`core/firebase`) | free |
| App Check, Performance Monitoring | Firebase, only if the user asks | free |

**Never add** Firestore, Realtime Database, Firebase Auth, Cloud Storage for
Firebase or Cloud Functions. Their Supabase replacements are above. (Cloud
Storage and Cloud Functions also need the paid Blaze plan.) If an existing
project uses them, plan a migration with the user instead of adding more.

Free-plan facts to tell the user (check https://supabase.com/pricing, they
change): 2 active projects (exactly UAT + PROD), 500 MB database, 1 GB storage,
50k MAU, and **a free project pauses after 7 days without traffic**. UAT pausing
is fine (restore it in the dashboard). A real launch on a paused PROD project is
not, so say this before PROD goes live.

## 1. Where things live

```
core/supabase/                       # new core module, one concern: the Supabase client
  module.yaml
  src/com/<org>/supabase/
    SupabaseConfig.kt                # url + publishable key, from AppEnvironment
    CreateSupabaseClient.kt          # the ONE client: Auth, Postgrest, Storage, Realtime, Functions
    SafeSupabaseCall.kt              # supabase-kt exceptions -> NetworkError
    SupabaseTables.kt                # table, bucket, function and channel names (no inline strings)
    auth/AuthSessionSource.kt        # sessionStatus as Flow<AuthState> for the app root
    auth/NativeSignIn.kt             # only Compose file: Google/Apple native sign-in launcher
supabase/                            # Supabase CLI project at the KMP repo root (committed)
  config.toml
  migrations/20261028070000_init.sql # every schema change, in order
  functions/<name>/index.ts          # edge functions
  tests/*.sql                        # pgTAP tests for RLS
  seed.sql                           # local/UAT seed data only
feature/<name>/src/.../data/datasource/remote/
  source/<Name>SupabaseSource.kt     # implements <Name>Remote (returns DTOs, may throw)
  dto/<Name>Dto.kt                   # @Serializable, @SerialName("snake_case")
app/shared/src/.../di/
  SecureSessionManager.kt            # Supabase session in SecureStorage (core/security)
  SupabaseTokenProvider.kt           # TokenProvider for Ktor calls to your own API
```

Module rules (add them to `kmp-clean-architecture` section 2b):
- `core/supabase` depends on `core/common` and `core/network` (shared engine
  with pinning, `ResultNet`, `NetworkError`). Nothing else.
- Feature **data** may depend on `core/supabase`. Domain and presentation never
  do, except `feature/auth` presentation may use `NativeSignIn.kt`'s launcher.
- `core/supabase` keeps `core/ui`'s platform list, including wasmJs.
- Only `core/supabase` and feature `data` import `io.github.jan.supabase`.

## 2. Dependencies (Amper)

```toml
# libs.versions.toml
[versions]
supabase = "3.8.0"        # latest stable on 2026-10-28; check Maven Central before adding
ktor = "3.5.1"            # supabase-kt 3.8.0 needs Ktor 3.5.x; keep ONE Ktor version

[libraries]
supabase-bom = { module = "io.github.jan-tennert.supabase:bom", version.ref = "supabase" }
supabase-auth = { module = "io.github.jan-tennert.supabase:auth-kt", version.ref = "supabase" }
supabase-postgrest = { module = "io.github.jan-tennert.supabase:postgrest-kt", version.ref = "supabase" }
supabase-storage = { module = "io.github.jan-tennert.supabase:storage-kt", version.ref = "supabase" }
supabase-realtime = { module = "io.github.jan-tennert.supabase:realtime-kt", version.ref = "supabase" }
supabase-functions = { module = "io.github.jan-tennert.supabase:functions-kt", version.ref = "supabase" }
supabase-compose-auth = { module = "io.github.jan-tennert.supabase:compose-auth", version.ref = "supabase" }
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
```
```yaml
# core/supabase/module.yaml
product:
  type: kmp/lib
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64, wasmJs ]   # same as core/ui

dependencies:
  - //core/common
  - //core/network: exported      # NetworkError / ResultNet in the public API
  - $libs.supabase.auth: exported
  - $libs.supabase.postgrest: exported
  - $libs.supabase.storage: exported
  - $libs.supabase.realtime: exported
  - $libs.supabase.functions: exported
  - $libs.supabase.compose.auth   # NativeSignIn.kt only
  - $libs.ktor.client.websockets  # realtime

settings:
  kotlin:
    serialization: json
  compose: enabled                # only for auth/NativeSignIn.kt, like core/ads
```
Add `core/supabase` to `project.yaml`. Only include the plugins the app uses:
drop Realtime or Storage if no feature needs them, and say which you dropped.

## 3. The client (created once)

```kotlin
// core/supabase/.../SupabaseConfig.kt
data class SupabaseConfig(
    val url: String,                 // https://<uat-ref>.supabase.co  or  https://<prod-ref>.supabase.co
    val publishableKey: String,      // sb_publishable_... (or the legacy anon key). NEVER the secret key.
    val isDebug: Boolean,
    val authScheme: String,          // deep link scheme, e.g. "com.touhidapps.kmp"
    val googleWebClientId: String? = null,   // only when Google sign-in is offered
)
```
```kotlin
// core/supabase/.../CreateSupabaseClient.kt
fun createAppSupabaseClient(
    config: SupabaseConfig,
    engine: HttpClientEngine,        // from core/network: the same engine (and pins) as the app HttpClient
    sessionManager: SessionManager,  // SecureSessionManager, bound in app/shared
): SupabaseClient = createSupabaseClient(config.url, config.publishableKey) {
    httpEngine = engine
    requestTimeout = 30.seconds
    defaultLogLevel = if (config.isDebug) LogLevel.DEBUG else LogLevel.WARNING
    defaultSerializer = KotlinXSerializer(Json {
        ignoreUnknownKeys = true      // a new column never crashes old apps
        explicitNulls = false
        coerceInputValues = true
    })

    install(Auth) {
        flowType = FlowType.PKCE      // never the implicit default for OAuth and magic links
        scheme = config.authScheme
        host = "auth-callback"        // deep link: com.touhidapps.kmp://auth-callback
        this.sessionManager = sessionManager
        alwaysAutoRefresh = true
        autoLoadFromStorage = true
    }
    install(Postgrest)
    install(Storage)
    install(Realtime)
    install(Functions)
    install(ComposeAuth) {
        config.googleWebClientId?.let { googleNativeLogin(serverClientId = it) }
        appleNativeLogin()
    }
}
```
- Bind it once in `app/shared`: `single { createAppSupabaseClient(get(), get(), get()) }`.
  Never create a client in a feature, per screen or per request.
- `SupabaseConfig` is built from `AppEnvironment` (section 11). Nothing else
  holds a Supabase URL or key.
- Shared engine: split `core/network`'s platform client factory into
  `createPlatformEngine(pins)` + `createHttpClient(engine, ...)`
  (`kmp-security` 3.1). Bind `single<HttpClientEngine> { createPlatformEngine(...) }`
  and pass it to both clients, so pinning and proxies apply to Supabase too.
- If the app has no Google/Apple sign-in, leave out `ComposeAuth`, its
  dependency and `compose: enabled`.

## 4. Errors: `safeSupabaseCall`

Every Supabase call in a repository goes through it, exactly like
`safeApiCall` for Ktor. Exceptions never reach a ViewModel.

```kotlin
// core/supabase/.../SafeSupabaseCall.kt
suspend inline fun <T> safeSupabaseCall(crossinline call: suspend () -> T): ResultNet<T, NetworkError> =
    try {
        ResultNet.Success(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthRestException) {
        ResultNet.Error(e.toNetworkError())                    // keeps errorCode for the auth feature
    } catch (e: RestException) {
        ResultNet.Error(e.toNetworkError())
    } catch (e: HttpRequestTimeoutException) {
        ResultNet.Error(NetworkError.RequestTimeout(log = e.message.orEmpty()))
    } catch (e: HttpRequestException) {                        // io.github.jan.supabase.exceptions
        ResultNet.Error(NetworkError.NoInternet(log = e.message.orEmpty()))
    } catch (e: SerializationException) {
        ResultNet.Error(NetworkError.Serialization(log = e.message.orEmpty()))
    } catch (e: Exception) {
        ResultNet.Error(NetworkError.Unknown(log = e.stackTraceToString()))
    }

fun RestException.toNetworkError(): NetworkError = when (statusCode) {
    401, 403 -> NetworkError.Unauthorized(log = "$error: $description")
    404, 406 -> NetworkError.NotFound(log = "$error: $description")      // 406 = .single() found no row
    408 -> NetworkError.RequestTimeout(log = error)
    409 -> NetworkError.Conflict(log = "$error: $description")           // unique violation
    413 -> NetworkError.PayloadTooLarge(log = error)
    429 -> NetworkError.TooManyRequests(log = error)
    in 500..599 -> NetworkError.ServerError(log = "$error: $description")
    else -> NetworkError.Unknown(log = "HTTP $statusCode $error: $description")
}
```
- Add `NetworkError.NotFound` if `kmp-networking-ktor` doesn't have it yet, and
  keep `NetworkErrorLogging`'s `when` exhaustive (`kmp-analytics-logging` 6).
- The `log` text goes to logs only. The UI maps `NetworkError` to `Strings`,
  never shows Postgres or Supabase messages.
- `feature/auth` maps `AuthRestException.errorCode` (`AuthErrorCode.InvalidCredentials`,
  `EmailNotConfirmed`, `UserAlreadyExists`, `WeakPassword`, `OverRequestRateLimit`,
  ...) to its own domain `AuthError` in its repository, before
  `safeSupabaseCall` flattens it.

## 5. Sessions and tokens

supabase-kt stores the session through a `SessionManager`. Its default is plain
settings storage, so replace it with `SecureStorage` (`kmp-security` 3.3). The
class joins `core/supabase` and `core/security`, so it lives in `app/shared`
(as `SecureTokenProvider` did).

```kotlin
// app/shared/.../di/SecureSessionManager.kt
class SecureSessionManager(
    private val storage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionManager {
    override suspend fun saveSession(session: UserSession) =
        storage.put(KEY, json.encodeToString(session))

    override suspend fun loadSession(): UserSession =
        json.decodeFromString(storage.get(KEY) ?: throw IllegalStateException("No saved session"))

    override suspend fun deleteSession() = storage.remove(KEY)

    private companion object { const val KEY = "supabase_session" }
}
```
```kotlin
// app/shared/.../di/SupabaseTokenProvider.kt: the Ktor client's TokenProvider
// (kmp-networking-ktor 6) for calls to your own API, which verifies Supabase JWTs.
class SupabaseTokenProvider(private val supabase: SupabaseClient) : TokenProvider {
    override suspend fun current(): Tokens? =
        supabase.auth.currentSessionOrNull()?.let { Tokens(access = it.accessToken, refresh = it.refreshToken) }

    override suspend fun refresh(): Tokens? = try {
        supabase.auth.refreshCurrentSession()
        current()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null                                    // Ktor then returns Unauthorized; the app root goes to login
    }
}
```
- Supabase owns refresh. Delete any hand-written refresh call for Supabase
  tokens. There is one session, so the app and its own API never disagree.
- Never log the session, the access token or the refresh token.

## 6. Auth (`feature/auth`)

The auth feature follows `kmp-feature-scaffold`: Contract, ViewModel, use cases,
`AuthRepository` in domain, `AuthSupabaseSource` in data.

```kotlin
// feature/auth/.../data/datasource/remote/source/AuthSupabaseSource.kt
class AuthSupabaseSource(private val supabase: SupabaseClient) : AuthRemote {
    override suspend fun signInWithEmail(email: String, password: String) {
        supabase.auth.signInWith(Email) { this.email = email; this.password = password }
    }
    override suspend fun signUp(email: String, password: String, displayName: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("display_name", displayName) }   // read by the profiles trigger
        }
    }
    override suspend fun sendOtp(email: String) {
        supabase.auth.signInWith(OTP) { this.email = email; createUser = true }
    }
    override suspend fun verifyOtp(email: String, code: String) {
        supabase.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
    }
    override suspend fun sendPasswordReset(email: String) {
        supabase.auth.resetPasswordForEmail(email)                        // redirects to the deep link
    }
    override suspend fun signOut() {
        supabase.auth.signOut()                                           // revokes the refresh token on the server
    }
    override fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id
}
```
- **Sign-up consent:** the register screen has an unticked Terms & Privacy
  checkbox; on success it adds `legal_agreed_at` to the sign-up `data` and
  marks `SignupConsentStore`, and the `feature/legal` overlay records the
  accepted versions on the server (`kmp-legal-consent` 4, 5).
- **App-level auth state:** `AuthSessionSource` in `core/supabase` maps
  `supabase.auth.sessionStatus` to `Flow<AuthState>` (`Loading`, `SignedIn(userId)`,
  `SignedOut`, `RefreshFailed`). `app/shared` observes it once and navigates to
  login on `SignedOut`. Features never observe auth on their own.
- **Deep links** (password reset, magic link, OAuth with PKCE): register
  `com.touhidapps.kmp://auth-callback` in the Supabase dashboard
  (Authentication > URL Configuration) for each project, add the intent filter
  in `AndroidManifest.xml` and `CFBundleURLTypes` in `Info.plist`, then call
  `supabase.handleDeeplinks(intent)` in `MainActivity.onCreate`/`onNewIntent`
  and `supabase.handleDeeplinks(url)` from `iosApp.swift`'s `onOpenURL`.
- **Google / Apple sign-in:** native flows via `compose-auth`
  (`rememberSignInWithGoogle` / `rememberSignInWithApple`) wrapped by
  `NativeSignIn.kt`, so the auth screen gets one `onResult` callback. Enable the
  providers in each Supabase project. Apple sign-in is required on iOS if any
  other social login is offered (App Store rule 4.8).
- **Logout clears everything** (`kmp-security` 2.11): `signOut()`, remove the
  realtime channels, clear Room caches that hold user data,
  `analytics.setUser(null)`.
- On logout and account switch, also delete this device's row in
  `device_tokens` (section 10) **before** `signOut()`, while RLS still allows it.

## 7. Database (Postgres + RLS)

### 7.1 Schema lives in migrations

```bash
supabase init                                   # once, creates supabase/
supabase migration new create_profiles         # writes supabase/migrations/<ts>_create_profiles.sql
supabase start                                  # local stack in Docker, for tests
supabase db reset                               # re-apply all migrations + seed locally
supabase link --project-ref <uat-ref> && supabase db push     # apply to UAT
supabase link --project-ref <prod-ref> && supabase db push    # apply to PROD, only with the user's go-ahead
```
Never change tables in the dashboard's table editor for UAT or PROD. Every
change is a migration file, reviewed, applied to UAT first, then PROD.

```sql
-- supabase/migrations/20261028070000_create_profiles.sql
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 60),
  avatar_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy "read own profile" on public.profiles
  for select to authenticated using ((select auth.uid()) = id);
create policy "update own profile" on public.profiles
  for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

-- create the profile row when a user signs up
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', 'User'));
  return new;
end $$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();
```
**RLS rules (never break these):**
- `enable row level security` on **every** table in `public`, in the same
  migration that creates it. A table without RLS is readable by anyone with the
  publishable key, which ships inside the app.
- Policies use `(select auth.uid())`, name the role (`to authenticated`), and
  have `with check` on insert/update. Index the columns policies filter on.
- No policy uses `user_metadata` for authorization (users can edit it). Roles
  (admin, moderator) come from a table the user can't write, or from
  `app_metadata` set by the server.
- Run `supabase db lint` and the dashboard's Security Advisor before each PROD push.
- Business rules that must not be bypassed (balances, stock, payments) run in a
  `security definer` Postgres function or an edge function, never only in the app.

### 7.2 Data source in the feature

```kotlin
// feature/user/.../data/datasource/remote/dto/ProfileDto.kt
@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class UpdateProfileDto(@SerialName("display_name") val displayName: String)
```
```kotlin
// feature/user/.../data/datasource/remote/source/ProfileSupabaseSource.kt
class ProfileSupabaseSource(private val supabase: SupabaseClient) : ProfileRemote {

    override suspend fun getProfile(userId: String): ProfileDto =
        supabase.from(SupabaseTables.PROFILES)
            .select { filter { eq("id", userId) } }
            .decodeSingle()

    override suspend fun updateProfile(userId: String, body: UpdateProfileDto): ProfileDto =
        supabase.from(SupabaseTables.PROFILES)
            .update(body) {
                select()                                      // return the updated row
                filter { eq("id", userId) }
            }
            .decodeSingle()

    override suspend fun orders(userId: String, page: Int, pageSize: Int): List<OrderDto> {
        val from = (page * pageSize).toLong()
        return supabase.from(SupabaseTables.ORDERS)
            .select(Columns.list("id", "status", "total_minor", "created_at")) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                range(from, from + pageSize - 1)
            }
            .decodeList()
    }

    override suspend fun placeOrder(body: PlaceOrderDto): OrderDto =
        supabase.postgrest.rpc("place_order", body).decodeAs()   // security definer function
}
```
```kotlin
// feature/user/.../data/repository/ProfileRepositoryImpl.kt
class ProfileRepositoryImpl(
    private val remote: ProfileRemote,
    private val session: CurrentUser,                         // core/common interface, bound to Supabase auth
    private val local: ProfileLocalDataSource,               // Room cache, optional
) : ProfileRepository {
    override suspend fun getProfile(): ResultNet<Profile, NetworkError> {
        val userId = session.id() ?: return ResultNet.Error(NetworkError.Unauthorized(log = "no session"))
        return safeSupabaseCall { remote.getProfile(userId) }
            .map { it.toDomain() }
            .onSuccess { local.save(it) }
    }
}
```
Rules:
- Table, column, bucket, RPC and channel names live in `SupabaseTables` (or the
  feature's own constants object). No string literals in calls.
- Select only the columns the screen needs. Always paginate lists with `range`.
- DTOs are `@Serializable` with `@SerialName` for snake_case. Never reuse a DTO
  as a domain model or a Room entity. Money is an integer in minor units.
- Filter by the current user even though RLS enforces it: it's clearer and
  lets Postgres use the index.
- The current user id reaches repositories through a small `CurrentUser`
  interface in `core/common` (bound in `app/shared` to `supabase.auth`), so
  domain code never sees supabase-kt types.

## 8. Storage

```kotlin
// feature/user/.../ProfileSupabaseSource.kt
override suspend fun uploadAvatar(userId: String, bytes: ByteArray): String {
    val path = "$userId/avatar.jpg"                          // first folder = owner id (policy below)
    supabase.storage.from(SupabaseTables.AVATARS_BUCKET).upload(path, bytes) {
        upsert = true
        contentType = ContentType.Image.JPEG
    }
    return path                                               // store the path, not a URL
}

override suspend fun avatarUrl(path: String): String =
    supabase.storage.from(SupabaseTables.AVATARS_BUCKET).createSignedUrl(path, expiresIn = 1.hours)
```
```sql
-- migration: private bucket, users write only under their own folder
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', false, 2097152, array['image/jpeg', 'image/png', 'image/webp']);

create policy "own avatar read" on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "own avatar write" on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "own avatar update" on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
```
- Buckets are **private** unless the content is truly public (product images on
  the landing page). Private files use signed URLs with a short expiry.
- Compress and resize images before upload (max 1080 px, JPEG/WebP). Use
  `uploadAsFlow` when the UI shows progress.
- Coil loads the signed URL. Don't cache signed URLs longer than their expiry.

## 9. Realtime

```kotlin
// feature/chat/.../data/datasource/remote/source/ChatSupabaseSource.kt
override fun newMessages(roomId: String): Flow<MessageDto> = channelFlow {
    val channel = supabase.channel("room:$roomId")
    val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
        table = SupabaseTables.MESSAGES
        filter("room_id", FilterOperator.EQ, roomId)
    }
    launch { inserts.collect { send(it.decodeRecord<MessageDto>()) } }
    channel.subscribe(blockUntilSubscribed = true)
    awaitClose { launch(NonCancellable) { supabase.realtime.removeChannel(channel) } }
}
```
```sql
alter publication supabase_realtime add table public.messages;   -- opt the table in
```
- RLS applies: a user only receives rows their `select` policy allows.
- One channel per screen scope, removed in `awaitClose`. The ViewModel collects
  the repository `Flow` in `viewModelScope`, so leaving the screen closes it.
- Realtime fills gaps poorly after a reconnect. On (re)subscribe, fetch the
  latest page with Postgrest and then append live rows, de-duplicated by id.
- Use **broadcast** for typing indicators and **presence** for "online"
  instead of writing those to tables.

## 10. Edge functions (server logic and secrets)

```
supabase/functions/
  send-push/index.ts       # FCM HTTP v1, triggered by a database webhook or the app
  create-payment/index.ts  # payment provider secret key stays here
  ai-complete/index.ts     # AI provider key stays here (core/ai calls this)
  app-status/index.ts      # only if there is no other backend for it
```
```kotlin
// feature data source
override suspend fun createPayment(body: CreatePaymentDto): PaymentIntentDto =
    supabase.functions.invoke(SupabaseTables.FN_CREATE_PAYMENT, body).body()
```
- supabase-kt sends the user's access token automatically. Functions verify it
  (the default `verify_jwt = true`) and act as that user. Use the service role
  key **only inside functions**, never in the app, never in `env.properties`.
- Secrets per environment: `supabase secrets set --project-ref <ref> STRIPE_KEY=...`.
  UAT gets test keys, PROD live keys.
- Deploy: `supabase functions deploy <name> --project-ref <uat-ref>`, then PROD
  with the user's go-ahead.
- **Push:** (full app side and the current PHP sender: `kmp-push-notifications`,
  `web-admin-landing` 6c; this is the Supabase variant) the app gets its FCM token from `core/firebase` and upserts it into
  `device_tokens (user_id, token, platform, updated_at)` with RLS "own rows".
  `send-push` reads tokens with the service role and calls FCM HTTP v1 with a
  Firebase service account JSON stored as a Supabase secret.
- **AI** (`core/ai`): `AiAssistant`'s remote implementation calls the
  `ai-complete` function, which holds the provider key and rate-limits per user.

## 11. Environments (UAT and PROD)

Two Supabase projects, "MyApp UAT" and "MyApp Prod", exactly like the two
Firebase projects (`kmp-environments` section 2b). They never share a database,
storage, users or secrets.

```properties
# config/uat/supabase.properties   (git-ignored, like google-services.json)
SUPABASE_URL=https://<uat-ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```
`scripts/use_env.py` reads it into `AppEnvironment.supabaseUrl` /
`supabasePublishableKey`, refuses a secret or service-role key, and refuses UAT
and PROD pointing at the same project. `app/shared` builds `SupabaseConfig`
from `AppEnvironment`. Migrations and functions are applied to UAT first, then
PROD.

## 12. Security

- The publishable key is public by design. Security comes from **RLS**, not
  from hiding the key. The secret/service-role key never leaves the server.
- Sessions in `SecureStorage` (section 5), PKCE for redirects, logout revokes
  the session on the server.
- In each project: enable email confirmation, set minimum password length
  (8+) and leaked-password protection where the plan allows, set the JWT expiry
  (default 1 hour; HIGH tier 15 minutes), and configure CAPTCHA for sign-up if
  abuse appears.
- **HIGH tier** (`kmp-security`): `*.supabase.co` certificates are managed by
  Supabase and rotate, so never pin the leaf. Either pin the issuing CA keys
  with backups (check the current chain with `openssl s_client` before each
  release), or move money operations behind a backend on a domain you control.
  Ask the user which. Device attestation tokens are verified in an edge
  function before sensitive operations.

## 13. Tests (TDD, inside out)

- **Repository tests** use a fake `ProfileRemote` (`commonTest`), like any
  other repository. Most tests never touch supabase-kt.
- **Source tests** check the HTTP that supabase-kt sends, with Ktor `MockEngine`:
  ```kotlin
  private fun testClient(handler: MockRequestHandler) =
      createSupabaseClient("https://test.local", "test-key") {
          httpEngine = MockEngine(handler)
          install(Postgrest)
          install(Auth) { autoLoadFromStorage = false; alwaysAutoRefresh = false }
      }

  @Test fun getProfileFiltersById() = runTest {
      val client = testClient { request ->
          assertEquals("/rest/v1/profiles", request.url.encodedPath)
          assertEquals("eq.u1", request.url.parameters["id"])
          respond("""{"id":"u1","display_name":"Ana","updated_at":"2026-10-28T00:00:00Z"}""",
              headers = headersOf(HttpHeaders.ContentType, "application/json"))
      }
      assertEquals("Ana", ProfileSupabaseSource(client).getProfile("u1").displayName)
  }
  ```
- `safeSupabaseCall` tests: 401, 404/406, 409, 5xx, timeout and offline each map
  to the right `NetworkError`.
- **RLS tests** in `supabase/tests/*.sql` with pgTAP (`supabase test db`): user A
  can't read or update user B's rows, anonymous users read nothing private,
  storage paths outside the user's folder are rejected. These run in CI against
  the local stack (`supabase start`).
- Never run tests against the UAT or PROD project.

## 14. Checklist

- [ ] Supabase for auth, database, storage, realtime, server logic; Firebase only for Analytics, Crashlytics, App Distribution, FCM, Remote Config
- [ ] `core/supabase` added to `project.yaml`, depends only on `core/common` + `core/network`, same platforms as `core/ui`
- [ ] One `SupabaseClient` in `app/shared`, sharing the `core/network` engine; PKCE; `SecureSessionManager`
- [ ] Features use Supabase only in `data/`, through `<Name>SupabaseSource` + `safeSupabaseCall`; no inline table names
- [ ] Every table has RLS + policies in the same migration; pgTAP tests prove isolation; `supabase db lint` clean
- [ ] Storage buckets private unless public on purpose; per-user folder policies; signed URLs
- [ ] Realtime channels removed on close; refetch after reconnect
- [ ] Secrets and the service-role key only in edge functions (`supabase secrets set`), per environment
- [ ] Separate UAT and PROD projects; `supabase.properties` git-ignored; migrations and functions go to UAT first
- [ ] Free-plan pause and limits told to the user before PROD launch
- [ ] File report lists migrations, functions, `core/supabase` and wiring changes (`kmp-feature-scaffold` section 13)
