---
name: kmp-networking-ktor
description: Build networking in a Kotlin Multiplatform app with Ktor client + kotlinx.serialization on the KMP-MultiModule-Template layout (core/network, ResultNet, safeApiCall). Use when adding API calls, configuring the HttpClient, auth/token refresh, error handling, uploads, pagination, or testing network code.
---

# KMP Networking with Ktor

All HTTP in the app goes through **one shared Ktor `HttpClient`** configured in
`core/network`, returns `ResultNet<T, NetworkError>`, and is tested with Ktor's
`MockEngine`. Don't use other HTTP libraries (Retrofit, OkHttp directly, URLSession
or fetch).

**Supabase is separate.** Calls to Supabase (auth, tables, storage, realtime,
edge functions) go through the one `SupabaseClient` in `core/supabase` and
`safeSupabaseCall` (`kmp-supabase`). This skill covers the Ktor `HttpClient` for
your own API and third-party HTTP. Both clients share one platform engine from
`core/network`, so timeouts, proxies and pinning match.

Reference:
[TouhidApps/KMP-MultiModule-Template](https://github.com/TouhidApps/KMP-MultiModule-Template)
`core/network` (`createHttpClient`, `ResultNet`, `NetworkError`, `safeApiCall`,
`BaseApi`). Layering follows `kmp-clean-architecture`, and the feature workflow
(TDD, screenshots, file report) follows `kmp-feature-scaffold`.

## 1. Where things live

```
core/network/                      // the ONLY module that configures Ktor
  module.yaml                      // ktor core, content-negotiation, logging, auth, serialization + engines
  src/.../network/
    CreateHttpClient.kt            // createHttpClient(config, tokenProvider)
    NetworkConfig.kt               // baseUrl, isDebug, timeouts (injected, not hardcoded)
    ResultNet.kt                   // ResultNet, NetworkError, map/onSuccess/onError
    safeApiCall.kt                 // exception -> NetworkError mapping
    HttpStatusException.kt         // typed non-2xx error
    TokenProvider.kt               // interface: access/refresh tokens (implemented elsewhere)
feature/<name>/src/.../data/datasource/remote/
  api/<Name>Api.kt                 // interface, returns DTOs
  source/<Name>Source.kt           // Ktor implementation (or <Name>ApiImpl)
  source/ApiUrls.kt                // endpoint paths for this feature
  dto/<Name>Dto.kt                 // @Serializable
```

Engines per platform (already in `core/network/module.yaml`): `okhttp` on
android and jvm, `darwin` on iOS, `js` on wasmJs. Keep `platforms:` equal to
`core/ui`'s.

## 2. The HttpClient

Create it **once**: `single<HttpClient> { createHttpClient(get(), get()) }` in
`app/shared`'s `appModule`. `NetworkConfig` is built from the active
`AppEnvironment` (UAT or PROD). See the `kmp-environments` skill. Never create a client in a feature, a repository or
per request.

```kotlin
data class NetworkConfig(
    val baseUrl: String,                  // per build/environment, not a const in code
    val isDebug: Boolean,
    val timeoutMillis: Long = 30_000,
)

fun createHttpClient(config: NetworkConfig, tokens: TokenProvider): HttpClient = HttpClient {
    expectSuccess = false

    defaultRequest {
        url(config.baseUrl)
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header("Platform", "KMP")         // body Content-Type is set by ContentNegotiation per request
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true      // new server fields don't crash old apps
            explicitNulls = false
            coerceInputValues = true      // null for a non-null field with default -> default
            isLenient = true
        })
    }

    install(HttpTimeout) {
        connectTimeoutMillis = config.timeoutMillis
        requestTimeoutMillis = config.timeoutMillis
        socketTimeoutMillis = config.timeoutMillis
    }

    install(HttpRequestRetry) {
        maxRetries = 2
        retryIf { request, response ->
            request.method in listOf(HttpMethod.Get, HttpMethod.Head) && response.status.value in 500..599
        }
        retryOnExceptionIf { request, cause ->
            request.method == HttpMethod.Get && cause is IOException && cause !is HttpRequestTimeoutException
        }
        exponentialDelay()
    }

    install(Auth) {
        bearer {
            loadTokens { tokens.current()?.let { BearerTokens(it.access, it.refresh) } }
            refreshTokens { tokens.refresh()?.let { BearerTokens(it.access, it.refresh) } }
            sendWithoutRequest { request -> request.url.host == Url(config.baseUrl).host }
        }
    }

    HttpResponseValidator {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                throw HttpStatusException(response.status.value, response.bodyAsText().take(2_000))
            }
        }
    }

    if (config.isDebug) {
        install(Logging) {
            level = LogLevel.BODY
            logger = Logger.SIMPLE
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }
}

class HttpStatusException(val code: Int, val body: String) : Exception("HTTP $code")
```

When you improve the template's `CreateHttpClient.kt`, fix these:
- A hardcoded `Authorization: Bearer token_here` header. Use the `Auth` plugin
  with a `TokenProvider`.
- `LogLevel.ALL`, `println` loggers and a custom logging plugin in every build.
  Log only when `isDebug`, through one logger, and always sanitize auth headers.
- Retrying on **any** non-success, including 4xx and POST. Retry only idempotent
  methods on 5xx or IO errors, with backoff.
- Throwing a plain `Exception(message)` for HTTP errors. Status codes are then
  lost, and everything becomes `Unknown`. Throw `HttpStatusException(code)`.
- `prettyPrint = true` in production JSON. Remove it.
- `BASE_URL` as a const. Inject it through `NetworkConfig`, built from `AppEnvironment` (`kmp-environments`).

## 3. safeApiCall: exceptions become typed errors

```kotlin
suspend inline fun <T> safeApiCall(crossinline call: suspend () -> T): ResultNet<T, NetworkError> =
    try {
        ResultNet.Success(call())
    } catch (e: CancellationException) {
        throw e                                            // never swallow cancellation
    } catch (e: HttpStatusException) {
        ResultNet.Error(e.toNetworkError())
    } catch (e: HttpRequestTimeoutException) {
        ResultNet.Error(NetworkError.RequestTimeout(log = e.message.orEmpty()))
    } catch (e: SerializationException) {
        ResultNet.Error(NetworkError.Serialization(log = e.message.orEmpty()))
    } catch (e: UnresolvedAddressException) {
        ResultNet.Error(NetworkError.NoInternet(log = e.message.orEmpty()))
    } catch (e: IOException) {
        ResultNet.Error(NetworkError.NoInternet(log = e.message.orEmpty()))
    } catch (e: Exception) {
        ResultNet.Error(NetworkError.Unknown(log = e.stackTraceToString()))
    }

fun HttpStatusException.toNetworkError(): NetworkError = when (code) {
    401, 403 -> NetworkError.Unauthorized(log = body)
    408 -> NetworkError.RequestTimeout(log = body)
    409 -> NetworkError.Conflict(log = body)
    413 -> NetworkError.PayloadTooLarge(log = body)
    429 -> NetworkError.TooManyRequests(log = body)
    in 500..599 -> NetworkError.ServerError(log = body)
    else -> NetworkError.Unknown(log = "HTTP $code: $body")
}
```
- The order of the catch blocks matters. The specific exceptions
  (`HttpRequestTimeoutException` is an `IOException`) must come before
  `IOException`, and `CancellationException` must come first.
- Add a `NetworkError.NotFound`/`BadRequest` subtype if the UI must react to it
  differently. The UI maps each `NetworkError` to `Strings`. Never show
  `errMessage` or the server body to users.
- If the backend returns a JSON error body (`{ "code": ..., "message": ... }`),
  decode it into an `ApiErrorDto` inside `toNetworkError()`. Keep the
  user-facing text in `Strings`.

## 4. Writing an API (feature data layer)

```kotlin
object ApiUrls {
    const val V1 = "api/v1"               // no leading "/": keeps the base path (UAT = https://abc.com/uat/)
    const val PROFILE = "$V1/profile"
    fun order(id: String) = "$V1/orders/$id"
}

interface ProfileApi {
    suspend fun getProfile(): ProfileDto
    suspend fun updateProfile(body: UpdateProfileRequestDto): ProfileDto
    suspend fun searchOrders(query: String, page: Int, pageSize: Int): PageDto<OrderDto>
    suspend fun deleteOrder(id: String)
    suspend fun uploadAvatar(bytes: ByteArray, fileName: String, onProgress: (Float) -> Unit): AvatarDto
}

class ProfileSource(private val client: HttpClient) : ProfileApi {

    override suspend fun getProfile(): ProfileDto =
        client.get(ApiUrls.PROFILE).body()

    override suspend fun updateProfile(body: UpdateProfileRequestDto): ProfileDto =
        client.put(ApiUrls.PROFILE) { setBody(body) }.body()

    override suspend fun searchOrders(query: String, page: Int, pageSize: Int): PageDto<OrderDto> =
        client.get("${ApiUrls.V1}/orders") {
            parameter("q", query)
            parameter("page", page)
            parameter("size", pageSize)
        }.body()

    override suspend fun deleteOrder(id: String) {
        client.delete(ApiUrls.order(id))
    }

    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String, onProgress: (Float) -> Unit): AvatarDto =
        client.submitFormWithBinaryData(
            url = "${ApiUrls.V1}/avatar",
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            },
        ) {
            onUpload { sent, total -> total?.takeIf { it > 0 }?.let { onProgress(sent.toFloat() / it) } }
        }.body()
}
```
Rules:
- The API interface returns **DTOs** and may throw. The **repository** wraps every
  call in `safeApiCall` and maps DTO to domain model (see `kmp-clean-architecture`).
- Use relative paths only, with **no leading `/`**. The base URL comes from
  `defaultRequest` and ends with `/`. UAT is path-based (`https://abc.com/uat/`,
  see `kmp-environments` 1b), and a path like `"/api/v1/profile"` drops `/uat`, so a
  UAT build would call PROD. Put paths in the feature's `ApiUrls`, never inline
  string literals in calls.
- Use `parameter(...)` for queries, never string concatenation. Encode path
  segments that come from user input.
- Request bodies are `@Serializable` `...RequestDto` classes, never `Map<String, Any>`.
- Don't call `runBlocking`, and don't switch to `Dispatchers.IO` yourself. Ktor
  suspends on its own engine threads.

## 5. DTOs

```kotlin
@Serializable
data class ProfileDto(
    @SerialName("id") val id: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("roles") val roles: List<String> = emptyList(),
)
```
- Use `val`s, nullable fields with defaults (servers drift), and explicit
  `@SerialName` for every field.
- Use one DTO file per endpoint group, in `dto/`. Never reuse DTOs as domain
  models or as Room entities.
- Keep generic wrappers in `core/network` if the backend has a common envelope:
  `@Serializable data class ApiEnvelope<T>(val data: T? = null, val error: ApiErrorDto? = null)`.

## 6. Auth and tokens

- With Supabase Auth, `TokenProvider` is bound to `SupabaseTokenProvider`
  (`kmp-supabase` section 5): Supabase stores and refreshes the session, and
  your own API verifies the Supabase JWT. The rest of this section applies to
  a backend with its own tokens.
- `TokenProvider` is an interface in `core/network`. Tokens are stored in
  `SecureStorage` from `core/security`, never in `core/preferences` or
  `core/database`. Because core modules depend only on `core/common`
  (`kmp-clean-architecture` section 2b), the small `SecureTokenProvider(secureStorage)`
  that joins the two is written and bound in `app/shared`. Never put tokens in
  code, logs or `BaseApi`.
- The refresh call uses a **separate** client without the `Auth` plugin, or a
  request marked with `markAsRefreshTokenRequest()`, so refresh can't loop.
- On a failed refresh: clear tokens, return `NetworkError.Unauthorized`, and let
  the app navigate to login (an app-level observer, not each feature).
- After logout, call `client.authProvider<BearerAuthProvider>()?.clearToken()`
  so cached tokens are dropped.

## 7. Pagination, polling and streams

- Pagination: the repository exposes `suspend fun page(n): ResultNet<PageModel, NetworkError>`.
  The ViewModel keeps `items`, `page`, `isLoadingMore` and `endReached` in `State`,
  and loads the next page when the `LazyColumn` nears the end. De-duplicate by ID.
- Polling: use `flow { while (currentCoroutineContext().isActive) { emit(api.status()); delay(interval) } }`
  in the repository, collected in `viewModelScope`, so it stops with the screen.
- Live updates: Ktor `WebSockets` (`client.webSocket(...)`) wrapped in a repository
  `Flow`. Reconnect with backoff, and expose connection state to the UI.

## 8. Images

Coil 3 uses Ktor (`coil-network-ktor3`). Use the shared component (`MyAsyncImage`
/ `AppAsyncImage`) from `core/ui`, never a raw `HttpClient` call for images. Don't
point demo data at copyrighted image URLs.

## 9. Security

- HTTPS only. Never disable certificate checks. Add certificate pinning per
  engine (OkHttp `CertificatePinner`, Darwin `handleChallenge`) only if the user
  asks for it.
- No API keys or secrets in source. Read them from build config or the
  environment, and keep real secrets on the server.
- Sanitize `Authorization` and any token headers in logs. Disable body logging
  in release.

## 10. Testing with MockEngine (TDD: write these first)

Add to the module's `test-dependencies:` `$libs.ktor.client.mock` (add
`ktor-client-mock` to `libs.versions.toml` if missing) and
`$libs.kotlinx.coroutines.test`.

