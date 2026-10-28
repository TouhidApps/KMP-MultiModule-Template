---
name: web-admin-landing
description: Build the web side of a KMP app (admin portal + landing page) with a Next.js multi-module monorepo and a PHP + MySQL modular API, all under backend/ at the KMP project root, using server-driven UI, RSC/SSR/ISR and edge rendering, test-driven development, a scalable stateless design, and an error log for every API with an admin error panel filtered by date, push notifications sent from the admin portal with push analytics, and admin-editable, versioned Privacy Policy and Terms & Conditions pages with user consent records. Use when adding or changing the admin portal, landing page, web API, MySQL schema, API error logging, push notifications and push analytics, or the privacy policy, terms and conditions or other legal pages.
---

# Admin Portal + Landing Page (Next.js + PHP + MySQL)

The web side of the product lives **inside the KMP repo**, in `backend/`, next to
`app/ core/ feature/`. It has three parts:

- **API**: PHP (Laravel) + MySQL, split into modules. It is the single source of
  truth for the admin portal, the landing page **and the KMP mobile app**.
- **Landing page**: Next.js App Router, statically rendered and cached at the
  CDN edge, with sections driven by the server (the API says what to show).
- **Admin portal**: Next.js App Router, server-rendered per request with React
  Server Components and Server Actions. The browser never calls PHP directly.

**Work test-first (TDD).** For every behavior, write the failing test before the
code. Section 10 gives the order and the patterns.

Related skills: the KMP client of this API follows `kmp-networking-ktor`, its
URLs per environment follow `kmp-environments`, security tiering follows
`kmp-security`, layering ideas follow `kmp-clean-architecture`, and readable
code follows `kotlin-readable-code` (the same rules apply to PHP and TypeScript).
Maintenance and update alerts for the app are driven from section 6b, and the
app side is `kmp-update-maintenance-alerts`. Push notifications, their admin
page and push analytics are section 6c; the app side is
`kmp-push-notifications`. The Privacy Policy and Terms & Conditions pages,
their versions and consent records are section 6d; the app side is
`kmp-legal-consent`. Every API error is logged and shown
in the admin error panel (section 12c); the app's analytics and error reporting
are in `kmp-analytics-logging`.

## 1. Read the project first

Before adding anything, check what is already there:
```bash
ls backend/ backend/api/modules backend/web/apps backend/web/packages 2>/dev/null
cat backend/web/package.json backend/api/composer.json 2>/dev/null
```
If `backend/` exists, follow it. If it differs from this skill, keep its layout
and apply the same ideas (one-way module graph, API is the only door to MySQL,
browser never talks to PHP from the admin portal). If it does not exist, create
the layout in section 2.

Default stack (use the latest stable versions at the time of work, and pin them):
PHP 8.3+, Laravel 12, MySQL 8.4 LTS (InnoDB, `utf8mb4`), Redis, Node LTS, pnpm,
Turborepo, Next.js App Router (15 or 16), React 19, TypeScript `strict`,
Tailwind CSS, Zod. Tests: Pest (PHP), Vitest + Testing Library + MSW (web),
Playwright (end-to-end and screenshots).

## 2. Layout (required)

```
<kmp-project-root>/
  app/  core/  feature/              # KMP app (untouched by this skill)
  project.yaml                       # Amper; backend/ is NOT an Amper module
  backend/
    README.md                        # how to run everything locally
    docker-compose.yml               # mysql, redis, api, landing, admin, mailpit
    .env.example                     # documented, no real secrets
    contracts/
      openapi.yaml                   # the API contract: web + mobile both use it
    api/                             # PHP + MySQL (Laravel), modular monolith
      composer.json
      bootstrap/  config/  public/index.php  routes/api.php
      modules/
        Shared/                      # kernel: envelope, errors, auth guard, base value objects
        Auth/                        # admin login, tokens, roles + permissions
        Cms/                         # landing page content + server-driven page schema
        Users/                       # app users (seen by admin, used by mobile)
        <Feature>/                   # one folder per business area
      tests/Architecture/            # module boundary tests (Pest arch)
    web/                             # Next.js multi-module monorepo
      package.json  pnpm-workspace.yaml  turbo.json  tsconfig.base.json
      apps/
        landing/                     # public site: static + ISR + edge
        admin/                       # admin portal: SSR + RSC + Server Actions
      packages/
        api-client/                  # typed fetch client generated from openapi.yaml
        sdui/                        # server-driven section schema (Zod) + renderer registry
        ui/                          # design system: tokens, components (shadcn-style, own code)
        auth/                        # session cookie + token verification helpers (server-only)
        config/                      # eslint, tsconfig, tailwind preset, vitest preset
      e2e/                           # Playwright tests + screenshot capture
    docker/
      php/Dockerfile  nginx/default.conf  node/Dockerfile
```

Rules:
- The landing page is **`backend/web/apps/landing`**. The admin portal is
  **`backend/web/apps/admin`**. Both are inside `backend/`, as the user asked.
- `backend/` never imports from `app/ core/ feature/`, and the KMP code never
  imports from `backend/`. They share **only** `backend/contracts/openapi.yaml`.
- Add to the root `.gitignore`: `backend/**/node_modules`, `backend/**/.next`,
  `backend/**/.turbo`, `backend/api/vendor`, `backend/**/.env`,
  `backend/**/coverage`, `backend/web/e2e/test-results`.
- Each deployable (api, landing, admin) has its own Dockerfile and can scale on
  its own.

## 3. Module rules (never break these)

### Web (Next.js monorepo)
```
apps/landing ─┐                 ┌─> packages/sdui ─┐
              ├─> packages/ui ──┤                  ├─> packages/api-client
apps/admin  ──┘                 └─> packages/auth ─┘
                          (all) ─> packages/config  (dev only)
```
- Apps depend on packages. **Packages never depend on apps.** An app never
  imports another app.
- `packages/ui` is presentational only: no `fetch`, no cookies, no env vars.
- `packages/auth` and server code in `api-client` start with
  `import "server-only"` so they can't end up in a browser bundle.
- Each package has `package.json` with `"name": "@web/<name>"`, an `exports`
  map and its own `vitest.config.ts`. No deep imports (`@web/ui/src/...`).
- Enforce the graph with ESLint `import/no-restricted-paths` (or
  `eslint-plugin-boundaries`) in `packages/config`.

`turbo.json`:
```json
{
  "$schema": "https://turbo.build/schema.json",
  "tasks": {
    "build": { "dependsOn": ["^build"], "outputs": [".next/**", "!.next/cache/**", "dist/**"] },
    "test": { "dependsOn": ["^build"], "outputs": ["coverage/**"] },
    "lint": {},
    "typecheck": { "dependsOn": ["^build"] },
    "dev": { "cache": false, "persistent": true }
  }
}
```

### API (PHP modules)
Each module is a small clean-architecture slice:
```
modules/Cms/
  CmsServiceProvider.php            # binds interfaces, loads routes + migrations
  Domain/                           # pure PHP: entities, value objects, repository interfaces
  Application/                      # use cases (one class, one public __invoke), DTOs
  Infrastructure/                   # Eloquent models, repository implementations, cache
  Http/                             # controllers, form requests, resources, routes.php
  Database/migrations/
  Tests/Unit/  Tests/Feature/
```
- Dependency rule: `Http → Application → Domain`, and
  `Infrastructure → Domain`. **Domain imports nothing from Laravel.**
- A module talks to another module **only** through that module's
  `Application` interfaces (or a domain event). Never through its Eloquent
  models or tables. No cross-module foreign keys except to `users` / `admins`.
- Register modules in `composer.json` (`"Modules\\": "modules/"`) and in
  `bootstrap/providers.php`. Don't use a third-party modules package.
- Enforce boundaries with a Pest architecture test:
```php
// tests/Architecture/ModulesTest.php
arch('domain is framework free')
    ->expect('Modules\*\Domain')
    ->not->toUse(['Illuminate', 'Laravel']);

arch('modules only see other modules through Application')
    ->expect('Modules\Cms')
    ->not->toUse(['Modules\Users\Infrastructure', 'Modules\Users\Domain']);
```

## 4. API contract (shared by web and mobile)

- All routes are under `/api/v1`. Breaking changes go to `/api/v2`; never break
  `v1` while a shipped mobile build still uses it.
- `backend/contracts/openapi.yaml` is written **before** the endpoint (it is part
  of the test-first step). The web client types are generated from it:
  `pnpm --filter @web/api-client generate` (`openapi-typescript`).
- Every response uses one envelope. It matches `ApiEnvelope<T>` /
  `ApiErrorDto` in `kmp-networking-ktor`, so the KMP app decodes it directly:
```json
{ "data": { "id": 7, "title": "Hello" }, "error": null, "meta": { "page": 1, "perPage": 20, "total": 134 } }
{ "data": null, "error": { "code": "validation_failed", "message": "Title is required", "fields": { "title": ["required"] } } }
```
- Status codes: 200/201 success, 401 not signed in, 403 no permission, 404,
  409 conflict, 422 validation, 429 rate limited, 5xx server. The `error.code`
  is a stable snake_case string; the `message` is for logs. Clients show their
  own localized text.
- Lists are paginated (`?page=&perPage=`, max 100) or cursor-based for feeds.
  Never return an unbounded list.
- Times are ISO-8601 UTC strings. Money is integer minor units plus currency.
  IDs are opaque to clients (use ULIDs for public IDs).

Envelope helper in `modules/Shared`:
```php
final class ApiResponse
{
    public static function ok(mixed $data, array $meta = [], int $status = 200): JsonResponse
    {
        return response()->json(['data' => $data, 'error' => null, 'meta' => $meta ?: null], $status);
    }

    public static function fail(string $code, string $message, int $status, array $fields = []): JsonResponse
    {
        $error = ['code' => $code, 'message' => $message] + ($fields ? ['fields' => $fields] : []);
        return response()->json(['data' => null, 'error' => $error], $status);
    }
}
```
Map exceptions to the envelope once, in `bootstrap/app.php`
(`->withExceptions(...)`), so controllers never build error JSON by hand.

## 5. PHP + MySQL for scale

**Stateless API.** No file sessions, no local uploads. Sessions, cache, queues
and rate limits use Redis. Uploads go to S3-compatible object storage. Any number
of API containers can run behind the load balancer.

**MySQL rules:**
- InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`, `BIGINT UNSIGNED` auto-increment
  primary keys internally, plus a `CHAR(26)` ULID `public_id` with a unique index.
- Every schema change is a migration in the module's `Database/migrations`.
  Migrations are additive and backward-compatible (expand, deploy, then contract
  in a later release). Never edit a migration that already ran in UAT or PROD.
- Index every foreign key and every column used in `WHERE` / `ORDER BY` on lists.
  Check new list queries with `EXPLAIN`; no full table scans on large tables.
- Prevent N+1: eager load (`with()`), and turn on
  `Model::preventLazyLoading(! app()->isProduction())`.
- Use transactions for multi-row writes. Use `lockForUpdate()` only for short
  critical sections (counters, balances).
- Configure a read replica (`'read' => [...], 'write' => [...]` with `sticky => true`)
  so reads scale separately. Use `PDO` prepared statements only (Eloquent / query
  builder); never build SQL with string concatenation.
- Soft-delete admin-managed content (`deleted_at`) and keep an `audit_logs`
  table (who, what, when, before/after) written by the `Shared` module.

**Use case example (Application layer):**
```php
namespace Modules\Cms\Application;

final class PublishPage
{
    public function __construct(
        private readonly PageRepository $pages,
        private readonly PageCache $cache,
        private readonly Clock $clock,
    ) {}

    public function __invoke(string $pageId, AdminId $by): Page
    {
        $page = $this->pages->get($pageId) ?? throw new PageNotFound($pageId);
        $published = $page->publish($this->clock->now(), $by);   // rules live in Domain
        $this->pages->save($published);
        $this->cache->invalidate($published->slug);               // also pings Next.js revalidation
        return $published;
    }
}
```
Controllers only validate (Form Request), call one use case and return a
Resource through `ApiResponse::ok(...)`.

**Background work.** Emails, image resizing, exports and webhook calls go to
queued jobs (Redis queue, `php artisan queue:work` as its own container). The
request returns fast.

## 6. Server-driven UI (landing page and more)

The API decides **what** the landing page shows; Next.js decides **how** it
looks. Admins edit pages in the admin portal without a web deploy.

The `Cms` module serves a page schema:
```json
GET /api/v1/pages/home?locale=en
{ "data": {
    "slug": "home", "locale": "en", "version": 12,
    "seo": { "title": "MyApp", "description": "…", "ogImage": "https://cdn…/og.png" },
    "sections": [
      { "id": "s1", "type": "hero",     "props": { "title": "…", "subtitle": "…", "cta": { "label": "Get the app", "href": "/download" } } },
      { "id": "s2", "type": "features", "props": { "items": [ { "icon": "shield", "title": "…", "body": "…" } ] } },
      { "id": "s3", "type": "faq",      "props": { "items": [ { "q": "…", "a": "…" } ] } }
    ] } }
```

`packages/sdui` owns the schema and the registry:
```ts
// packages/sdui/src/schema.ts
import { z } from "zod";

export const heroSection = z.object({
  id: z.string(),
  type: z.literal("hero"),
  props: z.object({
    title: z.string(),
    subtitle: z.string().optional(),
    cta: z.object({ label: z.string(), href: z.string() }).optional(),
  }),
});
// featuresSection, faqSection, … defined the same way

export const knownSection = z.discriminatedUnion("type", [heroSection, featuresSection, faqSection]);
export type Section = z.infer<typeof knownSection>;

/** Keeps the sections this build knows and drops the rest, so a newer API never breaks an older site. */
export function parseSections(raw: unknown[]): Section[] {
  return raw.flatMap((item) => {
    const parsed = knownSection.safeParse(item);
    return parsed.success ? [parsed.data] : [];
  });
}
```
```tsx
// packages/sdui/src/render.tsx  (a Server Component: no "use client")
import type { ComponentType } from "react";
import { Hero, Features, Faq } from "@web/ui";
import type { Section } from "./schema";

