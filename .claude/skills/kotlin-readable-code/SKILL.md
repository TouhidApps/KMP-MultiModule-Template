---
name: kotlin-readable-code
description: Write human-readable, clean Kotlin / KMP / Compose code, covering clean-code principles (SRP, DRY, KISS, YAGNI, SOLID), naming, small functions, structure, comments, idioms and file organization. Use whenever writing, refactoring or reviewing Kotlin code in a KMP app.
---

# Human-Readable Clean Kotlin Code

Write code for the **next person who reads it**, who may be you in six months.
Readable code is quick to understand, safe to change, and needs few comments. It
matters more than clever code or saving lines.

Applies to everything written under the other skills (`kmp-feature-scaffold`,
`kmp-clean-architecture`, `compose-multiplatform-ui`, ...). Follow the official
[Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
and the project's existing style. Where this skill and the project differ,
**match the project** and mention the difference.

## 1. The test for readable code

Before you finish a file, ask:
1. Can someone understand **what** each function does from its name and signature alone?
2. Can they understand **how** it works by reading top to bottom once, without jumping around?
3. Is every **why** that isn't obvious written down in a short comment?
4. Is there anything left that does nothing (dead code, unused params, commented-out blocks)?

If any answer is "no", fix it before moving on.

## 1b. Clean-code principles

Readable code also has to be **clean**: small parts with one job each, no
repetition, and no more than the task needs.

| Principle | Rule | In this codebase |
|---|---|---|
| **Single Responsibility (SRP)** | A class or function has one reason to change. | A ViewModel maps events to state, a use case holds one business rule, a mapper only maps, a `Source` only does HTTP. A screen file doesn't parse JSON. |
| **DRY (don't repeat yourself)** | One source of truth for each piece of knowledge. Extract on the **third** repetition (the rule of three). Don't merge two things that only look alike. | Shared UI goes to `core/ui` components, shared rules to a use case, and shared HTTP setup to `core/network`. Copy-pasted `state.update { it.copy(isLoading = false, ...) }` blocks become one `private fun showError(...)`. |
| **KISS** | Choose the simplest solution that works. | A plain function before a new class, a `data class` before an inheritance tree, `StateFlow` before a custom event bus. |
| **YAGNI** | Don't build for imagined future needs. | No unused parameters, interfaces with one fake "for later", or config flags nobody sets. Add them when a real second case appears. |
| **Open/Closed** | Extend by adding, not by editing a growing `when`. | New `AlertType` or `NetworkError` subtypes plus exhaustive `when`s, so the compiler lists every place to update. |
| **Liskov** | Implementations honor the interface's contract. | Every `FooRepository` returns `ResultNet` for errors. None of them throws for "not found" while another returns an error. |
| **Interface Segregation** | Small, focused interfaces. | `SecureStorage`, `DeviceIntegrity`, `Analytics`, not one `PlatformServices` god interface. |
| **Dependency Inversion** | Depend on abstractions, inject them. | Constructor injection through Koin, and an interface in domain with the implementation in data. No `object` singletons holding state, and no `KoinComponent.get()` inside classes. |
| **Law of Demeter** | Talk to direct collaborators only. | `order.customerCity`, not `order.customer.address.city.name` scattered across the UI. Map it once in the mapper. |
| **Pure functions where possible** | Same input gives the same output, with no side effects. | Mappers, validators and formatters are pure and trivially testable. Keep I/O at the edges (sources, repositories). |
| **Immutability** | Prefer values that don't change. | `val` and data classes, `copy()` for updates, and read-only collections in state. |
| **No magic values** | Name every number and string that carries meaning. | `const val PAGE_SIZE = 20`, `ApiUrls.PROFILE`, `AppTheme.spacing.lg`, `env.strings.retry`. |
| **Fail clearly** | Errors are explicit and handled once, at the right layer. | `safeApiCall` turns exceptions into `ResultNet` at the data edge. Never use `catch (e: Exception) {}` with an empty body. |
| **Boy-scout rule** | Leave touched code a little cleaner. | When you edit a file, fix a bad name or remove dead code nearby, in a separate commit from the behavior change if it's bigger than trivial. |

**Size limits (signals, not laws):** a function over about 20 lines, a class
over about 200 lines, more than 4 constructor dependencies, or more than 3
levels of nesting means it is probably doing too much. Split it by
responsibility.

**Code smells to fix when you see them:** a long parameter list, a feature that
reaches into another class's data, a "god" `Utils`/`Manager`/`Helper`,
duplicated blocks, flag arguments, primitive obsession (a `String` for an ID
that could be a value class), and speculative generality.

## 2. Naming: the most important part

| Kind | Rule | Good | Bad |
|---|---|---|---|
| Class / object | Noun, PascalCase, says what it *is* | `FlowerListRepository`, `SessionTimer` | `FlowerHelper`, `Manager2` |
| Function | Verb phrase, says what it *does* | `loadFlowers()`, `markAsRead(id)` | `process()`, `doIt()`, `handle()` |
| Boolean | Reads as a yes/no question | `isLoading`, `hasError`, `canSubmit`, `shouldRetry` | `loading`, `flag`, `status` |
| Collection | Plural noun | `flowers`, `unreadIds` | `flowerList2`, `data` |
| Lambda param | Name it when it isn't trivial | `onFlowerClick`, `onRetry` | `callback`, `listener1` |
| Constant | `UPPER_SNAKE_CASE` in `const val` / object | `MAX_RETRY_COUNT`, `PAGE_SIZE` | `x`, `num` |
| Composable | PascalCase noun, the UI it draws | `FlowerCard`, `ErrorState` | `drawStuff()` |

- **Say what it means, not how it is stored:** `remainingSeconds`, not `int1`;
  `selectedLanguage`, not `langVal`.
- **No unclear abbreviations:** `message`, not `msg`; `response`, not `resp`;
  `index`, not `idx`. Fine: `id`, `url`, `api`, `dto`, `ui`.
- **One word per concept across the codebase.** Pick `get`, `load` or `fetch` and
  keep it. Don't mix `getUser`, `fetchOrders`, `retrieveCart`.
- **Name length matches scope.** `i` in a three-line loop is fine. A class property
  needs a full name.
- **Units in names** when a type doesn't carry them: `timeoutMillis`,
  `distanceMeters`. Better still, use `Duration`.
- **Avoid negatives:** `isEnabled`, not `isNotDisabled`.
- **Suffixes follow the architecture:** `...Dto`, `...Entity`, `...Model`,
  `...UseCase`, `...RepositoryImpl`, `...Contract`, `...State`, `...Events`,
  `...Actions`.

## 3. Functions

- **Do one thing.** If you need "and" to describe it (`validateAndSaveAndNotify`),
  split it.
- **Keep it short:** aim for under about 20 lines, and treat 40 as the limit.
  A composable over about 60 lines needs extracted child composables.
- **One level of abstraction per function.** High-level steps call well-named
  helpers. Don't mix them with low-level string slicing.
  ```kotlin
  // Hard to read: mixes the steps with the details
  fun submit() {
      if (state.value.email.isBlank() || !state.value.email.contains("@")) { /* ... */ }
      val body = UpdateRequestDto(state.value.email.trim().lowercase(), /* ... */)
      viewModelScope.launch { /* 25 more lines */ }
  }

  // Readable: a short story, details live in named helpers
  fun submit() {
      val email = state.value.email.trim()
      if (!isValidEmail(email)) return showEmailError()
      saveProfile(email)
  }
  ```
- **Return early** instead of nesting: guard clauses first, the happy path last
  and un-indented.
  ```kotlin
  fun discountFor(user: UserModel?): Int {
      if (user == null) return 0
      if (!user.isPremium) return 0
      return if (user.yearsActive >= 3) 20 else 10
  }
  ```
- **Few parameters:** three or fewer is ideal. Beyond four, group them in a data
  class. Use **named arguments** at call sites with more than two parameters,
  and always for booleans: `loadFlowers(forceRefresh = true)`.
- **No boolean flag parameters that switch behavior** (`save(isDraft: Boolean)`).
  Write two functions instead (`saveDraft()`, `publish()`).
- **No hidden side effects.** A function called `getX` must not also write,
  navigate or log.

## 4. Kotlin idioms that help reading

Use idioms when they make code **clearer**. Avoid them when they only make it
shorter.
- Use `val` by default, `var` only when it must change. Use read-only `List`/`Map`
  in APIs.
- Use expression bodies for one-liners:
  `fun isEmpty() = items.isEmpty()`.
- Use `when` instead of `if/else if` chains. Over sealed types, make it
  exhaustive, with **no `else -> {}`**.
- Use null safety with `?.`, `?:` and early `return`, and **never `!!`**.
  Prefer `requireNotNull(x) { "reason" }` when null is a real bug.
- Scope functions: at most **one per expression, never nested**. `it` is fine in
  one-line lambdas. Name the parameter when the lambda is longer or nested.
  ```kotlin
  // Hard to read
  user?.let { it.address?.let { a -> a.city?.let { println(it) } } }
  // Readable
  val city = user?.address?.city ?: return
  showCity(city)
  ```
- Collection operators read like a sentence. Put each step on its own line once
  there are more than two:
  ```kotlin
  val activeFlowerNames = flowers
      .filter { it.isActive }
      .sortedBy { it.name }
      .map { it.name }
  ```
- Extract a complex condition into a named `val` or function:
  ```kotlin
  val canSubmit = email.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH && !isLoading
  ```
- Use **trailing commas** in multi-line parameter and argument lists, for cleaner diffs.
- Use **imports, not fully qualified names** inline
  (`com.example.flowers.domain.model.FlowerModel` in the middle of a line is noise).
- Use `Duration` (`30.seconds`) instead of raw milliseconds where the API allows.

## 5. Structure and files

- **One main type per file, named after it.** `FlowerListSource` lives in
  `FlowerListSource.kt`, not `NoticeRemoteSource.kt`. An entry function for
  `forceUpdate` lives in `ForceUpdateEntry.kt`, not `DetailsEntry.kt`.
- **Order inside a class:** properties, `init`, public functions, then private
  helpers, in the order they are called. A reader scrolls down and meets each
  helper right after its first use.
- **Group related code** and separate the groups with one blank line. Don't
  add banner comments like `// ======= HELPERS =======`.
- **Package by feature, then layer**, as the template does (`flowers/data`,
  `flowers/domain`, `flowers/presentation`). Never a global `utils` package
  full of unrelated helpers.
- **Visibility:** `private` / `internal` by default. Public API is what other
  modules really need.
- **Composable files:** the stateful screen at the top, then the stateless
  content, then small private child composables, then `@Preview`s at the bottom.

## 6. Comments

Good code explains **what** and **how**. Comments explain **why**.
- **Write a comment** for a business rule, a workaround, a non-obvious
  decision, a platform quirk, or a link to a ticket or spec:
  ```kotlin
  // Room already defaults to Dispatchers.IO; setting it explicitly breaks on Kotlin/Native.
  ```
- **KDoc** (`/** ... */`) on public APIs of `core` modules and on anything
  surprising. Keep it to one or two lines that say what it returns and when it fails.
- **Don't write comments that repeat the code** (`// increment counter`,
  `// constructor`). Rename the code instead.
- **Never leave commented-out code.** Git remembers it. The template has large
  commented-out blocks (e.g. in `FlowerListViewModel`, `MainNavigation`). Don't
  copy that pattern into new code, and remove such blocks when you touch a file.
- **TODOs** only with a reason, and only if the user asked to leave work for
  later: `// TODO(touhid): replace with real version-check endpoint`.
- Keep comments true. When you change code, update or delete its comment.

## 7. Compose-specific readability

- `modifier: Modifier = Modifier` is the **first optional parameter**. Apply it to
  the root element only.
- Parameters go in this order: required data, `modifier`, optional config, then
  event lambdas (`onClick`, `onRetry`) last, so a trailing lambda reads naturally.
- Hoist state. Stateless composables take `state` and `onEvent`, which keeps
  them previewable and readable.
- Put each modifier on its own line in a chain longer than two calls:
  ```kotlin
  Modifier
      .fillMaxWidth()
      .padding(AppTheme.spacing.lg)
      .clickable(onClick = onClick)
  ```
- Don't put logic in the composition. Compute `val`s above the layout, or in the
  ViewModel.
- Name `remember`ed values for what they hold (`scrollState`, `focusManager`).

## 8. Errors and edge cases, readably

- Make failure visible in the type (`ResultNet<T, NetworkError>`), not through
  `null` meaning "something went wrong".
- Use `require()` for bad arguments, `check()` for bad state, and `error("...")` for
  impossible branches, each with a message a human understands.
- Handle edge cases explicitly, with named cases: empty list, first page, last
  page, offline.

## 9. Formatting

- Use 4-space indentation and a maximum line length of about 120 characters. Let
  the IDE formatter (or `ktlint`/`detekt`, if the project has them) decide the
  rest. Don't fight it.
- Use one blank line between functions and no more than one blank line in a row.
  Remove trailing whitespace and unused imports.
- Use string templates instead of concatenation:
  `"Total: $count"`, not `"Total: " + count`.

## 10. Refactoring for readability

When asked to make code readable, or when touching hard-to-read code:
1. Make sure tests cover the behavior first. Write characterization tests if
   needed (TDD rule from `kmp-feature-scaffold`).
2. Rename unclear names, extract functions and named conditions, flatten nesting
   with early returns, delete dead and commented-out code, and split oversized
   files and composables.
3. Change **behavior and readability in separate steps or commits**, so review stays easy.
4. List what changed in the file report (`kmp-feature-scaffold` section 13).

## 11. Review checklist

- [ ] Clean-code principles hold: SRP, no duplication (rule of three), KISS/YAGNI, dependencies injected, no magic values, no empty catches
- [ ] Size signals respected (function ~20 lines, class ~200, ≤4 deps, ≤3 nesting levels)
- [ ] Names say what things are and do; booleans read as questions; no unclear abbreviations
- [ ] Functions do one thing, are about 20 lines or fewer, use early returns, and have at most 3–4 params (named args for booleans)
- [ ] `val`, read-only collections, no `!!`, exhaustive `when` without `else -> {}`
- [ ] At most one scope function per expression, never nested; named lambda params when not trivial
- [ ] Imports instead of fully qualified names; trailing commas; string templates
- [ ] One type per file with a matching file name; class members ordered top-down; `private`/`internal` by default
- [ ] Comments explain *why*; no commented-out code, no stale comments, no TODOs without reason
- [ ] Compose: `modifier` first optional param, lambdas last, hoisted state, one modifier per line
- [ ] Formatter clean, no unused imports, lines ≤ ~120 chars
