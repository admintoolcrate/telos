# Telos: conventions

Telos is a private, offline-first task manager and goal tracker for Android. No accounts, no
servers, no ads, no analytics. Data lives on the device; the only thing that can ever leave it
is the user's own passphrase-encrypted backup, and only after they switch it on.

## Commands
- `./gradlew assembleDebug test` must pass before every commit. (`test` runs the `:domain`
  JUnit 5 suites and the `:app` Robolectric suites; release unit tests are disabled.)
- The Android SDK lives outside the repo; `local.properties` (git-ignored) points at it.
- Room schemas are exported to `app/schemas/` and committed.

## Modules
- `:domain`: pure Kotlin/JVM, no Android imports. Recurrence, quick-add parser, filter
  language, reminder planning, import parsers, codecs, merge, backup crypto. **New logic goes
  here, with tests.**
- `:app`: Room, Hilt, Compose UI (ViewModel + `StateFlow<UiState>`), alarms, WorkManager,
  Glance widgets, capture surfaces, app lock, Drive.

## Data rules
- Every syncable row has `id` (UUIDv7 from `Ids.newId`), `createdAt`, `updatedAt` (epoch ms),
  `deviceId`, and `deletedAt` (tombstone). Bump `updatedAt` on *every* write: merges are
  last-writer-wins per record, ties broken by `deviceId`.
- User deletion sets `trashedAt` (restorable for 30 days); purging scrubs content and sets
  `deletedAt`. Never hard-delete a syncable row.
- Ordering uses fractional string keys (`OrderKeys`), so a reorder writes one row.
- Any schema change bumps the Room version and ships a `Migration` (or `AutoMigration`)
  **and** a migration test in `app/src/test/.../data/MigrationTest.kt`.
- Due times are wall-clock (`LocalDate` + minute of day), floating by default; they turn into
  an `Instant` only when a reminder is scheduled.

## Time
- Never call `LocalDate.now()`, `Instant.now()` or `System.currentTimeMillis()` in logic.
  Take a `java.time.Clock` and a `ZoneId` (injected via Hilt in `:app`).
- Recurrence, parsing and reminders are the riskiest code: every change needs tests across DST
  (spring-forward gap, fall-back overlap), month ends, leap years and time-zone changes.

## Network
- Only `backup/NetworkGate.kt` may open a connection, and only while backup is on.
  `NetworkIsolationTest` fails the build if anything else references `java.net`/`HttpURLConnection`.
- Never fetch a shared URL's page title or favicon.

## Tone and look
- Telos follows the ToolCrate family look shared with Stagira, Lyceum and Kairos:
  **read `docs/family-look.md`** (the same file in all four repos; change it in all four).
  Mono palette (`ui/theme/Theme.kt`: black on white, true black at night; `accent` is ink),
  EB Garamond only for display moments and `ScreenTitle`, Inter for everything else, 24 dp
  cards, pill buttons. Play icon and feature graphic: `tools/family_graphics.py`.
- `ui/RenderTest` draws real screens in light and dark to `app/build/screens/`; look at them
  after UI changes.
- No gamification, streaks, karma or guilt copy. Overdue items sit under a neutral "Earlier"
  heading, in normal ink: never red, never "You missed…".
- Priority colours always come with a text or shape cue.

## Compose
- Never write to state during composition (it loops forever). Derive with `remember(key)`, or
  change state in event handlers and effects. `AccessibilityTest` catches this as an idle timeout.
- Feature screens register in `ui/Routes.kt`; task-screen sections in `TaskDetailSlots`.
- Records from `:domain` can't be smart-cast across modules: use `?.let` / local vals.

## Accessibility checklist (every screen)
- Every icon-only control has a `contentDescription`; headings use `Modifier.semantics { heading() }`.
- Swipe and drag gestures have equivalent `customActions` (complete, reschedule, move up/down).
- Layouts survive font scale 2.0 (no fixed heights on text containers).
- Animations check `LocalReduceMotion`.
- `AccessibilityTest` renders the main screens at 1x and 2x text and fails on any clickable node
  without text or a content description; add new screens to it.

## Commits
- One commit per milestone or coherent change, imperative subject ("Add recurrence engine"),
  body explains why. Branch: `claude/laughing-davinci-f7bafy`.

## Website (telos.toolcrate.cc)
- `docs/` is the public website, served by GitHub Pages from `main` (`docs/CNAME`). It is Jekyll:
  `index.html` (home), `support.html`, `404.html`, the privacy policy and delete-your-data pages,
  all in `_layouts/default.html`; names, links and the family list live in `docs/_config.yml`.
- **Keep it true to the app.** When a change adds, removes or renames something a person would
  notice (a feature, a screen, a setting, what's stored or sent, supported Android versions,
  Play availability), update the home page's features, steps, FAQ or private panel in the same
  change, along with the README, store listing and privacy policy where they say the same thing.
  Internal refactors and fixes need no site change.
- The phone on the home page shows `docs/play-store/screenshots/` directly; when you redraw the
  store screenshots, check each slide's alt text and caption (`data-name`, `data-desc`) still fits,
  and add or drop a slide (and its dot) if the set changes.
- Follow the family look and voice (docs/family-look.md; no gamification or guilt copy). Quote only built-in copy or
  verified passages (Lyceum's `quotes/Quotes.kt`, word for word); no claims the app doesn't back up.
- `docs/assets/site.css`, `site.js` and `_layouts/default.html` are the same in Lyceum, Telos and
  Kairos; change them in all three. Preview: `cd docs && jekyll build` (or `jekyll serve`), then
  look at the home page in light, dark and at phone width.