const registry: { [T in Section["type"]]: ComponentType<Extract<Section, { type: T }>["props"]> } = {
  hero: Hero,
  features: Features,
  faq: Faq,
};

export function RenderSections({ sections }: { sections: Section[] }) {
  return sections.map((section) => {
    const Component = registry[section.type] as ComponentType<typeof section.props>;
    return <Component key={section.id} {...section.props} />;
  });
}
```
Rules:
- Unknown or invalid sections are skipped and logged, never a crash.
- Section props are **content only** (text, links, image URLs, icon names from
  our own set). No raw HTML, CSS or scripts from the API. If rich text is
  needed, send a limited Markdown subset and sanitize it on render.
- Adding a section type = new Zod schema + component + registry entry + tests,
  then the admin form for it. Release the web change **before** admins can use it.
- The same schema can drive a KMP screen (e.g. a promo/home feed): the Kotlin
  side decodes `sections` into a sealed class and skips unknown `type`s the same
  way. Keep the contract in `openapi.yaml`.

## 6b. App status: maintenance and update alerts (admin → mobile app)

Admins turn on maintenance, a soft update or a force update for the mobile app
from the admin portal, with no app release. The app side is in
`kmp-update-maintenance-alerts`. UAT and PROD each have their own settings
because each has its own database (section 12b): changing UAT never affects
PROD users.

### Module `AppStatus`
```
modules/AppStatus/
  Domain/
    Platform.php                  # enum: android, ios, desktop, web
    ReleasePolicy.php             # latestVersion, latestBuild, minSupportedBuild, softUpdateEnabled, storeUrl, remindAfterHours, texts
    MaintenanceWindow.php         # startsAt, endsAt, noticeMinutes, platforms, texts, status
    UpdateDecision.php            # pure: decide(policy, build) -> none|soft|force
    AppStatusRepository.php       # interface
  Application/
    GetAppStatus.php              # public read, cached
    SaveReleasePolicy.php         # admin
    ScheduleMaintenance.php  StartMaintenanceNow.php  EndMaintenance.php  CancelMaintenance.php
  Infrastructure/  EloquentAppStatusRepository.php  AppStatusCache.php
  Http/
    PublicAppStatusController.php  AdminReleasePolicyController.php  AdminMaintenanceController.php
    Middleware/BlockDuringMaintenance.php
    routes.php
  Database/migrations/
  Tests/Unit/  Tests/Feature/
```

Tables (additive migrations, section 5 rules):
```sql
app_release_policies (
  id BIGINT UNSIGNED PK, platform ENUM('android','ios','desktop','web') UNIQUE,
  latest_version VARCHAR(20), latest_build INT UNSIGNED, min_supported_build INT UNSIGNED,
  soft_update_enabled BOOL, remind_after_hours SMALLINT UNSIGNED DEFAULT 24,
  store_url VARCHAR(500), texts JSON NULL,          -- {"en":{"title":..,"message":..},"bn":{..}}
  updated_by BIGINT UNSIGNED, created_at, updated_at
)
maintenance_windows (
  id BIGINT UNSIGNED PK, public_id CHAR(26) UNIQUE,
  starts_at DATETIME(0), ends_at DATETIME(0) NULL, notice_minutes SMALLINT UNSIGNED DEFAULT 60,
  platforms JSON, texts JSON NULL,
  status ENUM('scheduled','ended','cancelled'), created_by, ended_by NULL, created_at, updated_at,
  INDEX (status, starts_at)
)
```

Domain rules (each one has a unit test):
- `UpdateDecision::decide`: `build < minSupportedBuild` → `force`;
  else `softUpdateEnabled && build < latestBuild` → `soft`; else `none`.
  No policy for the platform → `none`.
- `minSupportedBuild <= latestBuild`. `latestVersion` matches `^\d+\.\d+\.\d+$`.
- `storeUrl` is required, `https`, and its host is in an allowlist
  (`play.google.com`, `apps.apple.com`, `testflight.apple.com`,
  `appdistribution.firebase.dev`). Texts are plain text: title ≤ 60, message ≤ 300,
  tags stripped, only locales the app supports (`en`, `bn`).
- A maintenance window is **active** when `status = scheduled` and
  `starts_at <= now` and (`ends_at` is null or `now < ends_at`); **upcoming** when
  `now` is within `notice_minutes` before `starts_at`. `ends_at > starts_at`.
  Only one window may overlap another for the same platform (409 `maintenance_overlap`).
- Times are stored and returned in UTC.

### Endpoints (write them in `openapi.yaml` first)
```
GET  /api/v1/app/status?platform=&build=&version=&locale=     public, throttle 60/min per IP
GET  /api/v1/admin/app-status                                  app_status.view
PUT  /api/v1/admin/app-status/release-policies/{platform}      app_status.manage
POST /api/v1/admin/app-status/maintenance                      app_status.manage   (schedule, or start now)
POST /api/v1/admin/app-status/maintenance/{id}/end             app_status.manage
POST /api/v1/admin/app-status/maintenance/{id}/cancel          app_status.manage
```
Public response (the KMP app's contract, `kmp-update-maintenance-alerts` section 3):
```json
{ "data": {
    "update": { "type": "soft", "latestVersion": "1.2.0", "latestBuild": 15, "minSupportedBuild": 10,
                "storeUrl": "https://play.google.com/store/apps/details?id=com.touhidapps.kmp",
                "remindAfterHours": 24, "title": null, "message": "Faster checkout and bug fixes." },
    "maintenance": null,
    "serverTime": "2026-11-02T19:10:00Z" },
  "error": null }
```
```yaml
# backend/contracts/openapi.yaml (excerpt)
AppStatus:
  type: object
  required: [update, maintenance, serverTime]
  properties:
    update:
      type: object
      required: [type, latestVersion, latestBuild, minSupportedBuild, storeUrl, remindAfterHours]
      properties:
        type: { type: string, enum: [none, soft, force] }
        latestVersion: { type: string, example: "1.2.0" }
        latestBuild: { type: integer, minimum: 0 }
        minSupportedBuild: { type: integer, minimum: 0 }
        storeUrl: { type: string, format: uri, nullable: true }
        remindAfterHours: { type: integer, minimum: 1, maximum: 168 }
        title: { type: string, maxLength: 60, nullable: true }
        message: { type: string, maxLength: 300, nullable: true }
    maintenance:
      nullable: true
      type: object
      required: [id, phase, startsAt]
      properties:
        id: { type: string, minLength: 26, maxLength: 26 }
        phase: { type: string, enum: [upcoming, active] }
        startsAt: { type: string, format: date-time }
        endsAt: { type: string, format: date-time, nullable: true }
        title: { type: string, maxLength: 60, nullable: true }
        message: { type: string, maxLength: 300, nullable: true }
    serverTime: { type: string, format: date-time }