```kotlin
fun mockClient(baseUrl: String = "https://test.local/", handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    defaultRequest { url(baseUrl) }
    HttpResponseValidator {
        validateResponse { if (!it.status.isSuccess()) throw HttpStatusException(it.status.value, it.bodyAsText()) }
    }
}

class ProfileSourceTest {
    @Test
    fun getProfileCallsTheRightPathAndParsesTheBody() = runTest {
        val client = mockClient { request ->
            assertEquals("/api/v1/profile", request.url.encodedPath)
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """{"id":"1","full_name":"Sample User"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val dto = ProfileSource(client).getProfile()
        assertEquals("Sample User", dto.fullName)
    }

    @Test
    fun pathBasedUatBaseUrlKeepsTheUatPrefix() = runTest {
        val client = mockClient(baseUrl = "https://test.local/uat/") { request ->
            assertEquals("/uat/api/v1/profile", request.url.encodedPath)   // fails if ApiUrls starts with "/"
            respond("""{"id":"1","full_name":"Sample User"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        ProfileSource(client).getProfile()
    }
}

class ProfileRepositoryImplTest {
    @Test
    fun serverErrorBecomesServerErrorResult() = runTest {
        val client = mockClient { respondError(HttpStatusCode.InternalServerError) }
        val result = ProfileRepositoryImpl(ProfileSource(client)).getProfile()
        val error = assertIs<ResultNet.Error<NetworkError>>(result).error
        assertIs<NetworkError.ServerError>(error)
    }
}
```
Test at least: the correct path, method, query and body; parsing with missing or
extra fields; each status mapping (401, 404, 429, 500); a timeout or IO error
becoming `NoInternet`/`RequestTimeout`; and that cancellation propagates. Use
neutral, made-up JSON fixtures. Never use real user data or copyrighted content.
Keep large fixtures in `test/.../fixtures/`.

