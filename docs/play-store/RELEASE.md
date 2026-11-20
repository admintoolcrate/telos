# Getting Telos onto Google Play

A checklist, in order. Steps marked **(you)** need your accounts or decisions; everything else is
already set up in this repo. It matches Lyceum's and Kairos's `docs/play-store/RELEASE.md` (same
ToolCrate developer account), written against Google's rules as of late 2026; check anything that
may have changed since.

What's ready in the repo:

- Release signing from `keystore.properties` or CI secrets (`app/build.gradle.kts`).
- `.github/workflows/build.yml`: tests and a debug APK on every push. A release run builds the signed
  bundle and, once set up, uploads it to **Internal testing**.
- Version codes come from the CI run number (1000 + run), so every upload is higher than the last.
- Target SDK 36, which Play requires for new apps from 31 August 2026.
- Privacy policy, data-safety answers, store text, icon, feature graphic and screenshots in this folder.

---

## 1. Developer account (you)

Use the ToolCrate account Stagira is published from. Keep trader status **non-trader**.

## 2. Upload key (you, once; keep it safe)

On your own computer (not in the repo):

```sh
keytool -genkeypair -v -keystore telos-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Keep the `.jks` file and its passwords in a password manager. **Never commit them**: `.gitignore`
already blocks `*.jks` and `keystore.properties`. Reusing the upload key of Lyceum or Kairos also
works; a separate one is tidier.

Then either:

- **Build locally:** copy `keystore.properties.example` to `keystore.properties`, fill it in, and run
  `./gradlew :app:bundleRelease`. The bundle is `app/build/outputs/bundle/release/app-release.aab`.
- **Or let GitHub build it.** In the telos repo, open **Settings → Secrets and variables → Actions** and add:
  - `UPLOAD_KEYSTORE_BASE64`: the output of `base64 -w0 telos-upload.jks`
  - `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS` (`upload`), `UPLOAD_KEY_PASSWORD`

  Then run **Actions → Build → Run workflow** with **release** ticked (or push a tag such as `v0.1.0`).
  The run's `telos-play-bundle` artifact is the signed `.aab`.

## 3. Create the app in Play Console (you)

1. **Create app**: name *Telos – Tasks & Goals*, language English, **App**, **Free**.
2. The package is `cc.toolcrate.telos`. It can never change once used.
3. **Upload the first bundle by hand** (Test and release → Testing → Internal testing → Create
   release) and accept **Play App Signing**. Play's upload API only works for an app that already
   has a release, so automatic uploads (step 10) start from the second build.

## 4. App content (Policy → App content) (you, about 20 minutes)

| Section | Answer |
|---|---|
| Privacy policy | `https://admintoolcrate.github.io/telos/privacy/` (step 6) |
| Ads | **No** |
| App access | All functionality is available without special access |
| Content rating | Category **All other app types**. No violence, no user-to-user content or sharing, no purchases, no location sharing |
| Target audience | **18 and over** (as for Stagira and Lyceum; keeps it out of the Families programme). It can be widened later |
| Data safety | See [`data-safety.md`](data-safety.md) |
| Health apps | **My app does not have any health features.** |
| Advertising ID | **No** |
| Government, financial, news | No |

**Exact alarms:** Telos asks for `SCHEDULE_EXACT_ALARM` (the person grants it in Android settings),
not `USE_EXACT_ALARM`, so no exact-alarm declaration is needed. If Play Console asks anyway, the
answer is: reminders the user sets for a task at a chosen time.

Internal testing can start before every section is complete. Closed testing and production need all
of them.

## 5. Store listing (you)

Copy from [`store-listing.md`](store-listing.md). Graphics are in `graphics/` and `screenshots/`.

## 6. Host the privacy policy (you)

The repo's `docs/` folder is a small GitHub Pages site:

1. On GitHub, open **Settings → Pages**, set Source to **Deploy from a branch**, choose branch `main`
   and folder **/docs**, then **Save**.
2. The policy appears at `https://admintoolcrate.github.io/telos/privacy/`.
3. Optional: to use `telos.toolcrate.cc`, add a `docs/CNAME` file with that name and a DNS `CNAME`
   record pointing to `admintoolcrate.github.io`.

## 7. Google sign-in for Drive backup (you)

Drive backup only connects once Google knows this app:

1. In Google Cloud Console (the Stagira project can host it), enable the **Google Drive API**.
2. **OAuth consent screen**: app name Telos, support email, the privacy policy URL, scope
   `https://www.googleapis.com/auth/drive.appdata` (non-sensitive). Until it is published, only
   test users you add can connect: add the testers' Google accounts.
3. In Play Console, open **Test and release → Setup → App signing** and copy the **SHA-1** of the
   *App signing key* and of the *Upload key*. In Google Cloud **Credentials**, add an **Android
   OAuth client** for each SHA-1 with package `cc.toolcrate.telos`.

Without this, everything except Drive backup works.

## 8. Internal testing (you)

Internal testing has no review and reaches up to 100 testers within minutes.

1. Testing → Internal testing → **Testers**: create an email list (yourself and a few others).
2. Share the opt-in link; testers install from Play.
3. Things to check on real phones (they can't be checked in CI):
   - [ ] Quick add in words ("Call dentist tomorrow 3pm #Health p2"); edit, complete, undo.
   - [ ] A reminder a few minutes ahead arrives on time; it still arrives after a phone restart and
         after changing the time zone. Android 14+: allow *Alarms & reminders* when asked.
   - [ ] A recurring task moves on when completed.
   - [ ] Widgets (Today, Add), the Quick Settings tile, app shortcuts, sharing text from another
         app and *Add to Telos* on selected text.
   - [ ] App lock: PIN, wrong-PIN wait, fingerprint/face; widgets and quick add behave as expected.
   - [ ] Export to a file, uninstall, reinstall, import: everything is back.
   - [ ] Drive backup (after step 7): back up, restore on a second phone, wrong passphrase refused.
   - [ ] Dark mode, the largest font size, TalkBack, a small phone.

## 9. Closed testing, then production (you)

If the ToolCrate account is a **personal** account created after 13 November 2023, each new app
needs a **closed test with at least 12 testers opted in for 14 days in a row** before you can apply
for production access. Internal testing doesn't count. Organisation accounts are exempt.

## 10. Uploads from GitHub (one-time setup)

Release runs can upload straight to **Internal testing**:

1. Play Console → **Users and permissions**: give the service account already used for Stagira,
   Lyceum or Kairos access to **Telos** with **Release to testing tracks** and *View app information*.
2. Telos repo → **Settings → Secrets and variables → Actions**: add `PLAY_SERVICE_ACCOUNT_JSON`,
   the service account's JSON key.

From then on, **Actions → Build → Run workflow → release** (or a `v*` tag) sends a build to Internal
testing, with the commit's first line as the testers' "What's new". Promoting further stays a click
in Play Console.

## Every update

- CI numbers builds itself. For a local build, raise `versionCode` in `app/build.gradle.kts` above
  the last one Play has.
- Raise `versionName` for releases people will see (it's 0.1.0 now).