```
- Unknown `platform` → 422. Missing `build` → 422 (`build` is an integer ≥ 0).
  Unknown `locale` → falls back to `en` texts, then `null`.
- `GetAppStatus` reads the policy and the next window from Redis
  (`AppStatusCache`, key `app_status:{platform}`, 30 s TTL) and decides per
  request, so the cache isn't split by build. Every admin change calls
  `AppStatusCache::forget()`. Response header `Cache-Control: public, max-age=30`.
- Never return `latestBuild` data for a platform without a policy: return
  `type: none` with empty fields.

**Maintenance middleware.** `BlockDuringMaintenance` runs on every mobile route
(`/api/v1/*` except `app/status`, `health`, and everything under `admin/`). While a
window is active for the caller's platform (`X-App-Platform` header, which the KMP
`HttpClient` sends on every request; no header = all platforms), it answers:
```
HTTP/1.1 503 Service Unavailable
Retry-After: 1800
X-App-Maintenance: 1
{ "data": null, "error": { "code": "maintenance", "message": "Scheduled maintenance" } }
```
The admin portal and landing page keep working, so admins can end maintenance.

### Admin portal page: `apps/admin/app/(dashboard)/app-status/`
```
app-status/
  page.tsx                 # RSC: loads GET /admin/app-status
  actions.ts               # Server Actions: savePolicy, scheduleMaintenance, startNow, end, cancel
  schema.ts                # Zod schemas shared by the forms and actions
  ReleasePolicyForm.tsx    # client component, useActionState
  MaintenancePanel.tsx
  AlertPreview.tsx         # phone-sized preview of the dialog the app will show
```
Layout:
- **Header:** environment badge ("UAT" or "PROD", PROD in the error color), so an
  admin always knows which users they are about to affect.
- **Update policy card**, tabs per platform (Android, iOS). Fields: latest
  version, latest build, minimum supported build, "Show soft update" switch,
  remind after (hours), store URL, optional title and message per language
  (English, Bangla). A live `AlertPreview` shows the soft and force dialogs with
  the entered text (or the app's default text when empty).
- **Maintenance card:** current or next window with its state chip
  (Upcoming / Active / Ended), "End now" and "Cancel" buttons, and a form to
  schedule one (platforms, start, end, notice minutes, texts) or "Start now".
- **History:** the last 20 audit log entries for this module (who, what, when, before/after).

Safety rules:
- Raising `minSupportedBuild` (force update) and "Start now" open a confirm
  dialog that names the environment and the effect ("All Android users below
  build 15 in PROD will be blocked until they update"). In PROD the admin must
  type the platform name to confirm.
- Times are entered in the admin's local time zone, shown with the zone, and sent as UTC.
- Disable "Save" while pending; show field errors from PHP `error.fields`.

```ts
// app-status/schema.ts
import { z } from "zod";

const text = z.object({
  title: z.string().trim().max(60).optional(),
  message: z.string().trim().max(300).optional(),
});
const storeHosts = ["play.google.com", "apps.apple.com", "testflight.apple.com", "appdistribution.firebase.dev"];

export const releasePolicySchema = z
  .object({
    platform: z.enum(["android", "ios"]),
    latestVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
    latestBuild: z.coerce.number().int().min(0),
    minSupportedBuild: z.coerce.number().int().min(0),
    softUpdateEnabled: z.coerce.boolean(),
    remindAfterHours: z.coerce.number().int().min(1).max(168),
    storeUrl: z.string().url().refine((u) => {
      const url = new URL(u);
      return url.protocol === "https:" && storeHosts.includes(url.hostname);
    }, "store_url_not_allowed"),
    texts: z.object({ en: text, bn: text }).partial(),
  })
  .refine((p) => p.minSupportedBuild <= p.latestBuild, {
    path: ["minSupportedBuild"],
    message: "min_above_latest",
  });
```

### Tests (write first, section 10 order)
1. **Contract:** `AppStatus` schema and the six paths in `openapi.yaml`.
2. **Domain (Pest unit):** `UpdateDecision` table (below min → force, between → soft,
   soft disabled → none, equal to latest → none, no policy → none); window phase
   (before notice → none, in notice → upcoming, after start → active, after end → none,
   cancelled → none); policy rejects `min > latest`, `http://` and unknown hosts.
3. **Use cases** with in-memory repository + `FixedClock`: saving a policy forgets
   the cache and writes an audit log; overlapping windows → `MaintenanceOverlap`.
4. **Feature (MySQL):** public status returns `soft` for build 12 when latest is 15;
   `force` for build 9 when min is 10; validates against `openapi.yaml`; 422 for a
   bad platform; admin endpoints return 403 without `app_status.manage`; during an
   active window `/api/v1/profile` returns 503 with `X-App-Maintenance: 1` and
   `error.code = maintenance`, while `/api/v1/app/status` and `/api/v1/admin/*`
   still work.
5. **Web:** `releasePolicySchema` cases; Server Actions (invalid input, API error,
   success revalidates `/app-status`); `AlertPreview` renders custom text and
   falls back to defaults.
6. **E2E (Playwright, UAT seed):** admin enables soft update for Android →
   `GET {BASE}/api/v1/app/status?platform=android&build=12` returns `soft`;
   admin starts maintenance → a mobile endpoint returns 503; admin ends it → 200.
   Screenshot the page in UAT with each card filled in.

## 6c. Push notifications and push analytics (admin → mobile app)

Admins write, target, schedule and send push notifications from the admin
portal, and see how each one did. The app side (FCM, push list, tap actions)
is `kmp-push-notifications`. UAT and PROD each have their own database **and**
their own Firebase project (section 12b, `kmp-environments` 2), so a UAT send
can only reach UAT builds.

Push types (the `type` field, shared with the app):

| `type` | Extra fields | Tap in the app opens |
|---|---|---|
| `default` | none | the app's push list, this push highlighted |
| `deeplink` | `target` (one of `PushDeepLinkTarget`) + `params` | that app page |
| `app_update` | none (the app uses the release policy's store URL, 6b) | Play Store / App Store page of the app |
| `web_url` | `url` (`https`) | the page in the app's in-app browser |

**Keep all history.** Pushes, recipients and events are never deleted. Admins
can **hide** a push (it leaves the app's list, its data and stats stay). The
app's inbox returns only the latest 30 per device.

### Module `Push`
```
modules/Push/
  Domain/
    PushType.php  DeepLinkTarget.php          # enums; DeepLinkTarget lists params per target
    PushMessage.php                           # texts per locale, type, target, params, url, image, audience, status
    Audience.php                              # platforms, locales, userIds, minBuild, maxBuild, testOnly
    PushStatus.php                            # draft, scheduled, sending, sent, cancelled, failed
    PushDevice.php  PushEventType.php
    Inbox.php                                 # const LIMIT = 30
    PushRepository.php  PushDeviceRepository.php  PushSender.php   # interfaces
  Application/
    RegisterDevice.php  UpdateDevice.php  GetInbox.php  RecordPushEvents.php
    CreatePush.php  CancelPush.php  HidePush.php  SendTestPush.php  PreviewAudience.php
    DispatchPush.php                          # queued: fan-out in chunks
    GetPushStats.php  GetPushTimeline.php  GetPushOverview.php
  Infrastructure/
    FcmPushSender.php                         # FCM HTTP v1 (kreait/firebase-php)
    FcmMessageFactory.php                     # per-platform payload (below)
    EloquentPushRepository.php  EloquentPushDeviceRepository.php
  Http/
    PublicPushDeviceController.php  PublicPushInboxController.php  PublicPushEventController.php
    AdminPushController.php  AdminPushStatsController.php
    Middleware/AuthenticateDevice.php         # X-Device-Key → PushDevice
    routes.php
  Console/DispatchDuePushes.php               # schedule()->everyMinute()
  Database/migrations/
  Tests/Unit/  Tests/Feature/
```

Tables (additive migrations, section 5 rules):
```sql
push_devices (
  id BIGINT UNSIGNED PK, installation_id CHAR(36) UNIQUE, device_key_hash CHAR(64) UNIQUE,
  fcm_token VARCHAR(255) NULL UNIQUE, platform ENUM('android','ios'),
  user_id BIGINT UNSIGNED NULL, app_build INT UNSIGNED, app_version VARCHAR(20),
  locale VARCHAR(8), time_zone VARCHAR(40), notifications_enabled BOOL,
  token_invalid_at DATETIME NULL, last_seen_at DATETIME, created_at, updated_at,
  INDEX (platform, notifications_enabled, token_invalid_at), INDEX (user_id)
)
push_messages (
  id BIGINT UNSIGNED PK, public_id CHAR(26) UNIQUE, type ENUM('default','deeplink','app_update','web_url'),
  texts JSON,                                  -- {"en":{"title":..,"body":..},"bn":{..}}; en required
  target VARCHAR(40) NULL, params JSON NULL, url VARCHAR(2048) NULL, image_url VARCHAR(2048) NULL,
  audience JSON, status ENUM('draft','scheduled','sending','sent','cancelled','failed'),
  scheduled_at DATETIME NULL, sent_at DATETIME NULL, hidden_at DATETIME NULL,
  idempotency_key CHAR(36) UNIQUE, created_by BIGINT UNSIGNED, created_at, updated_at,
  INDEX (status, scheduled_at), INDEX (created_at)
)
push_recipients (                              -- one row per push per device; the inbox reads this
  id BIGINT UNSIGNED PK, push_id BIGINT UNSIGNED, device_id BIGINT UNSIGNED, platform ENUM('android','ios'),
  status ENUM('pending','sent','failed'), error_code VARCHAR(40) NULL, sent_at DATETIME NULL,
  UNIQUE (push_id, device_id), INDEX (device_id, status, sent_at)
)
push_events (
  id BIGINT UNSIGNED PK, push_id BIGINT UNSIGNED, device_id BIGINT UNSIGNED, platform ENUM('android','ios'),
  event ENUM('received','opened','list_opened'), source ENUM('tray','list') NULL,
  occurred_at DATETIME, created_at,
  UNIQUE (push_id, device_id, event), INDEX (push_id, event, occurred_at)   -- the UNIQUE index also serves read_at
)
push_stats (                                   -- counters, one row per push per platform
  push_id BIGINT UNSIGNED, platform ENUM('android','ios'),
  targeted INT UNSIGNED, sent INT UNSIGNED, failed INT UNSIGNED,
  received INT UNSIGNED, opened INT UNSIGNED, list_opened INT UNSIGNED,
  PRIMARY KEY (push_id, platform)
)
push_test_devices (admin_id BIGINT UNSIGNED, device_id BIGINT UNSIGNED, label VARCHAR(60), PRIMARY KEY (admin_id, device_id))
```
`push_recipients` and `push_events` are append-only and narrow. When they pass
tens of millions of rows, move rows older than a year to `*_archive` tables
with the same schema (still queryable by the stats pages). Never delete them.

Domain rules (each one has a unit test):
- English title (1..65) and body (1..240) are required; other locales the app
  supports (`bn`) are optional and fall back to English. Plain text: tags
  stripped, no control characters.
- `deeplink` needs a known `target` and exactly its required params
  (`notice_details` → `id`, `chat` → `conversationId`), each 1..64 chars of
  `[A-Za-z0-9_-]`. `web_url` needs `url`: `https`, ≤ 2048, no userinfo, host
  not an IP address. `image_url`, when set, is `https`. Other types reject these fields.
- `DeepLinkTarget` cases equal the `PushDeepLinkTarget` enum in `openapi.yaml`
  (an arch/contract test reads the file), and the KMP app has the same test.
- `scheduled_at` is in the future and at most 30 days ahead. Only `draft` and
  `scheduled` can be cancelled; only `sent` can be hidden.
- Audience: no filter = every Android and iOS device with
  `notifications_enabled` and a valid token. `userIds` ≤ 1000. `testOnly` = the
  sending admin's `push_test_devices`.
- The FCM data payload (all fields as strings) must stay under 4 KB; reject the
  push at create time if it doesn't.

### Sending (queue, FCM HTTP v1)
```
CreatePush (send now) ─> status=sending ─> DispatchPush job
DispatchDuePushes (every minute) ─> due scheduled pushes ─> DispatchPush job
DispatchPush:
  for each chunk of 500 matching devices (keyset by device id):
    INSERT IGNORE push_recipients (pending)       # retry-safe: a device is never sent twice
    group by locale → FcmMessageFactory → FcmPushSender::sendMulticast(tokens)
    per result: sent | failed(error_code); UNREGISTERED / INVALID_ARGUMENT token → token_invalid_at = now
    push_stats += counts
  status = sent (or failed if every chunk failed), sent_at = now
```
- Runs on the Redis queue (`queue:work` container), `tries = 5`, exponential
  backoff; `QUOTA_EXCEEDED` / `UNAVAILABLE` / `INTERNAL` retry the chunk, other
  errors don't. One job per push, one chunk per child job, so a large send is
  spread over workers.
- `FcmMessageFactory` builds per platform:
  ```php
  // Android: data-only, high priority, so the app builds the notification and reports "received"
  ['token' => $t, 'data' => $data, 'android' => ['priority' => 'high', 'ttl' => '86400s',
      'fcm_options' => ['analytics_label' => $push->publicId]]]
  // iOS: APNs alert shown by the system, same data keys for the tap
  ['token' => $t, 'data' => $data,
   'apns' => ['headers' => ['apns-priority' => '10', 'apns-expiration' => (string) (time() + 86400)],
              'payload' => ['aps' => ['alert' => ['title' => $title, 'body' => $body], 'sound' => 'default',
                                      'mutable-content' => $push->imageUrl ? 1 : 0]],
              'fcm_options' => ['analytics_label' => $push->publicId, 'image' => $push->imageUrl]]]
  // $data = push_id, type, title, body, image, target, params (JSON string), url
  ```
- Credentials per environment: `FCM_PROJECT_ID` and `FCM_CREDENTIALS` (path to
  the service account JSON, mounted from the host's secret store, never in the
  repo). On boot, `PushServiceProvider` fails if the JSON's `project_id` differs
  from `FCM_PROJECT_ID`, so UAT can't be wired to the PROD Firebase project by mistake.
- Admin sends are idempotent: the form sends an `Idempotency-Key` (UUID); a
  repeat with the same key returns the first push.
- Rate limit sends per admin (`push.send`: 10 per minute) and audit-log each
  create, cancel, hide and test send (section 5).

### Endpoints (write them in `openapi.yaml` first)
```
POST /api/v1/push/devices                        public, throttle 10/min per IP → 201 { deviceKey } (once)
PUT  /api/v1/push/devices/{installationId}       X-Device-Key
GET  /api/v1/push/inbox?locale=                  X-Device-Key → latest 30 (Inbox::LIMIT)
POST /api/v1/push/events                         X-Device-Key, ≤ 50 events, throttle 30/min per device
GET  /api/v1/admin/push?from=&to=&type=&status=&q=&page=   push.view (all history, 20 per page)
POST /api/v1/admin/push                          push.send, Idempotency-Key header
GET  /api/v1/admin/push/{id}                     push.view (message + stats per platform)
GET  /api/v1/admin/push/{id}/timeline            push.view (opens per hour, first 72 h)
POST /api/v1/admin/push/{id}/cancel              push.send
POST /api/v1/admin/push/{id}/hide                push.send
POST /api/v1/admin/push/audience-preview         push.send → { android, ios, total }
POST /api/v1/admin/push/test                     push.send → sends to the admin's test devices
GET  /api/v1/admin/push/targets                  push.view → deep link targets and their params
GET  /api/v1/admin/push/overview?from=&to=       push.view → totals and rates for the range
PUT  /api/v1/admin/push/test-devices/{installationId}   push.send (label a device as "my test device")
```
- Device endpoints: `AuthenticateDevice` hashes `X-Device-Key` (SHA-256) and
  loads the device; unknown key → 401 `device_key_invalid`. A bearer user token
  on `PUT devices` links `user_id` (the mobile guard, section 8; if the app's
  users sign in with Supabase, the guard verifies the Supabase JWT with the
  project's JWKS); no bearer → `user_id = null`. A token already used by
  another device moves to this one.
- Inbox query (indexed, no scan):
  ```sql
  SELECT m.*, r.sent_at,
         (SELECT MIN(e.occurred_at) FROM push_events e
           WHERE e.push_id = r.push_id AND e.device_id = r.device_id
             AND e.event IN ('opened', 'list_opened')) AS read_at
  FROM push_recipients r
  JOIN push_messages m ON m.id = r.push_id AND m.hidden_at IS NULL
  WHERE r.device_id = ? AND r.status = 'sent'
  ORDER BY r.sent_at DESC LIMIT 30
  ```
  Texts are picked for `locale` (query, else the device's), falling back to `en`.
- `RecordPushEvents`: `INSERT IGNORE` each event; only rows actually inserted
  increment `push_stats` (so retries and double taps count once). Events for a
  push the device never received → ignored. `occurredAt` more than 7 days in
  the future or before the push's `sent_at` → clamped to now.
- During maintenance (6b) the device endpoints answer 503 like other mobile
  routes; the app keeps its cache and queued events.

```yaml
# backend/contracts/openapi.yaml (excerpt)
PushType: { type: string, enum: [default, deeplink, app_update, web_url] }
PushDeepLinkTarget: { type: string, enum: [home, notices, notice_details, profile, settings, chat, more] }
InboxItem:
  type: object
  required: [id, type, title, body, sentAt]
  properties:
    id: { type: string, minLength: 26, maxLength: 26 }
    type: { $ref: "#/components/schemas/PushType" }
    title: { type: string, maxLength: 65 }
    body: { type: string, maxLength: 240 }
    imageUrl: { type: string, format: uri, nullable: true }
    target: { allOf: [{ $ref: "#/components/schemas/PushDeepLinkTarget" }], nullable: true }
    params: { type: object, additionalProperties: { type: string }, nullable: true }
    url: { type: string, format: uri, nullable: true }
    sentAt: { type: string, format: date-time }
    readAt: { type: string, format: date-time, nullable: true }
PushStats:
  type: object
  required: [platform, targeted, sent, failed, received, opened, listOpened]
  properties:
    platform: { type: string, enum: [android, ios, all] }
    targeted: { type: integer }
    sent: { type: integer }
    failed: { type: integer }
    received: { type: integer, description: "Android only" }
    opened: { type: integer }
    listOpened: { type: integer }
    openRate: { type: number, description: "(opened + listOpened, unique devices) / sent" }
```

### Push analytics (what the admin sees)
For each push, per platform and in total:

| Metric | Source | Note |
|---|---|---|
| Targeted | devices matched by the audience | at send time |
| Sent / Failed | FCM HTTP v1 responses | failed split by `error_code` |
| Received | app `received` events | Android only (iOS shows "n/a") |
| Opened | app `opened` events (tray tap) | unique devices |
| Opened from list | app `list_opened` events | unique devices |
| Open rate | unique devices that opened either way ÷ sent | shown as % |
| Opens over time | `push_events` per hour | first 72 h |

The **overview** (top of the push list page) shows, for the chosen date range:
pushes sent, devices reached, total opens, average open rate, open rate by
type, and the best 5 pushes by open rate. Firebase's own delivery report per
push is in the Firebase console (Messaging → Reports), filtered by the
`analytics_label` (= the push's public id); link to it from the detail page.

### Admin portal pages: `apps/admin/app/(dashboard)/push/`
```
push/
  page.tsx                  # RSC: overview cards + history table (all pushes, filters in the URL)
  new/page.tsx              # compose form
  [id]/page.tsx             # detail: message, audience, stats, timeline chart, actions
  actions.ts                # Server Actions: createPush, sendTest, cancelPush, hidePush, previewAudience, duplicate
  schema.ts                 # Zod schemas shared by forms and actions
  PushForm.tsx              # client component, useActionState
  PushPreview.tsx           # Android and iOS notification mockups with the entered text
  PushStatsCards.tsx  PushTimelineChart.tsx  PushHistoryTable.tsx
```
- **Header:** environment badge (UAT / PROD, PROD in the error color), as in 6b.
- **History table:** title (English), type chip, audience summary, status chip,
  scheduled/sent time in the admin's time zone, sent, opened, open rate. Filters:
  date range (same rules as the error panel, 12c), type, status, text search.
  Pagination 20 per page. Hidden pushes show a "Hidden from app" chip. Row → detail.
- **Compose form**, top to bottom:
  1. Type: segmented buttons "Default", "Open app page", "App update", "Web link".
  2. Type fields: for "Open app page" a select filled from `GET admin/push/targets`
     plus one input per required param; for "Web link" a URL field (`https://` only).
  3. Texts: English title + message (required, with counters 65 / 240), Bangla tab (optional).
  4. Image URL (optional, `https`), with a thumbnail check.
  5. Audience: "All devices" or filters (Android / iOS, languages, app build
     range, specific user ids). A live "Will reach about N devices (A Android,
     I iOS)" line from `previewAudience`, debounced.
  6. When: "Send now" or "Schedule" (date-time in the admin's zone, sent as UTC).
  7. Buttons: "Send test to my devices" (lists the admin's test devices) and
     "Send" / "Schedule".
- **`PushPreview`** next to the form shows how the push looks on Android and
  iOS in the chosen language, and what a tap will do ("Opens: Profile",
  "Opens: Play Store / App Store", "Opens: https://abc.com/offer in the in-app browser").
- **Send confirmation:** a dialog that names the environment and reach ("Send
  to 12,340 devices in PROD?"). In PROD the admin types `SEND` to confirm.
  Disable the button while pending.
- **Detail page:** stats cards (per platform and total, from the table above),
  the hourly timeline chart (opens and list opens), failure reasons, the exact
  payload per platform (read-only JSON, for debugging), and actions: Cancel
  (scheduled), Hide from app (sent), Duplicate (prefills the form).

```ts
// push/schema.ts
import { z } from "zod";

const https = z.string().trim().url().max(2048).refine((u) => new URL(u).protocol === "https:", "https_only");
const text = z.object({ title: z.string().trim().min(1).max(65), body: z.string().trim().min(1).max(240) });
const paramValue = z.string().regex(/^[A-Za-z0-9_-]{1,64}$/);

const base = z.object({
  texts: z.object({ en: text, bn: text.partial().optional() }),
  imageUrl: https.optional(),
  audience: z.object({
    platforms: z.array(z.enum(["android", "ios"])).min(1).default(["android", "ios"]),
    locales: z.array(z.enum(["en", "bn"])).optional(),
    userIds: z.array(z.string()).max(1000).optional(),
    minBuild: z.coerce.number().int().min(0).optional(),
    maxBuild: z.coerce.number().int().min(0).optional(),
  }),
  scheduledAt: z.coerce.date().optional(),
});

export const pushSchema = z.discriminatedUnion("type", [
  base.extend({ type: z.literal("default") }),
  base.extend({ type: z.literal("app_update") }),
  base.extend({ type: z.literal("web_url"), url: https }),
  base.extend({
    type: z.literal("deeplink"),
    target: z.enum(["home", "notices", "notice_details", "profile", "settings", "chat", "more"]),
    params: z.record(z.string(), paramValue).default({}),
  }),
]);
```
The PHP Form Request repeats every rule (the browser is never trusted), and the
target list in the select comes from the API, so a new app page needs no web
change beyond regenerating `@web/api-client`.

### Where Supabase fits
The decision to move this backend to Supabase is still open (`kmp-supabase`).
If it happens, keep the same contract: the tables become Postgres tables with
RLS (devices see only their own rows through the device key), `DispatchPush`
becomes the `send-push` edge function (`kmp-supabase` 10) with the service
account JSON as a Supabase secret, scheduled sends use `pg_cron`, and the admin
page calls the function instead of PHP. Until then, push lives in this PHP module.

### Tests (write first, section 10 order)
1. **Contract:** `PushType`, `PushDeepLinkTarget`, `InboxItem`, `PushStats` and
   all paths above in `openapi.yaml`; `DeepLinkTarget` cases equal the enum.
2. **Domain (Pest unit):** text rules (required English, limits, tags
   stripped); `deeplink` without or with extra params rejected; `web_url` with
   `http`, IP host or userinfo rejected; payload over 4 KB rejected; schedule
   in the past or > 30 days rejected; cancel only draft/scheduled; hide only sent.
3. **Use cases** (in-memory repos, fake `PushSender`, `FixedClock`):
   `DispatchPush` sends each device once even when a chunk is retried;
   `UNREGISTERED` marks the token invalid; counts land in `push_stats`; texts
   per device locale with English fallback; `RecordPushEvents` counts a
   duplicate once and ignores pushes the device never got; `GetInbox` returns
   30 newest, excludes hidden, sets `readAt`.
4. **Feature (MySQL):** register → key returned once; `PUT` with a wrong key →
   401; bearer links the user, no bearer unlinks; inbox validates against
   `openapi.yaml`; admin endpoints 403 without `push.view` / `push.send`;
   idempotent create; the boot check refuses a mismatched `FCM_PROJECT_ID`.
5. **FCM payloads:** `FcmMessageFactory` snapshots for each type and platform
   (Android data-only + high priority; iOS alert + same data; `analytics_label` set).
6. **Web:** `pushSchema` cases; `PushPreview` shows the tap target per type;
   Server Actions (validation errors, API error, success redirects to the detail
   page); the PROD confirm dialog needs `SEND`.
7. **E2E (Playwright, UAT seed with a fake FCM sender):** compose a `deeplink`
   push to Android → detail shows `targeted` and `sent`; post an `opened` event
   with the seeded device key → open rate updates; hide it → the inbox no
   longer returns it. Screenshot the list, form, preview and detail pages.

## 6d. Legal pages: Privacy Policy and Terms & Conditions (versioned)

The landing page serves the **Privacy Policy** and the **Terms & Conditions**.
Admins write and publish them in the admin portal, with no web deploy. Every
published version is kept forever and never edited, so you can always show
which text a user agreed to. The mobile app links to these pages and records
consent (`kmp-legal-consent`). Both stores need the privacy URL
(`play-console-release` 2, `app-store-connect-release` 2).

**Placeholder text only.** This skill writes a **template** with `{{...}}`
placeholders and `[REVIEW: ...]` notes, never legal text copied from another
site or generator. Tell the user every time: the text must be filled in and
reviewed by someone qualified for their country and their app before it is
published. Publishing refuses text that still has placeholders (rules below).

### URLs (per environment, stable)

| | PROD | UAT |
|---|---|---|
| Privacy Policy | `https://abc.com/privacy` | `https://abc.com/uat/privacy` |
| Terms & Conditions | `https://abc.com/terms` | `https://abc.com/uat/terms` |
| A language | `https://abc.com/bn/privacy` | `https://abc.com/uat/bn/privacy` |
| An old version | `https://abc.com/privacy/v/2` | `https://abc.com/uat/privacy/v/2` |
| In the app | `…/en/privacy?view=app` | `…/uat/en/privacy?view=app` |

- `/privacy` and `/terms` never change. Stores, emails and the app link to
  them, so never rename them. `proxy.ts` sends an unprefixed path to the
  default locale (`en`), the same way as every other landing page.
- `?view=app` hides the site header, footer navigation, download buttons and
  cookie banner, so the page reads cleanly inside Custom Tabs /
  `SFSafariViewController`. The text and the version are identical.
- The store listing always uses the **PROD** URL. A `/uat` URL never goes to a store.
- The `Cms` module reserves the slugs `privacy` and `terms` (422 `slug_reserved`),
  so a CMS page can't hide the legal pages.

### Module `Legal`

A separate module, not `Cms`, because legal text has its own rules: published
versions are immutable, they have an effective date, and users accept them.
```
modules/Legal/
  Domain/
    LegalDocType.php              # enum: privacy, terms (add more the same way, e.g. account_deletion)
    LegalVersion.php              # type, version, status, effectiveAt, requiresAcceptance, changeSummary, texts
    LegalText.php                 # locale, title, bodyMd; rules below
    LegalMarkdown.php             # pure: allowed subset check, placeholder check, sha256
    ConsentDecision.php           # pure: mustAccept(currentRequired, acceptedVersion)
    LegalRepository.php  AcceptanceRepository.php   # interfaces
  Application/
    GetCurrentLegal.php  GetLegalVersion.php  ListLegalVersions.php      # public, cached
    CreateDraft.php  SaveDraft.php  DeleteDraft.php  PublishVersion.php  # admin
    GetLegalStatus.php  RecordAcceptance.php                             # signed-in app user
    GetAcceptanceStats.php
  Infrastructure/  EloquentLegalRepository.php  EloquentAcceptanceRepository.php  LegalCache.php
  Http/
    PublicLegalController.php  UserLegalController.php  AdminLegalController.php  routes.php
  Console/ActivateDueLegalVersions.php     # schedule()->everyMinute(): revalidates when a version takes effect
  Database/migrations/
  Resources/templates/privacy.en.md  terms.en.md   # placeholder templates (below)
  Tests/Unit/  Tests/Feature/
```

Tables (additive migrations, section 5 rules):
```sql
legal_versions (
  id BIGINT UNSIGNED PK, public_id CHAR(26) UNIQUE,
  type ENUM('privacy','terms'), version SMALLINT UNSIGNED,          -- 1, 2, 3 … per type
  status ENUM('draft','published'),
  effective_at DATETIME(0) NULL, published_at DATETIME(0) NULL, published_by BIGINT UNSIGNED NULL,
  requires_acceptance BOOL NOT NULL DEFAULT 0,                       -- signed-in users must accept again
  change_summary JSON NULL,                                          -- {"en":"…","bn":"…"}, ≤ 300 chars each
  created_by BIGINT UNSIGNED, created_at, updated_at, deleted_at NULL,   -- only drafts are ever deleted
  UNIQUE (type, version), INDEX (type, status, effective_at)
)
legal_version_texts (
  version_id BIGINT UNSIGNED, locale VARCHAR(8), title VARCHAR(120),
  body_md MEDIUMTEXT, body_sha256 CHAR(64),
  PRIMARY KEY (version_id, locale)
)
legal_acceptances (                               -- append-only
  id BIGINT UNSIGNED PK, user_subject VARCHAR(64),   -- the mobile guard's user id (Supabase JWT `sub`)
  version_id BIGINT UNSIGNED, type ENUM('privacy','terms'), locale VARCHAR(8), body_sha256 CHAR(64),
  source ENUM('signup','reaccept'), platform ENUM('android','ios','desktop','web'),
  app_build INT UNSIGNED NULL, accepted_at DATETIME(0),
  UNIQUE (user_subject, version_id), INDEX (user_subject, type, version_id)
)
```
Acceptances keep only what proves consent: who (user id), which exact text
(version + locale + hash), when, and from where (signup or re-accept, platform,
build). No IP address, device id or user agent, unless the user's lawyer asks
for it; then add it with a retention period. When an account is deleted, its
acceptances are kept only as long as the privacy policy says, then removed with
the account's other data.

Domain rules (each one has a unit test):
- **Current version** of a type = the `published` version with the highest
  `effective_at <= now`. **Upcoming** = a `published` version with
  `effective_at > now` (at most one per type, 409 `legal_upcoming_exists`).
  Version 1 of each type always has `requires_acceptance = true`.
- **Published is immutable.** No endpoint edits a published version's text,
  date or flags. A typo fix is a new version with `requires_acceptance = false`.
  Published versions are never deleted.
- `effective_at` is now or later, at most 365 days ahead. A version with
  `requires_acceptance` should give users notice: the publish dialog suggests
  30 days and warns below 7 (warning only; the law of the user's country decides).
- English (`en`) is required, `bn` is optional. A missing locale falls back to
  English and the response says so (`fallback: true`).
- `title` 1..120 chars. `body_md` 1..100 KB in the **allowed Markdown subset**:
  headings `##` and `###`, paragraphs, bullet and numbered lists, `**bold**`,
  `*italic*`, links with `https:` or `mailto:` only, and `---`. No raw HTML,
  no images, no tables, no `javascript:` or `data:` links. `LegalMarkdown`
  rejects anything else (422 `legal_markdown_not_allowed` with the line number).
- **Publish refuses placeholders**: any `{{`, `}}`, `[REVIEW` or `[TODO` left in
  any locale → 422 `legal_placeholders_left` with the list. Drafts may keep them.
- `ConsentDecision::mustAccept`: `required` = the highest current-or-older
  version with `requires_acceptance = true`; the user must accept when they
  never accepted that type, or their highest accepted version is below `required`.
  An upcoming version is never required yet.
- `RecordAcceptance` accepts only the **current** version id of each type
  (409 `legal_version_not_current` otherwise, so an app that waited too long
  re-reads the text) and is idempotent per user and version.

### Endpoints (write them in `openapi.yaml` first)
```
GET    /api/v1/legal/{type}?locale=                       public: current + upcoming notice
GET    /api/v1/legal/{type}/versions                      public: version, effectiveAt, changeSummary (no bodies)
GET    /api/v1/legal/{type}/versions/{version}?locale=    public: one published version
GET    /api/v1/legal/status?locale=                       mobile user guard: what this user must accept
POST   /api/v1/legal/acceptances                          mobile user guard, throttle 10/min per user
GET    /api/v1/admin/legal                                legal.view: both types, current, upcoming, drafts
POST   /api/v1/admin/legal/{type}/drafts                  legal.edit (from=current|template)
PUT    /api/v1/admin/legal/drafts/{id}                    legal.edit
DELETE /api/v1/admin/legal/drafts/{id}                    legal.edit
POST   /api/v1/admin/legal/drafts/{id}/publish            legal.publish { effectiveAt, requiresAcceptance, changeSummary }
GET    /api/v1/admin/legal/{type}/versions/{id}           legal.view (with all locales)
GET    /api/v1/admin/legal/{type}/stats                   legal.view: acceptances per version and source
```
```json
GET /api/v1/legal/privacy?locale=bn
{ "data": {
    "type": "privacy", "id": "01JC…", "version": 3, "locale": "en", "fallback": true,
    "title": "Privacy Policy", "bodyMd": "## 1. Who we are\n…",
    "effectiveAt": "2026-11-01T00:00:00Z", "publishedAt": "2026-10-02T09:00:00Z",
    "upcoming": { "version": 4, "effectiveAt": "2026-12-01T00:00:00Z", "changeSummary": "We added Crashlytics." } },
  "error": null }

GET /api/v1/legal/status
{ "data": { "items": [
    { "type": "terms",   "currentId": "01JC…", "currentVersion": 2, "acceptedVersion": 1, "mustAccept": true,
      "title": "Terms & Conditions", "changeSummary": "New rules for chat content." },
    { "type": "privacy", "currentId": "01JD…", "currentVersion": 3, "acceptedVersion": 3, "mustAccept": false,
      "title": "Privacy Policy", "changeSummary": null } ] },
  "error": null }

POST /api/v1/legal/acceptances
{ "versionIds": ["01JC…", "01JD…"], "source": "signup", "locale": "en", "platform": "android", "appBuild": 15 }
→ 201 { "data": { "accepted": 2 } }
```
- No published version for a type → public `GET` returns 404
  `legal_not_published` and `status` leaves that type out (nothing to accept).
- Unknown `type` → 404. Unknown `locale` → English with `fallback: true`.
- Public reads are cached in Redis (`legal:{type}:{locale}`, 5 min TTL) and
  sent with `Cache-Control: public, max-age=300`. Publishing and
  `ActivateDueLegalVersions` forget the cache and revalidate the landing tags
  `legal:{type}` (section 7).
- `status` and `acceptances` are not cached and need the signed-in user (the
  mobile guard verifies the Supabase JWT, as in 6c).
- Permissions: `legal.view`, `legal.edit`, `legal.publish`. Give
  `legal.publish` to as few admins as possible. Audit-log every create, save,
  delete and publish (section 5).

### Landing pages: `apps/landing/app/[locale]/(legal)/`
```
(legal)/
  privacy/page.tsx                 # current Privacy Policy
  privacy/v/[version]/page.tsx     # an old version, with a "This is not the current version" notice
  terms/page.tsx  terms/v/[version]/page.tsx
  LegalDocument.tsx                # shared layout: title, "Effective 1 Nov 2026 · Version 3", TOC, body, history link
```
```tsx
// apps/landing/app/[locale]/(legal)/privacy/page.tsx
import { notFound } from "next/navigation";
import { getLegal } from "@web/api-client/server";
import { LegalDocument } from "../LegalDocument";

export const revalidate = 3600;     // safety net; publish and effective-date jobs revalidate sooner

export async function generateMetadata({ params }: LegalPageProps) {
  const { locale } = await params;
  const doc = await getLegal("privacy", locale);
  return doc ? { title: doc.title, alternates: { canonical: `/${locale}/privacy` } } : {};
}

export default async function PrivacyPage({ params, searchParams }: LegalPageProps) {
  const { locale } = await params;
  const { view } = await searchParams;
  const doc = await getLegal("privacy", locale);          // fetch tagged "legal:privacy", "legal"
  if (!doc) notFound();
  return <LegalDocument doc={doc} inApp={view === "app"} />;
}
```
- Markdown renders through `SafeMarkdown` in `packages/sdui` (a Server
  Component with `react-markdown`, `skipHtml`, an `allowedElements` list equal
  to the PHP subset, and a `urlTransform` that keeps only `https:` and `mailto:`).
  The same component renders the admin preview, so the preview is exact.
  `##` headings get ids and form the table of contents.
- The page shows the effective date, the version number, a link to "Previous
  versions" (from `/versions`), and, when there is an upcoming version, a
  notice: "An updated version takes effect on 1 Dec 2026" with its summary and a link.
- A static route beats the `[[...slug]]` catch-all, so these pages never go
  through the CMS. `?view=app` reads `searchParams`, which makes the route
  dynamic; keep it cheap by caching the API fetch with tags (the HTML render is
  small), or use two static variants (`/privacy` and `/app/privacy`) rewritten
  by `proxy.ts` if you need every hit served from the edge.
- Every landing page footer links to Privacy Policy and Terms & Conditions
  (from `packages/ui` `SiteFooter`, using `next/link` so `basePath` adds `/uat`).
- `sitemap.ts` lists `/privacy` and `/terms` for each locale with `hreflang`.
  Old versions are `noindex` with a canonical to the current page. On UAT
  everything is already `noindex` (12b).

### Admin portal page: `apps/admin/app/(dashboard)/legal/`
```
legal/
  page.tsx                  # RSC: both types side by side: current, upcoming, drafts, history
  [type]/drafts/[id]/page.tsx  # editor
  actions.ts                # Server Actions: createDraft, saveDraft, deleteDraft, publish
  schema.ts                 # Zod, same rules as PHP
  LegalEditor.tsx           # client: locale tabs (English, Bangla), Markdown textarea, live SafeMarkdown preview
  PlaceholderList.tsx       # lists every {{…}} / [REVIEW …] left, click jumps to the line
  VersionDiff.tsx           # word diff against the current version, per locale
  PublishDialog.tsx
```
- **Header:** the environment badge (UAT / PROD) as in 6b.
- **Overview** per type: current version (number, effective date, published
  by), upcoming version if any, drafts, the full version history with links to
  the public `/v/{n}` page, and acceptance counts per version.
- **Editor:** "Start from template" (`from=template`, the placeholder text
  below) or "Start from current" (copies the current version into a new draft).
  Autosave every 10 s while typing (`saveDraft`), Markdown help for the allowed
  subset, the placeholder list, a phone-width preview in "app view", and the
  diff against the current version.
- **Download .md / Upload .md** per locale. UAT and PROD have separate
  databases, so the flow is: publish in UAT, review it at
  `abc.com/uat/privacy`, download the Markdown, upload it into a PROD draft,
  publish. There is no cross-environment copy button.
- **Publish dialog:** effective date and time (admin's time zone, sent as UTC),
  "Users must accept this version" switch (on for version 1, off by default
  after that), change summary per locale, and the diff. It says what will
  happen ("Signed-in users of the PROD app will be asked to accept Terms v3 on
  1 Dec 2026"). In PROD the admin types the document name to confirm. The
  button stays disabled while placeholders are left.

```ts
// legal/schema.ts
import { z } from "zod";

export const PLACEHOLDER = /\{\{|\}\}|\[(REVIEW|TODO)\b/;

const text = z.object({
  title: z.string().trim().min(1).max(120),
  bodyMd: z.string().min(1).max(100_000),
});

export const draftSchema = z.object({
  texts: z.object({ en: text, bn: text.optional() }),
});

export const publishSchema = z.object({
  draftId: z.string().length(26),
  effectiveAt: z.coerce.date().refine((d) => d.getTime() >= Date.now() - 60_000, "effective_in_past"),
  requiresAcceptance: z.coerce.boolean(),
  changeSummary: z.object({ en: z.string().trim().max(300).optional(), bn: z.string().trim().max(300).optional() }),
});
```
The PHP side repeats every rule, including the Markdown subset and the
placeholder check. The browser is never the authority.

### Placeholder templates (`Resources/templates/*.en.md`)

Write them fresh for the app, as an outline of headings with short neutral
sentences and placeholders. Never paste text from another site or a generator.
Fill the data sections from what the app really collects: check
`kmp-analytics-logging`, `kmp-supabase`, `kmp-push-notifications`, `core/ads`
and every SDK in the project, and leave a `[REVIEW: …]` note where you are not sure.

```markdown
[REVIEW: Template only. Have a qualified person review this text for {{country}} and this app before publishing.]

## 1. Who we are
{{company_name}} ("we") runs the {{app_name}} app and the website {{website_url}}. Contact: {{contact_email}}.

## 2. What we collect
- **Account:** email address and display name, when you create an account.
- **Content you add:** {{content_types}}.
- **Device and app:** app version, platform, language, a notification token and an installation id.
- **Usage and crashes:** {{analytics_provider}} and {{crash_provider}} collect usage events and crash reports. [REVIEW: list the events]
- **Ads:** {{ads_provider_or_remove}}

## 3. Why we use it
## 4. Who we share it with
Service providers that run the app for us: {{hosting_provider}}, {{backend_provider}}, {{analytics_provider}}. [REVIEW: complete the list]
## 5. How long we keep it
## 6. Your choices and rights
You can see, change or delete your data, and delete your account in the app ({{delete_account_path}}) or at {{delete_account_url}}.
## 7. Children
## 8. Security
## 9. Transfers to other countries
## 10. Changes to this policy
We publish every change on this page with a new version number and effective date. Previous versions stay available.
## 11. Contact
{{contact_email}}
```
`terms.en.md` follows the same style with: 1. Agreement, 2. Who can use the
app, 3. Your account, 4. Acceptable use, 5. Your content, 6. Our content and
licences, 7. Third-party services, 8. Ending your account, 9. Disclaimers,
10. Limitation of liability, 11. Governing law ({{jurisdiction}}),
12. Changes to these terms, 13. Contact. Bangla (`bn`) starts as a copy of the
section headings with `[REVIEW: translate]`; a fluent speaker must translate
and review it.

### Tests (write first, section 10 order)
1. **Contract:** `LegalDocument`, `LegalStatus`, `LegalAcceptanceRequest` and
   all paths above in `openapi.yaml`.
2. **Domain (Pest unit):** current/upcoming selection by `effective_at`
   (before, at, after; two published, one upcoming); `mustAccept` table (never
   accepted → true; accepted v1, required v1 → false; accepted v1, v2 required →
   true; v3 not required after v2 → still only v2 needed; upcoming required → false);
   Markdown subset (allows the template; rejects `<script>`, `<img>`, `![x](…)`,
   `[x](javascript:…)`, `[x](http://…)`, tables); placeholder check finds `{{`,
   `[REVIEW` and `[TODO` in any locale.
3. **Use cases** (in-memory repos + `FixedClock`): publish freezes the text and
   forgets the cache; editing a published version fails; a second upcoming → 409;
   acceptance of a non-current version → 409; repeat acceptance → one row.
4. **Feature (MySQL):** public GET returns the current version and `fallback`
   for `bn`; 404 before the first publish; `status` needs a user token (401
   without); admin endpoints → 403 without the permission; publish with a
   placeholder → 422 with the list; responses validate against `openapi.yaml`.
5. **Web:** `SafeMarkdown` drops HTML and non-https links; `draftSchema` /
   `publishSchema`; `LegalDocument` shows version, effective date, upcoming
   notice and hides chrome with `inApp`; the publish button is disabled while
   `PlaceholderList` is not empty; footer links include the basePath.
6. **E2E (Playwright, UAT seed):** admin starts from template → publish is
   blocked → fills placeholders → publishes → `abc.com/uat/privacy` shows
   version 1; publishes Terms v2 with "must accept" → `GET /legal/status` for
   the seeded user returns `mustAccept: true`; after `POST /acceptances` it
   returns `false`. Screenshot the editor, the publish dialog, the page at phone
   and desktop width, and the `?view=app` page.

## 7. Rendering strategy (server-driven + edge)

| Surface | Rendering | Cache | Runtime |
|---|---|---|---|
| Landing pages (`/`, `/[slug]`) | Static at build + ISR | CDN edge, revalidated by tag | Node build, served from edge |
| Landing locale / geo redirect | `proxy.ts` (`middleware.ts` on Next 15) | none | edge-light, no DB |
| Landing legal pages (`/privacy`, `/terms`) | Static + ISR, tag `legal:{type}` | CDN edge, revalidated on publish and on the effective date | Node build, served from edge |
| Landing contact form | Server Action → PHP | none | Node |
| Admin pages | Dynamic SSR, React Server Components | `no-store` | Node |
| Admin mutations | Server Actions → PHP | `revalidatePath`/`revalidateTag` after | Node |
| Admin auth gate | `proxy.ts` verifies the JWT signature | none | edge-light |

### Landing: static, cached at the edge, revalidated on publish
```tsx
// apps/landing/app/[locale]/[[...slug]]/page.tsx
import { notFound } from "next/navigation";
import { getPage, listPublishedSlugs } from "@web/api-client/server";
import { parseSections, RenderSections } from "@web/sdui";

export const revalidate = 3600;            // safety net; publish events revalidate sooner
export const dynamicParams = true;         // new pages render on first request, then cache

export async function generateStaticParams() {
  return listPublishedSlugs();             // [{ locale: "en", slug: [] }, { locale: "en", slug: ["pricing"] }]
}

export async function generateMetadata({ params }: PageProps) {
  const { locale, slug } = await params;
  const page = await getPage(slug?.join("/") ?? "home", locale);
  return page ? { title: page.seo.title, description: page.seo.description } : {};
}

export default async function LandingPage({ params }: PageProps) {
  const { locale, slug } = await params;
  const page = await getPage(slug?.join("/") ?? "home", locale);
  if (!page) notFound();
  return <RenderSections sections={parseSections(page.sections)} />;
}
```
```ts
// packages/api-client/src/server.ts
import "server-only";

export async function getPage(slug: string, locale: string) {
  const res = await fetch(`${process.env.API_INTERNAL_URL}/api/v1/pages/${encodeURIComponent(slug)}?locale=${locale}`, {
    next: { tags: [`page:${slug}:${locale}`, "pages"] },
  });
  if (res.status === 404) return null;
  return unwrap<Page>(res);                // throws ApiError on error envelope
}
```
On publish, the PHP `PageCache::invalidate()` job calls the landing app:
```ts
// apps/landing/app/api/revalidate/route.ts
import { revalidateTag } from "next/cache";
import { timingSafeEqual } from "node:crypto";

export async function POST(request: Request) {
  const secret = Buffer.from(request.headers.get("x-revalidate-secret") ?? "");
  const expected = Buffer.from(process.env.REVALIDATE_SECRET ?? "");
  if (expected.length === 0 || secret.length !== expected.length || !timingSafeEqual(secret, expected)) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }
  const { tags } = (await request.json()) as { tags: string[] };
  tags.forEach((tag) => revalidateTag(tag));
  return Response.json({ revalidated: tags });
}
```
- Images through `next/image` with the CDN host in `images.remotePatterns`.
- Landing has **zero** client JavaScript by default. Add `"use client"` only to
  the smallest interactive leaf (menu toggle, carousel).
- Target Lighthouse ≥ 95 (performance, accessibility, SEO) on mobile. Provide
  `sitemap.ts`, `robots.ts`, Open Graph images and `hreflang` alternates.

### Edge rules
- Code at the edge (`proxy.ts`, or a route with `export const runtime = "edge"`)
  must be tiny and must not touch MySQL, Node APIs or large libraries. Allowed:
  read cookies/headers, verify a JWT with `jose`, redirect/rewrite, geo/locale.
- Use the edge runtime only for routes that need it. Data and auth decisions
  happen in PHP; the edge check is a fast first gate, not the authority.

### Admin: SSR + RSC + Server Actions
```tsx
// apps/admin/app/(dashboard)/pages/page.tsx
import { adminApi } from "@/lib/admin-api";        // server-only, adds the session token

export const dynamic = "force-dynamic";

export default async function PagesList({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
  const { page = "1" } = await searchParams;
  const result = await adminApi.pages.list({ page: Number(page), perPage: 20 });
  return <PagesTable rows={result.data} meta={result.meta} />;
}
```
```ts
// apps/admin/app/(dashboard)/pages/actions.ts
"use server";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { adminApi } from "@/lib/admin-api";

const publishInput = z.object({ pageId: z.string().length(26) });

export async function publishPage(_: ActionState, form: FormData): Promise<ActionState> {
  const input = publishInput.safeParse({ pageId: form.get("pageId") });
  if (!input.success) return { ok: false, error: "invalid_input" };
  const result = await adminApi.pages.publish(input.data.pageId);   // PHP checks the permission
  if (!result.ok) return { ok: false, error: result.error.code };
  revalidatePath("/pages");
  return { ok: true };
}
```
- The browser talks only to the admin Next.js server. PHP is on the private
  network (`API_INTERNAL_URL`), so there is no CORS and no token in the browser.
- Forms use `useActionState` for pending and error states, and Zod on both sides
  (Next.js for fast feedback, PHP Form Request as the authority).
- Tables: server-side pagination, sorting and filtering via URL `searchParams`.

## 8. Auth and roles (admin)

- `Auth` module: `admins`, `roles`, `permissions`, `role_permission`,
  `admin_role`. Permissions are strings like `pages.publish`, `users.ban`.
- Login: the admin app posts credentials via a Server Action to
  `POST /api/v1/admin/auth/login`. PHP checks the password (`password_hash` with
  Argon2id), rate-limits by IP and email, and returns a short-lived access token
  (JWT, EdDSA or RS256, 15 min) and a rotating refresh token (stored hashed in MySQL).
- Next.js stores both in `httpOnly; Secure; SameSite=Lax; Path=/` cookies.
  `proxy.ts` verifies the access token signature with the **public** key (`jose`)
  and redirects to `/login` if missing or expired; refresh happens server-side.
- Every PHP admin endpoint checks the permission (`->can('pages.publish')`
  policy). Hiding a button in the UI is not security.
- 2FA (TOTP) is required for admin accounts in HIGH-tier projects
  (`kmp-security` section 0). Audit-log every admin mutation.
- Mobile app users use a separate guard (`/api/v1/auth/...`) and never get
  admin permissions.

## 9. Security baseline (web + API)

- Secrets only in env vars / the host's secret store. `.env.example` lists every
  key with a dummy value. Never commit `.env`, keys or DB dumps.
- Security headers from Next.js (`headers()` in `next.config.ts`) and nginx:
  `Content-Security-Policy` (nonce-based on admin), `Strict-Transport-Security`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`,
  `frame-ancestors 'none'` for admin.
- Server Actions are POST-only and same-origin checked by Next.js; keep
  `serverActions.allowedOrigins` limited to your domains.
- PHP: validate every input in Form Requests, mass-assignment guarded
  (`$fillable`), rate limits (`RateLimiter::for`), uploads checked by MIME and size,
  stored outside `public/` with random names.
- Keep the admin portal on its own subdomain (`admin.example.com`), optionally
  behind an IP allowlist or zero-trust proxy. `noindex` on all admin pages.
- Run `composer audit` and `pnpm audit` in CI.

## 10. Test-driven development (write tests FIRST)

For every behavior: write the failing test, run it and see it fail for the right
reason, write the minimum code to pass, then refactor with tests green.

### Order (inside out)
1. **Contract**: add or change the endpoint in `openapi.yaml`.
2. **PHP Domain unit test** (rules: `Page::publish` rejects an empty page).
3. **PHP use case test** with in-memory fakes for repositories and clock.
4. **PHP feature (HTTP) test** against a real MySQL test database: status code,
   envelope shape, permission checks, validation errors.
5. **Web package tests**: `sdui` parsing, `api-client` with MSW, `ui` components.
6. **Web app tests**: Server Actions and page components with the API client faked.
7. **End-to-end (Playwright)**: the main flow against docker-compose.

### PHP (Pest)
```php
// modules/Cms/Tests/Unit/PublishPageTest.php
it('publishes a draft page and invalidates its cache', function () {
    $pages = new InMemoryPageRepository([PageMother::draft(id: 'p1', slug: 'pricing')]);
    $cache = new SpyPageCache();
    $publish = new PublishPage($pages, $cache, new FixedClock('2026-01-01T00:00:00Z'));

    $page = $publish('p1', AdminId::of(1));

    expect($page->isPublished())->toBeTrue()
        ->and($cache->invalidated)->toBe(['pricing']);
});

it('refuses to publish a page with no sections', function () {
    $pages = new InMemoryPageRepository([PageMother::draft(id: 'p1', sections: [])]);
    $publish = new PublishPage($pages, new SpyPageCache(), new FixedClock('2026-01-01T00:00:00Z'));

    expect(fn () => $publish('p1', AdminId::of(1)))->toThrow(EmptyPageCannotBePublished::class);
});
```
```php
// modules/Cms/Tests/Feature/PublishPageEndpointTest.php
uses(RefreshDatabase::class);

it('returns 403 for an admin without pages.publish', function () {
    $page = Page::factory()->draft()->create();

    $this->actingAsAdmin(permissions: [])
        ->postJson("/api/v1/admin/pages/{$page->public_id}/publish")
        ->assertForbidden()
        ->assertJsonPath('error.code', 'forbidden')
        ->assertJsonPath('data', null);
});

it('publishes the page for an editor', function () {
    $page = Page::factory()->draft()->withSections(2)->create();

    $this->actingAsAdmin(permissions: ['pages.publish'])
        ->postJson("/api/v1/admin/pages/{$page->public_id}/publish")
        ->assertOk()
        ->assertJsonPath('data.status', 'published')
        ->assertJsonPath('error', null);
});
```
- Test database: MySQL 8.4 in docker (same engine as production), not SQLite,
  so JSON columns, collations and indexes behave the same.
- Validate responses against `openapi.yaml` in feature tests (e.g.
  `league/openapi-psr7-validator`), so the contract and code can't drift.

### Web (Vitest + Testing Library + MSW)
```ts
// packages/sdui/src/schema.test.ts
import { describe, expect, it } from "vitest";
import { parseSections } from "./schema";

describe("parseSections", () => {
  it("keeps known sections in order", () => {
    const sections = parseSections([
      { id: "a", type: "hero", props: { title: "Hi" } },
      { id: "b", type: "faq", props: { items: [] } },
    ]);
    expect(sections.map((s) => s.id)).toEqual(["a", "b"]);
  });

  it("drops unknown or invalid sections instead of failing", () => {
    const sections = parseSections([
      { id: "a", type: "countdown", props: {} },
      { id: "b", type: "hero", props: {} },
      { id: "c", type: "hero", props: { title: "Ok" } },
    ]);
    expect(sections.map((s) => s.id)).toEqual(["c"]);
  });
});
```
```ts
// packages/api-client/src/server.test.ts
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

it("returns null when the page does not exist", async () => {
  server.use(http.get("*/api/v1/pages/missing", () =>
    HttpResponse.json({ data: null, error: { code: "not_found", message: "" } }, { status: 404 })));
  await expect(getPage("missing", "en")).resolves.toBeNull();
});