## 11. Checklist

- [ ] One `HttpClient` from `core/network`, injected via Koin; no clients in features or other core modules (`core/ai` reuses it)
- [ ] Feature endpoints and DTOs stay in the feature's `data/`; only client-wide plugins and envelopes go in `core/network`
- [ ] Base URL and debug flag from `NetworkConfig`; no hardcoded tokens or secrets
- [ ] JSON: `ignoreUnknownKeys`, `explicitNulls = false`, `coerceInputValues`; no `prettyPrint`
- [ ] Retry only idempotent requests on 5xx or IO errors, with backoff; timeouts set
- [ ] Non-2xx throws `HttpStatusException`; `safeApiCall` maps it to typed `NetworkError`, rethrows `CancellationException`
- [ ] Logging only in debug, `Authorization` sanitized
- [ ] API interface → Ktor source → repository wraps with `safeApiCall` and maps DTO → Model
- [ ] Paths in `ApiUrls`, queries via `parameter()`, `@Serializable` request DTOs
- [ ] Bearer auth with refresh via the `Auth` plugin; logout clears tokens
- [ ] `MockEngine` tests written first for source and repository (success, each error, parsing)
- [ ] UI maps every `NetworkError` to `Strings`; screenshots of the error and offline states (`kmp-feature-scaffold` section 12)
- [ ] File report of added and changed files (`kmp-feature-scaffold` section 13)
