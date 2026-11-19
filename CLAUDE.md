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
- Calm and bookish. "Paper" palette (`ui/theme/Theme.kt`): cream page, brown-black ink, sepia
  accent. EB Garamond for headings (`titleLarge` and up), Inter for everything else.
- No gamification, streaks, karma or guilt copy. Overdue items sit under a neutral "Earlier"
  heading, in normal ink: never red, never "You missed…".
- Priority colours always come with a text or shape cue.

## Accessibility checklist (every screen)
- Every icon-only control has a `contentDescription`; headings use `Modifier.semantics { heading() }`.
- Swipe and drag gestures have equivalent `customActions` (complete, reschedule, move up/down).
- Layouts survive font scale 2.0 (no fixed heights on text containers).
- Animations check `LocalReduceMotion`.

## Commits
- One commit per milestone or coherent change, imperative subject ("Add recurrence engine"),
  body explains why. Branch: `claude/laughing-davinci-f7bafy`.