it("throws ApiError with the server code on 5xx", async () => {
  server.use(http.get("*/api/v1/pages/home", () =>
    HttpResponse.json({ data: null, error: { code: "server_error", message: "" } }, { status: 500 })));
  await expect(getPage("home", "en")).rejects.toMatchObject({ code: "server_error", status: 500 });
});
```
- In `packages/config`'s Vitest preset, alias `server-only` to an empty module
  and set `API_INTERNAL_URL=http://api.test` so server code runs under test.
- Async Server Components are tested through their parts: data functions with
  MSW, and the presentational component with Testing Library. Full rendering of
  async pages is covered by Playwright.
- Server Actions: unit test the action function with `adminApi` mocked (valid
  input, invalid input, API error, success revalidates).

### End-to-end (Playwright)
```ts
// e2e/admin-publish.spec.ts
test("editor publishes a page and it appears on the landing site", async ({ page }) => {
  await loginAs(page, "editor@example.test");
  await page.goto(`${ADMIN_URL}/pages`);
  await page.getByRole("row", { name: /pricing/i }).getByRole("button", { name: "Publish" }).click();
  await expect(page.getByText("Published")).toBeVisible();

  await page.goto(`${LANDING_URL}/en/pricing`);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});
```
Seed e2e data with a dedicated seeder (`php artisan db:seed --class=E2eSeeder`),
never with production data.

