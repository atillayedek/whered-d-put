# Where Did I Put It?

> Save it now. Find it later.

A calm, private Android app for remembering where you put things. Type, say
or photograph *what* and *where*, then find it weeks later by searching for
it.

- **Offline-first.** Every save, edit, favourite and delete lands in the local
  Room database immediately. Supabase sync happens in the background with
  WorkManager (network constraint + exponential backoff).
- **Private by design.** Supabase Auth, Postgres Row Level Security on every
  table, per-user Storage folders, no analytics, no logging of personal data,
  photos re-encoded without EXIF metadata.
- **Real account deletion** through a Supabase Edge Function. The service-role
  key never ships in the app.

---

## Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Backend setup (Supabase)](#backend-setup-supabase)
4. [Building the app](#building-the-app)
5. [Release & Google Play](#release--google-play)
6. [Security notes](#security-notes)
7. [Project status](#project-status)

---

## Features

| Area | What it does |
| --- | --- |
| Onboarding | One calm screen explaining write / speak / photo. |
| Auth | Email + password sign-up with email confirmation, sign-in, sign-out, forgot/reset password (deep link `wheredidiputit://auth-callback`, PKCE). Session survives restarts; the app stays usable offline when the token can't refresh. |
| Remember | *Item* + *Where is it?* are the only required fields. Category and note are behind "Category and note" (progressive disclosure). |
| Speak | Android `SpeechRecognizer`. The transcript is shown and split best-effort into item and location (English and Turkish patterns); both fields stay editable. Hidden when the device has no recogniser; manual entry always works. |
| Photo | Android Photo Picker (no storage permission). MIME + size validated, downscaled to 1600 px, re-encoded as JPEG (drops EXIF/GPS), uploaded to `users/{user_id}/items/{item_id}/…` in a private bucket and cached on-device. |
| Search | Case- and accent-insensitive ("cekmece" finds "Çekmece"), matches title, location and note, debounced, title matches ranked first, runs on Room so it works offline. |
| Detail | Location shown prominently, photo, note, category, saved/edited time, sync status. Edit, favourite, delete with confirmation. |
| Favorites | Bottom-nav tab with the hearted memories. |
| Categories | Built-in taxonomy (Documents, Keys, Electronics, Clothes, Tools, Other) + custom categories created inline. |
| Settings | Email, theme (System/Light/Dark), Privacy, Sign out (warns about unsynced memories), Delete account, app version. |
| Ads | Google AdMob interstitials at natural pauses only (after saving, after leaving a memory). At most 3 per day, 2 hours apart, none in the first 24 hours after install, never during search or typing. Google UMP consent form where required, "Ad privacy choices" in Settings. |
| Sync | States `SYNCED`, `PENDING_CREATE`, `PENDING_UPDATE`, `PENDING_DELETE`, `FAILED`. Soft delete via `deleted_at`. Local edits win over older server rows. |

## Architecture

Clean Architecture + MVVM, single module, no over-abstraction:

```
app/src/main/java/com/wheredidiputit/
├── core/          config, Hilt modules, design system (WdipiTheme, WdipiColors,
│                  WdipiTypography, WdipiSpacing, WdipiShapes + components), utils
├── domain/        models, repository interfaces, pure logic (SearchText, SpeechParser)
├── data/          Room (entities, DAOs), DataStore, Supabase client + DTOs,
│                  repositories, image processing, SyncEngine / SyncWorker
└── presentation/  Compose screens + ViewModels, Navigation Compose (type-safe routes)
```

Write path: **ViewModel → Repository → Room → UI updates from Room → SyncWorker
→ Supabase**. The UI never waits on the network for local operations.

Stack: Kotlin 2.2, Jetpack Compose + Material 3, Coroutines/Flow, Hilt, Room,
WorkManager, Navigation Compose, DataStore, Coil 3, supabase-kt 3.2 (Auth,
Postgrest, Storage, Functions) on Ktor/OkHttp. `minSdk 26`, `targetSdk 36`.

## Backend setup (Supabase)

You need a Supabase project and the [Supabase CLI](https://supabase.com/docs/guides/cli).

```bash
supabase link --project-ref <your-project-ref>
supabase db push                                  # applies supabase/migrations/*
supabase functions deploy delete-account          # account deletion
```

The migrations create:

- `profiles`, `categories`, `items` (UUID keys, `timestamptz` UTC, length
  checks, indexes for sync and listing), triggers for server-side
  `updated_at`, immutable `created_at`, category ownership checks and
  automatic profile creation.
- RLS enabled and forced on all tables; `auth.uid() = user_id` policies; no
  access for `anon`; no client hard-deletes.
- A private `item-photos` bucket (JPEG only, 5 MB limit) with per-user folder
  policies.

Then, in the Supabase dashboard:

1. **Authentication → Providers → Email:** enable email sign-ups and
   *Confirm email*.
2. **Authentication → URL Configuration:** set *Site URL* to
   `https://wheredidiputit-ochre.vercel.app/auth/callback` and add both
   `https://wheredidiputit-ochre.vercel.app/auth/callback**` and
   `wheredidiputit://auth-callback**` to *Redirect URLs*. Email links open that
   web page, which hands the one-time code to the app on the phone, or shows
   "Your email is confirmed" anywhere else (instead of a blank page).
3. **Edge Functions → delete-account:** keep *Verify JWT* on. The function uses
   the built-in `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` secrets.
4. For production email volume, configure a custom SMTP provider.

## Building the app

Requirements: JDK 17+, Android SDK with platform 36.

Put the **client-safe** values in `local.properties` (git-ignored) or in
environment variables:

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<anon or publishable key>
```

Only the project URL and the anon/publishable key belong in the app. **Never**
put the service-role key, database password or any admin token in
`local.properties`, the app, or git.

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew lintRelease            # Android lint
./gradlew bundleRelease          # release AAB (R8 minified, resources shrunk)
```

### Ads (AdMob)

Create an app and an **Interstitial** ad unit in [AdMob](https://admob.google.com),
then set:

```properties
ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
ADMOB_INTERSTITIAL_ID=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

Debug builds always use Google's official test ad IDs, so you never see or
click real ads while developing. Release builds show ads only when both real
IDs are set; otherwise no ad is ever requested. In AdMob, also publish a
GDPR/UK consent message (*Privacy & messaging*) so the consent form appears.

Without credentials the app still builds and runs, but shows a clear
"Not connected yet" screen instead of pretending to work.

CI (`.github/workflows/android.yml`) runs unit tests, lint, the debug APK and
the release AAB on every push, then starts the debug APK on an Android 14
emulator, walks the signed-out screens in light and dark mode, fails on any
crash and keeps the screenshots as the `smoke-test-screenshots` artifact. Add `SUPABASE_URL` and `SUPABASE_ANON_KEY` as
repository secrets to produce a connected build, and `ADMOB_APP_ID` /
`ADMOB_INTERSTITIAL_ID` for a release build with ads.

## Website

`website/` is the static landing page (English/Türkçe, live search demo, beta
download) plus `/auth/callback`. It is the Vercel project `wheredidiputit`
(root directory `website`, no build step), served at
https://wheredidiputit-ochre.vercel.app. Connect the Vercel project to this
GitHub repository so every push redeploys it.

The beta download points at the `beta` GitHub release, which CI refreshes
with the tested debug APK after every green build of the default branch. A
cached debug key keeps the signature stable so new betas install over old ones.
Set `AUTH_WEB_URL` to override the website address the app uses for email links.

## Release & Google Play

- `applicationId` `com.wheredidiputit.app`, `versionCode` / `versionName` in
  `app/build.gradle.kts`. Bump both for every upload.
- **Signing:** create a keystore outside the repo and a git-ignored
  `keystore.properties` in the project root (or the same keys as environment
  variables):

  ```properties
  WDIPI_KEYSTORE_FILE=/absolute/path/to/upload-keystore.jks
  WDIPI_KEYSTORE_PASSWORD=…
  WDIPI_KEY_ALIAS=upload
  WDIPI_KEY_PASSWORD=…
  ```

  `bundleRelease` signs automatically when these are present; otherwise it
  produces an unsigned AAB. Use Play App Signing.

  For CI, add the keystore as repository secrets: `WDIPI_KEYSTORE_BASE64`
  (output of `base64 -w0 upload-keystore.jks`), `WDIPI_KEYSTORE_PASSWORD`,
  `WDIPI_KEY_ALIAS`, `WDIPI_KEY_PASSWORD`. The keystore is decoded into the
  runner's temp folder only for the build and deleted afterwards.
- Adaptive + themed (monochrome) launcher icon, splash screen, backups
  disabled, cleartext traffic disabled.
- **Privacy policy:** publish [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md)
  at a public URL after adding your legal entity and contact address, and link
  it in the Play Console.
- **Ads:** declare *Contains ads* in Play Console, and host an `app-ads.txt`
  file on your developer website as AdMob asks.
- **Store listing texts (EN/TR):** see [`docs/STORE_LISTING.md`](docs/STORE_LISTING.md).
- **Data safety form:** see [`docs/DATA_SAFETY.md`](docs/DATA_SAFETY.md).
- **Account deletion:** in-app (Settings → Delete account). Google Play also
  requires a web link where users can request deletion; point it to a page or
  support address you control.

## Security notes

- RLS is the security boundary; the app's `user_id` filters exist only for
  index use.
- Storage paths are validated both by bucket policies and by a check
  constraint on `items.image_url`.
- Memories and photos are never passed to the ad SDK; ads only ever see what
  the AdMob SDK itself collects.
- The Supabase client logs nothing (`LogLevel.NONE`); the app contains no
  `Log` calls with personal data; release builds strip `Log.v/d/i`.
- Auth deep links use PKCE, so an intercepted link is useless without the
  verifier stored on the requesting device.
- Only `MainActivity` is exported (launcher + auth deep link). WorkManager's
  default initializer is replaced by the Hilt worker factory.
- `allowBackup=false` and data-extraction rules exclude everything.

## Project status

What is implemented and verified in this repository:

- Full Android app source for every MVP feature listed above.
- Supabase migrations — applied to PostgreSQL 16 and checked for RLS isolation
  between users, anonymous access, storage folder isolation, ownership
  spoofing, category ownership, soft-delete-only, server timestamps and
  account-deletion cascade.
- Unit tests for search normalisation and speech parsing.
- CI build of the debug APK and the release AAB.

What needs your credentials before the app is usable end-to-end:

- A Supabase project with the migrations applied and the Edge Function
  deployed, plus `SUPABASE_URL` / `SUPABASE_ANON_KEY` for the build.
- An upload keystore for a signed release.
- A hosted privacy policy URL with your contact details.

Until those exist, sign-up, sync, photo upload and account deletion can't be
exercised against a live backend, so the app should not be called
production-ready yet.