### Rules
- No skipped, disabled or `.only` tests. Never weaken an assertion to go green.
- One behavior per test, named in plain language.
- Fakes over mocks in PHP use case tests; MSW over mocking `fetch` in web tests.
- Coverage goal: Domain + Application ≥ 90%; overall is a signal, not a target.
- CI runs, in order: lint → typecheck → unit → feature (MySQL service) → build → e2e.

## 11. Local run and verify

```bash
cd backend
cp .env.example .env
docker compose up -d mysql redis
(cd api && composer install && php artisan migrate --seed && ./vendor/bin/pest)
(cd web && pnpm install && pnpm turbo lint typecheck test build)
docker compose up -d                        # api :8080, landing :3000, admin :3001
(cd web && pnpm --filter e2e test)
```
To try UAT routing locally, build with `BASE_PATH=/uat` and open
`http://localhost/uat/` through the nginx container.
Also check: `php artisan route:list --path=api/v1`, `EXPLAIN` for new list
queries, and that `curl -I localhost:3000/en` returns cache headers from the
static/ISR render.

## 12. Deploy and scale

- **API**: PHP-FPM (or Octane/FrankenPHP for higher throughput) behind nginx,
  N stateless containers, Redis for cache/session/queue, separate queue worker
  and scheduler containers. MySQL primary + read replica, daily backups with
  tested restore, slow query log on.
- **Landing**: `next build` and deploy to a host with a CDN (Vercel, Cloudflare,
  or self-hosted `output: "standalone"` behind a CDN). When self-hosting more
  than one instance, configure a shared cache handler (Redis) so ISR and
  `revalidateTag` work across instances.
- **Admin**: `output: "standalone"` container, at least two instances, on its
  own subdomain.
- Environments: **UAT** and **PROD**, served from one domain with UAT under
  `/uat` (section 12b).
- Health checks: `GET /api/v1/health` (DB + Redis), `/api/health` on each Next.js app.
- Observability: structured JSON logs to stderr with a request ID passed from
  the apps and Next.js to PHP (`X-Request-Id`), every API, queue, app and web
  error stored and shown in the admin error panel (section 12c), and basic
  metrics (latency, error rate, queue depth).

## 12b. UAT and PROD environments (UAT under `/uat`)

The backend has the same two environments as the app (`kmp-environments`):
**UAT** for testing and **PROD** for real users. They share one domain; UAT lives
under the `/uat` path:

| | PROD | UAT |
|---|---|---|
| Landing | `https://abc.com/` | `https://abc.com/uat/` |
| Admin | `https://abc.com/admin/` (or `admin.abc.com`) | `https://abc.com/uat/admin/` (or `admin.abc.com/uat`) |
| API | `https://abc.com/api/v1/...` | `https://abc.com/uat/api/v1/...` |
| KMP `BASE_URL` | `https://abc.com/` | `https://abc.com/uat/` |

**Fully separate, same code.** UAT runs the same images as PROD, built from the
same commit, but it has its **own** MySQL database and user, Redis (or Redis DB
number + key prefix), JWT key pair, revalidate secret, storage bucket/prefix,
mail sender and queue workers. UAT never reads or writes PROD data, and PROD
data is never copied to UAT without anonymizing it.

### Layout
```
backend/
  env/
    uat.env.example                 # every key, dummy values (committed)
    prod.env.example                # every key, dummy values (committed)
    uat.env  prod.env               # real values, NOT committed (or in the host's secret store)
  docker-compose.yml                # local dev (one env)
  docker-compose.deploy.yml         # api, queue, landing, admin per env: *-uat and *-prod services
  docker/nginx/abc.com.conf         # one server block, routes /uat/* to the UAT services
```
Key variables in each env file:
```dotenv
# env/uat.env.example
APP_ENV=uat
APP_URL=https://abc.com/uat
BASE_PATH=/uat                       # "" in prod
API_INTERNAL_URL=http://api-uat:8080 # private network; no /uat needed inside
DB_DATABASE=myapp_uat
DB_USERNAME=myapp_uat
REDIS_PREFIX=myapp_uat_
SESSION_COOKIE=myapp_uat_session
JWT_PUBLIC_KEY_PATH=/run/secrets/jwt_uat.pub
REVALIDATE_SECRET=change-me
```
Git-ignore `backend/env/*.env` (keep `*.env.example`).

### nginx: one domain, prefix routing
UAT locations come first. The `/uat` prefix is removed before the request reaches
PHP, so the Laravel routes are identical in both environments. Next.js keeps the
prefix because it is built with `basePath`.
```nginx
# docker/nginx/abc.com.conf
location /uat/api/   { proxy_pass http://api-uat:8080/api/;   include proxy_params; }   # strips /uat
location /uat/admin  { proxy_pass http://admin-uat:3001;      include proxy_params; }   # keeps /uat/admin
location /uat        { proxy_pass http://landing-uat:3000;    include proxy_params;
                       add_header X-Robots-Tag "noindex, nofollow" always; }

location /api/       { proxy_pass http://api-prod:8080/api/;  include proxy_params; }
location /admin      { proxy_pass http://admin-prod:3001;     include proxy_params; }
location /           { proxy_pass http://landing-prod:3000;   include proxy_params; }
```
`proxy_params` sets `Host`, `X-Forwarded-For`, `X-Forwarded-Proto` and
`X-Forwarded-Prefix` (`/uat` or empty). Trust the proxy in Laravel
(`trustProxies(at: ...)`) and set `APP_URL` per env so generated URLs (password
reset links, file URLs, pagination `next` links) include `/uat`.

### Next.js: basePath per environment
`basePath` is fixed at build time, so build the landing and admin images **once
per environment** from the same commit:
```ts
// apps/landing/next.config.ts
import type { NextConfig } from "next";

const basePath = process.env.BASE_PATH ?? "";   // "/uat" or ""

const config: NextConfig = {
  basePath,                                       // landing: "/uat" | ""
  output: "standalone",
  env: { NEXT_PUBLIC_APP_ENV: process.env.APP_ENV ?? "prod" },
};
export default config;
```
- Admin uses ``basePath: `${basePath}/admin` `` (`/uat/admin` or `/admin`).
- `next/link`, `next/image`, `redirect()` and `revalidatePath()` add the
  basePath for you. Never hardcode `/uat` or `/` in links; plain `<a href>` and
  `fetch("/api/...")` from the browser must use the basePath too.
- Cookies: UAT uses its own names (`myapp_uat_session`) and
  `Path=/uat`, so a tester signed in to UAT is never signed in to PROD, and the
  two never overwrite each other.
- Revalidation: the UAT API calls only the UAT landing app
  (`https://abc.com/uat/api/revalidate` or the private `landing-uat` host).
- UAT shows a visible "UAT" badge in the admin header and the landing footer, and
  adds " (UAT)" to page titles, same as the app (`titleSuffix` in
  `kmp-environments`). UAT is `noindex` (header above + `robots.ts` returning
  `disallow: "/"`), and is excluded from the PROD sitemap.
- Optionally protect all of `/uat` with basic auth or an IP allowlist in nginx,
  leaving `/uat/api/` open for test builds of the mobile app.

### KMP app
Set the app's base URL per environment to the root that contains `/api`:
```properties
# config/uat/env.properties  (kmp-environments)
BASE_URL=https://abc.com/uat/
WEB_URL=https://abc.com/uat/        # landing root: legal pages (6d, kmp-legal-consent)
# config/prod/env.properties
BASE_URL=https://abc.com/
WEB_URL=https://abc.com/
```
With a path in the base URL, keep the **trailing slash** and write request
paths **without** a leading slash (`"api/v1/pages/home"`). A leading slash makes
Ktor resolve from the host root and silently drops `/uat`, sending UAT builds to
PROD. Add a test in `core/network` that builds a request with the UAT base URL
and asserts the final URL starts with `https://abc.com/uat/api/`.

### Release flow
1. Merge to `main` → CI builds images tagged with the commit SHA → deploy to
   **UAT** (`BASE_PATH=/uat`) → run migrations on the UAT DB → run Playwright
   e2e against `https://abc.com/uat`.
2. Test on UAT (web + mobile UAT build).
3. Promote the **same** commit to PROD: rebuild the Next.js images with
   `BASE_PATH=""` (basePath is build-time), reuse the API image as is, run
   migrations on the PROD DB, then deploy.

### Tests for this (write first)
- PHP: `config('app.url')` and generated links include `/uat` when
  `APP_URL=https://abc.com/uat`.
- Next.js: a Vitest test that `next.config.ts` sets `basePath` from `BASE_PATH`;
  a Playwright smoke test per env that loads `/`, a landing page, the admin login
  and `GET {BASE}/api/v1/health`, and checks the UAT badge, `noindex` header and
  cookie path on UAT only.
- KMP: the base URL test above.

## 12c. Error log for every API and the admin error panel

**Every API endpoint logs its errors, with no code in the endpoint.** One
middleware records each failed request: which module, route, status, error code,
exception and request ID. App and web errors (`kmp-analytics-logging` section 8)
go to the same log. Admins browse it in the admin portal on an **Errors** page
filtered by date. UAT and PROD each have their own database (12b), so the UAT
panel shows UAT errors only.

### Module `Monitoring`
```
modules/Monitoring/
  MonitoringServiceProvider.php    # binds repository, failed-job listener, routes, prune schedule
  Domain/
    ErrorEvent.php                 # occurredAt, source, level, module, route, status, code, message, ...
    ErrorSource.php                # enum: api, queue, android, ios, desktop, web_admin, web_landing
    ErrorLevel.php                 # enum: error (5xx, crashes), warning (4xx)
    ErrorFilter.php                # fromUtc, toUtcExclusive, source, level, module, status, q
    Fingerprint.php                # pure: groups "the same error"
    ErrorLogRepository.php         # interface
  Application/
    RecordError.php                # never throws
    IngestClientErrors.php         # app + web errors
    ListErrors.php  GetError.php  SummarizeErrors.php  PruneErrors.php
  Infrastructure/
    MysqlErrorLogRepository.php  ErrorLogModel.php
  Http/
    Middleware/AssignRequestId.php  Middleware/LogApiErrors.php
    AdminErrorsController.php  ClientErrorsController.php
    Requests/ListErrorsRequest.php  Requests/ClientErrorsRequest.php
    routes.php
  Database/migrations/2026_10_28_000000_create_error_logs_table.php
  Tests/Unit/  Tests/Feature/
```
- No other module depends on `Monitoring`, and `Monitoring` doesn't use any other
  module. It reads only the request, the route, the response and the exception.
  Add both directions to the Pest arch test (section 3).
- A new module is covered by the middleware the day it is added.

### Table
```php
Schema::create('error_logs', function (Blueprint $table) {
    $table->id();
    $table->char('public_id', 26)->unique();
    $table->dateTime('occurred_at', 3);
    $table->string('source', 16);
    $table->string('level', 8);
    $table->string('module', 40);                      // "Cms", from the controller namespace
    $table->string('method', 8)->nullable();
    $table->string('route', 191)->nullable();          // template: "api/v1/pages/{page}"
    $table->unsignedSmallInteger('status')->nullable();
    $table->string('error_code', 64)->nullable();      // envelope error.code
    $table->string('message', 1000);
    $table->string('exception_class', 191)->nullable();
    $table->string('location', 255)->nullable();       // file:line
    $table->mediumText('trace')->nullable();           // max 16 KB, no arguments
    $table->char('request_id', 36)->nullable()->index();
    $table->string('actor_type', 16)->nullable();      // admin | user | null
    $table->unsignedBigInteger('actor_id')->nullable();
    $table->string('app_version', 32)->nullable();
    $table->char('fingerprint', 40);
    $table->json('context')->nullable();               // redacted extras only
    $table->index('occurred_at');
    $table->index(['source', 'occurred_at']);
    $table->index(['module', 'occurred_at']);
    $table->index(['level', 'occurred_at']);
    $table->index(['fingerprint', 'occurred_at']);
});
```
- `occurred_at` is UTC. Every list query has a date range, so it always uses one
  of these indexes.
- **Retention:** `PruneErrors` runs daily from the scheduler and deletes rows
  older than `ERROR_LOG_RETENTION_DAYS` (default 90) in chunks of 5,000, so it
  never locks the table for long.
- At very high volume, partition by month on `occurred_at`. MySQL then needs the
  unique key to include it: `(public_id, occurred_at)`.

### Recording API errors (one middleware, every module)
```php
// bootstrap/app.php
->withMiddleware(function (Middleware $middleware) {
    $middleware->prepend(AssignRequestId::class);      // reads X-Request-Id (UUID) or makes one; echoes it back
    $middleware->append(LogApiErrors::class);          // global, so unmatched routes (404/405) are logged too
})
->withExceptions(function (Exceptions $exceptions) {
    $exceptions->report(function (Throwable $e) {      // only reportable exceptions reach here (not 4xx ones)
        request()->attributes->set('monitoring.exception', $e);
    });
    // ...render every exception into the { data, error } envelope (section 4)
})
```
```php
// modules/Monitoring/Http/Middleware/LogApiErrors.php
final class LogApiErrors
{
    public function __construct(private readonly RecordError $record) {}

    public function handle(Request $request, Closure $next): Response
    {
        return $next($request);
    }

    /** Runs after the response is sent, so logging never slows the client down. */
    public function terminate(Request $request, Response $response): void
    {
        if (! $request->is('api/*') || $response->getStatusCode() < 400) {
            return;
        }
        if ($response->headers->has('X-App-Maintenance')) {
            return;                                    // planned 503 from section 6b, not an error
        }
        ($this->record)(ErrorEventFactory::fromHttp($request, $response, $request->attributes->get('monitoring.exception')));
    }
}
```
Register the middleware as a singleton in `MonitoringServiceProvider`, so
Laravel calls `terminate` on the same instance.

`ErrorEventFactory::fromHttp` fills the row:
- `module` from the controller class: `Modules\Cms\Http\...` → `Cms`. No route
  matched → `Unknown`.
- `route` = `$request->route()?->uri()` (the template, never real ids). No route
  matched → the path with id-like segments replaced by `{id}`.
- `status`, and `error_code` + `message` from the envelope the response already has.
- For a 422, the names of the failing fields go into `context.fields`
  (`["title", "email"]`), **never** their values.
- With an exception: class, `file:line` and the trace, cut to 16 KB. Set
  `zend.exception_ignore_args = On` in `php.ini` so traces carry no arguments.
- `level` = `error` for 5xx, `warning` for 4xx.
- `actor_type` / `actor_id` from the guard, `app_version` from the
  `X-App-Version` header, `request_id` from `AssignRequestId`.
- `fingerprint` = `sha1(source|module|route|status|error_code|exception_class|location)`.

**Never stored:** request or response bodies, the query string, `Authorization`,
cookies, tokens, passwords, OTPs, or the raw IP (store
`hash_hmac('sha256', $ip, config('monitoring.ip_salt'))` in `context` if needed).

`RecordError` must never break a request or hide the original error:
```php
final class RecordError
{
    public function __construct(
        private readonly ErrorLogRepository $errors,
        private readonly FloodGuard $flood,             // Redis: max 100 rows per fingerprint per minute
        private readonly LoggerInterface $stderr,       // JSON lines to stderr, for container logs
    ) {}

    public function __invoke(ErrorEvent $event): void
    {
        $this->stderr->log($event->level->psrLevel(), $event->message, $event->toLogContext());
        if (! $this->flood->allow($event->fingerprint)) {
            return;                                     // still counted in stderr; the panel shows the group
        }
        try {
            $this->errors->save($event);
        } catch (Throwable $e) {
            $this->stderr->critical('error_log write failed', ['reason' => $e->getMessage()]);
        }
    }
}
```
- Queue jobs: listen to `Queue::failing(...)` in the service provider and record
  the failure with `source = queue` and the module from the job's namespace.
- Each module may still log its own domain warnings through Laravel's `Log`
  with `['module' => 'Cms']` in the context. Those go to stderr, not the panel.

### Endpoints (write them in `openapi.yaml` first)
```
POST /api/v1/client-errors                 public, throttle 30/min per IP + per user, max 20 per request
GET  /api/v1/admin/errors                  errors.view
GET  /api/v1/admin/errors/summary          errors.view
GET  /api/v1/admin/errors/{publicId}       errors.view   (only this one returns the trace)
```
List query:
```
GET /api/v1/admin/errors?from=2026-10-22&to=2026-10-28&tz=Asia/Dhaka&source=android&level=error
                        &module=Cms&status=500&q=01J9...&page=1&perPage=50
```
```php
// modules/Monitoring/Http/Requests/ListErrorsRequest.php
public function rules(): array
{
    return [
        'from' => ['required', 'date_format:Y-m-d'],
        'to' => ['required', 'date_format:Y-m-d', 'after_or_equal:from'],
        'tz' => ['nullable', 'timezone:all'],
        'source' => ['nullable', Rule::enum(ErrorSource::class)],
        'level' => ['nullable', Rule::enum(ErrorLevel::class)],
        'module' => ['nullable', 'string', 'max:40'],
        'status' => ['nullable', 'integer', 'between:400,599'],
        'q' => ['nullable', 'string', 'max:100'],
        'page' => ['nullable', 'integer', 'min:1'],
        'perPage' => ['nullable', 'integer', 'between:1,100'],
    ];
}

public function after(): array
{
    return [function (Validator $validator) {
        $from = CarbonImmutable::parse($this->input('from'));
        if ($validator->errors()->isEmpty() && $from->diffInDays(CarbonImmutable::parse($this->input('to'))) >= 90) {
            $validator->errors()->add('to', 'range_too_long');
        }
    }];
}

/** `from` and `to` are whole days in the admin's time zone, both inclusive. */
public function filter(): ErrorFilter
{
    $zone = new DateTimeZone($this->input('tz', 'UTC'));
    return new ErrorFilter(
        fromUtc: CarbonImmutable::createFromFormat('Y-m-d', $this->input('from'), $zone)->startOfDay()->utc(),
        toUtcExclusive: CarbonImmutable::createFromFormat('Y-m-d', $this->input('to'), $zone)->addDay()->startOfDay()->utc(),
        source: $this->enum('source', ErrorSource::class),
        level: $this->enum('level', ErrorLevel::class),
        module: $this->input('module'),
        status: $this->integer('status') ?: null,
        q: $this->input('q'),
    );
}
```
- The repository uses `occurred_at >= fromUtc AND occurred_at < toUtcExclusive`,
  newest first, paginated (`meta.page/perPage/total`).
- `q` matches `request_id` or `error_code` exactly, or the message with an
  escaped `LIKE '%…%'` (only inside the date range, which keeps it cheap).
- `summary` returns errors per day (in `tz`) split by level, plus the top 10
  groups by `fingerprint` with count, first seen and last seen. Group by day with
  `DATE(CONVERT_TZ(occurred_at, '+00:00', ?))`. That needs MySQL's time zone
  tables: load them in the MySQL image (`mysql_tzinfo_to_sql /usr/share/zoneinfo | mysql mysql`).
- `client-errors` accepts only `android`, `ios`, `desktop` from the mobile app.
  `web_admin` and `web_landing` need the internal `X-Service-Token` that only the
  Next.js servers have. A client can never claim `api` or `queue`. Strings are
  cut to the column sizes, and the response is `202 { "data": { "accepted": n } }`.
- Add `errors.view` to the `Auth` permissions and give it to the admin roles
  that need it.

### Errors from the Next.js apps
```ts
// apps/admin/instrumentation.ts  (same file in apps/landing with source "web_landing")
import type { Instrumentation } from "next";

export const onRequestError: Instrumentation.onRequestError = async (error, request, context) => {
  const err = error as Error & { digest?: string };
  await fetch(`${process.env.API_INTERNAL_URL}/api/v1/client-errors`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Service-Token": process.env.MONITORING_SERVICE_TOKEN! },
    body: JSON.stringify({
      errors: [{
        occurredAt: new Date().toISOString(), platform: "web_admin", appVersion: process.env.APP_VERSION,
        tag: `${context.routerKind}:${context.routePath}`, message: err.message.slice(0, 1000),
        exceptionClass: err.name, stackTrace: err.stack?.slice(0, 8000), requestId: request.headers["x-request-id"],
      }],
    }),
  }).catch(() => {});                                  // never fail the page because reporting failed
};
```
`adminApi` sends the same `X-Request-Id` to PHP, so a Next.js error and the PHP
error behind it share one request ID in the panel.

### Admin portal page: `apps/admin/app/(dashboard)/errors/`
```
errors/
  page.tsx                 # RSC: parse filters, load list + summary in parallel
  [id]/page.tsx            # one error: all fields, trace, "Show similar" (same fingerprint)
  filters.ts               # Zod: searchParams -> ErrorFilters with defaults (pure, unit-tested)
  ErrorFilters.tsx         # "use client": date range, presets, source, level, module, search
  ErrorsPerDay.tsx         # small bar chart, server-rendered SVG/CSS, no chart library
  ErrorsTable.tsx          # presentational
```
```ts
// errors/filters.ts
import { z } from "zod";

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
const MAX_DAYS = 90;

const schema = z.object({
  from: isoDate.optional().catch(undefined),
  to: isoDate.optional().catch(undefined),
  tz: z.string().max(64).optional().catch(undefined),
  source: z.enum(["api", "queue", "android", "ios", "desktop", "web_admin", "web_landing"]).optional().catch(undefined),
  level: z.enum(["error", "warning"]).optional().catch(undefined),
  module: z.string().max(40).optional().catch(undefined),
  status: z.coerce.number().int().min(400).max(599).optional().catch(undefined),
  q: z.string().trim().max(100).optional().catch(undefined),
  page: z.coerce.number().int().min(1).catch(1).default(1),
});

export type ErrorFilters = z.infer<typeof schema> & { from: string; to: string; tz: string };

/** Bad values are dropped one by one; missing dates mean "the last 7 days, ending today in tz". */
export function parseErrorFilters(params: Record<string, string | string[] | undefined>, now: Date): ErrorFilters {
  const flat = Object.fromEntries(Object.entries(params).map(([k, v]) => [k, Array.isArray(v) ? v[0] : v]));
  const parsed = schema.parse(flat);
  const tz = isValidTimeZone(parsed.tz) ? parsed.tz! : "UTC";
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: tz }).format(now);   // "2026-10-28"
  let to = parsed.to ?? today;
  let from = parsed.from ?? addDays(to, -6);
  if (from > to) [from, to] = [to, from];
  if (daysBetween(from, to) >= MAX_DAYS) from = addDays(to, -(MAX_DAYS - 1));
  return { ...parsed, from, to, tz };
}
```
(`addDays`, `daysBetween` and `isValidTimeZone` are small pure helpers in the same
file, with their own tests.)
```tsx
// errors/page.tsx
import { adminApi } from "@/lib/admin-api";
import { parseErrorFilters } from "./filters";

export const dynamic = "force-dynamic";

export default async function ErrorsPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const filters = parseErrorFilters(await searchParams, new Date());
  const [list, summary] = await Promise.all([
    adminApi.errors.list({ ...filters, perPage: 50 }),
    adminApi.errors.summary({ from: filters.from, to: filters.to, tz: filters.tz }),
  ]);
  return (
    <section aria-labelledby="errors-title">
      <h1 id="errors-title">Errors</h1>
      <ErrorFilters value={filters} />
      <ErrorsPerDay days={summary.data.days} tz={filters.tz} />
      {list.data.length === 0
        ? <EmptyState title="No errors in this period" />
        : <ErrorsTable rows={list.data} meta={list.meta} tz={filters.tz} />}
    </section>
  );
}
```
```tsx
// errors/ErrorFilters.tsx
"use client";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useTransition } from "react";
import type { ErrorFilters as Filters } from "./filters";

export function ErrorFilters({ value }: { value: Filters }) {
  const router = useRouter();
  const pathname = usePathname();                      // without basePath; router adds /uat/admin back
  const params = useSearchParams();
  const [pending, startTransition] = useTransition();

  function update(patch: Record<string, string | undefined>) {
    const next = new URLSearchParams(params);
    for (const [key, v] of Object.entries(patch)) (v ? next.set(key, v) : next.delete(key));
    next.delete("page");                               // a new filter starts at page 1
    startTransition(() => router.replace(`${pathname}?${next}`));
  }

  useEffect(() => {
    if (!params.get("tz")) update({ tz: Intl.DateTimeFormat().resolvedOptions().timeZone });
  }, []);                                             // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <form role="search" aria-label="Filter errors" aria-busy={pending} onSubmit={(e) => e.preventDefault()}>
      <label>From <input type="date" name="from" defaultValue={value.from} max={value.to} key={`f${value.from}`}
        onChange={(e) => update({ from: e.target.value })} /></label>
      <label>To <input type="date" name="to" defaultValue={value.to} min={value.from} key={`t${value.to}`}
        onChange={(e) => update({ to: e.target.value })} /></label>
      <PresetButtons onPick={(from, to) => update({ from, to })} tz={value.tz} />   {/* Today, 7 days, 30 days */}
      {/* Source, Level, Module selects and a search box call update({ ... }) the same way */}
    </form>
  );
}
```
Page rules:
- **Filters live in the URL**, so a filtered view can be shared, bookmarked and
  reloaded. Default: last 7 days, all sources and levels.
- Times are shown in the admin's time zone with the zone name
  ("28 Oct 2026, 12:31 (Asia/Dhaka)"); the date inputs are in the same zone.
- Table columns: time, level chip (error = error color, warning = tertiary),
  source, module, `METHOD route`, status, error code, message (one line,
  truncated), request ID (copy button). Each row links to the detail page.
- Above the table: errors per day for the range, and "Top errors" (the summary's
  groups) with counts. Clicking a group filters by its fingerprint.
- States: loading (`loading.tsx` skeleton), empty ("No errors in this period"),
  API error, and 403 (a "You don't have access to errors" message, not a crash).
- Sidebar item **Errors** with a badge showing today's `error`-level count, only
  for admins with `errors.view`. The header shows the UAT/PROD badge, as on every page.

### Tests (write first, section 10 order)
1. **Contract:** the four paths and the `ErrorEvent`, `ErrorSummary` and
   `ClientErrorsRequest` schemas in `openapi.yaml`.
2. **Domain (Pest unit):** `Fingerprint` is the same for two 500s on one route
   and differs by route or exception; `ErrorLevel::fromStatus` (500 → error,
   422 → warning).
3. **Use cases** with an in-memory repository: `RecordError` saves the event;
   `RecordError` doesn't throw and writes to stderr when the repository throws;
   the flood guard stops after 100 per fingerprint per minute; `PruneErrors`
   deletes only rows older than the retention.
4. **Feature (MySQL):**
   - A controller that throws → 500 envelope to the client **and** one
     `error_logs` row with `module = Cms`, route template, exception class,
     trace and the request's `X-Request-Id`.
   - A 422 → a `warning` row with `context.fields = ["title"]` and no submitted
     values anywhere in the row (assert the posted password string is absent).
   - 2xx and 3xx → no row. A 503 with `X-App-Maintenance` → no row.
   - An unknown route → a 404 row with `module = Unknown` and a masked path.
   - A failing queued job → a `queue` row.
   - `GET admin/errors` with `from = to = 2026-10-28`, `tz = Asia/Dhaka` includes
     an error at `2026-10-27T18:30:00Z` and excludes one at `2026-10-28T18:00:00Z`.
   - `to` before `from` → 422 `validation_failed`; more than 90 days → 422
     `range_too_long`; without `errors.view` → 403.
   - Summary counts per day in `tz`. Responses validate against `openapi.yaml`.
   - `client-errors`: a batch of 20 → 202 `accepted = 20`; 21 → 422; the 31st call
     in a minute → 429; `platform = api` → 422; `web_admin` without the service
     token → 403.
5. **Web (Vitest):** `parseErrorFilters` defaults to the 7 days ending today in
   `tz` (fixed `now`), swaps reversed dates, clamps to 90 days, drops a bad
   `level` but keeps the rest; `ErrorFilters` changing **From** calls
   `router.replace` with the new `from` and no `page` (mock `next/navigation`);
   `ErrorsTable` shows the local time with the zone and the level chip.
6. **E2E (Playwright, UAT seed):** `ErrorLogSeeder` adds errors on three days.
   Open **Errors**, pick a one-day range, and only that day's rows stay; open a
   row and see its request ID and trace. Then call a test endpoint that fails
   and see the new row appear for today. Screenshot the panel (desktop and
   phone), the empty state and the detail page.

## 13. Show the result: screenshots (required)

Capture the real running apps with Playwright and save to
`docs/screenshots/web/<feature>/`:
- Landing: every changed page at phone (390×844) and desktop (1440×900), light
  and dark if the site supports it.
- Admin: list, empty, error and form-with-validation-errors states, plus one
  short recording (`recordVideo`) of the main flow.
```ts
await page.setViewportSize({ width: 390, height: 844 });
await page.screenshot({ path: "docs/screenshots/web/cms/landing-phone-home.png", fullPage: true });
```
Never fake or edit screenshots. Use neutral, non-copyrighted sample content,
own or open-licensed icons (Lucide, Material Symbols) and fonts (OFL). If
something can't be captured in your environment, say so and give the command.

## 14. Report added and changed files (required)

End every change with a file report built from `git status --porcelain`, grouped
by module (`backend/api/modules/Cms`, `backend/web/apps/admin`,
`backend/web/packages/sdui`, `backend/contracts`, …), with **Added**, **Changed**
and **Deleted** tables, exactly as in `kmp-feature-scaffold` section 13. Call out
risky items separately: migrations, contract changes that affect the mobile app,
new env vars, dependency upgrades.

## Checklist
- [ ] Everything web lives in `backend/`; landing at `backend/web/apps/landing`, admin at `backend/web/apps/admin`
- [ ] KMP code and `backend/` share only `backend/contracts/openapi.yaml`
- [ ] TDD: contract → domain → use case → HTTP feature → web packages → app → e2e, tests written first, all green, none skipped
- [ ] Web module graph holds (apps → packages, no app-to-app, `server-only` on server code)
- [ ] PHP module rules hold (Domain framework-free, no cross-module Infrastructure access), arch tests green
- [ ] All responses use the `{ data, error, meta }` envelope; errors mapped centrally
- [ ] Migrations additive; indexes for new queries; no N+1; no string-built SQL
- [ ] Landing is static/ISR with tag revalidation on publish; minimal client JS
- [ ] Server-driven sections validated with Zod; unknown types skipped; no HTML from the API
- [ ] App status (6b): public `app/status` decides none/soft/force by build; maintenance returns 503 + `X-App-Maintenance` on mobile routes only; admin page confirms force update and start-now with the environment named
- [ ] Push (6c): every push, recipient and event kept (hide, never delete); inbox returns the latest 30 per device; four types validated (`deeplink` targets match `openapi.yaml`, `https` only); queued fan-out sends each device once; Android data-only, iOS APNs alert; per-environment FCM project checked at boot; admin page has preview, reach estimate, test send, PROD confirm and per-push stats (sent, failed, received, opened, open rate)
- [ ] Legal (6d): `/privacy` and `/terms` live per env (PROD URL in the stores, never `/uat`); published versions immutable and kept; publish blocked while `{{…}}` / `[REVIEW` placeholders remain; Markdown subset only; `mustAccept` from the latest required version; acceptances store version + hash, no IP; footer links on every page; template text reviewed by a qualified person (told to the user)
- [ ] Edge code is tiny (cookies, JWT verify, redirects); no DB at the edge
- [ ] Admin: SSR + Server Actions, browser never calls PHP, PHP checks every permission
- [ ] Secrets in env only; security headers set; audits clean
- [ ] UAT under `/uat` with its own DB, Redis, keys and cookies; basePath per env; UAT is noindex and badged
- [ ] Error log (12c): `LogApiErrors` covers every API route (module, route template, status, code, exception, request ID); queue failures and app/web errors ingested; no bodies, tokens or personal data stored; never breaks the request; pruned after 90 days
- [ ] Admin **Errors** page: date range filter (inclusive, admin's time zone, max 90 days, in the URL), source/level/module/search filters, per-day chart, top errors, detail page with trace; `errors.view` required
- [ ] Screenshots captured from the real apps; file report included
