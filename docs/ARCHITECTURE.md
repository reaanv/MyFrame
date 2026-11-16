# Architecture

MyFrame is a single-module Android app (`:app`, package `com.myframe.tv`) written in Kotlin with Jetpack Compose.
It has one activity, no fragments, and no dependency-injection framework. About 4,700 lines of Kotlin.

```
            ┌────────────────────────── ui/ ──────────────────────────┐
 remote ──▶ │ Browse.kt  Manage.kt  viewer/Viewer.kt   kit/Kit.kt     │
            └───────────────┬───────────────────────────┬─────────────┘
                            │                           │
                  core/ (pure Kotlin)            data/ (Android)
            SlideshowEngine, FrameGeometry,   Room DB, ImageStore,
            Models, StickLimits, Ambience     MediaRepository, ArtUpdater
                            ▲                           ▲
                            │                           │
 phone ──▶ server/ UploadServer + Pairing ──────────────┘
 Android ─▶ system/ Apps, HomeRole, BootReceiver, FrameDreamService
```

## Packages

| Package | Files | Responsibility |
|---|---|---|
| `core/` | `Models.kt`, `SlideshowEngine.kt`, `FrameGeometry.kt`, `StickLimits.kt`, `Ambience.kt` | Pure logic with no Android imports. Playback order, repeat and shuffle; frame, mount and image rectangles; memory and storage limits; evening dimming and night black-out. This is what the JVM unit tests cover. |
| `data/` | `Db.kt`, `ImageStore.kt`, `MediaRepository.kt`, `ArtUpdater.kt` | Room database (`media`, `albums`, album items, `presets`), bitmap decoding and caches, settings, photo import, and the daily painting download from the Met Open Access API. |
| `server/` | `UploadServer.kt`, `Pairing.kt` | The phone-upload HTTP server (NanoHTTPD) and its PIN pairing, session tokens and network guard. Serves `assets/web/index.html`. |
| `system/` | `System.kt` | Launching other apps, the optional Home role, the boot receiver and the screensaver (`DreamService`). |
| `ui/` | `Browse.kt`, `Manage.kt`, `viewer/Viewer.kt`, `kit/Kit.kt` | Compose screens. `Kit.kt` holds the shared D-pad components, focus memory, dialogs and the QR code. |
| root | `MainActivity.kt`, `MyFrameApp.kt` | Navigation stack (`Screen`, `Nav`) and the object graph (`Graph`) created once in the `Application`. |

## Key flows

**Showing a picture.** `ViewerScreen` asks `PlaylistBuilder` for a playlist from a preset's `Source`
(all art, favourite art, all photos, favourite photos or an album). `SlideshowEngine` decides the next index.
`ImageStore` decodes the file at the size it will be drawn, capped by `StickLimits.decodeSize`, and
`FrameGeometry` computes the frame, mount and image rectangles that `FramedPicture` draws.

**Memory discipline.** `StickLimits` fixes a 24 MB full-picture cache, an 8 MB thumbnail cache and a Full HD
pixel budget, even when the stick outputs 4K. When another app comes to the front, the viewer releases its
current and transition bitmaps and clears the cache, then reloads on return. This leaves memory for a music app.

**Importing a photo.** `MediaImporter` serialises imports so two decodes never overlap. It verifies the file by
decoding it (JPEG, PNG or WebP), stores it under its SHA-256 so duplicates are detected and client file names
never become paths, resizes to a 1920-pixel long edge by default, and refuses to use the last 1 GB of storage.

**Phone upload.** `UploadController` starts `UploadServer` on port 8080 when the user opens Phone upload, and
stops it after 30 idle minutes or when the network drops. `NetGuard` accepts only loopback, private and
link-local client addresses and checks the `Host` header against the TV's own IP. `Pairing` issues a random PIN
per start, locks after five wrong tries, and hands out random session tokens. State-changing calls need the
token in a header. Every non-GET response closes its connection so an unread request body cannot corrupt the
next request.

| Route | Method | Purpose |
|---|---|---|
| `/` | GET | the phone dashboard page |
| `/api/pair` | POST | exchange the PIN for a session token |
| `/api/state` | GET | storage, albums and current display |
| `/api/photos` | GET | photo list and thumbnails |
| `/api/upload` | POST | upload one image |
| `/api/collections` | POST | create, rename or delete an album |
| `/api/photos/delete`, `/api/photos/favorite` | POST | bulk actions on photos |
| `/api/display` | POST | choose what the TV shows |
| `/api/logout` | POST | end the session |

**New paintings.** `ArtUpdater` pages the Met collection search, keeps only public-domain paintings by an
allow-list of painters, downloads a few per day and stops at 300.

**System integration.** `HomeAlias` is an activity alias with the `HOME` category; it is disabled until the
user opts in. `BootReceiver` starts the activity after boot when enabled, which needs the
`SYSTEM_ALERT_WINDOW` app-op on Android 10 and newer. `FrameDreamService` shows a preset as the screensaver.

## Scripts

| Script | Purpose |
|---|---|
| `tools/appliance.sh` | `list`, `apply`, `undo`. Makes MyFrame the Home app over adb, disables only recognised launcher and recommendation packages, verifies the handover, rolls back on failure, and keeps an undo record per device in `tools/.appliance/`. |
| `tools/install-stick.ps1` | Windows installer: connects, installs with `-r` (keeps photos), opens the app. Never uninstalls. |
| `tools/upload-smoke.postman_collection.json` | 12 requests and 21 assertions against the upload API. |
| `tools/tv.sh` | Emulator helper: send remote keys, take a screenshot, dump visible text. |
| `tools/fetch_art.py`, `tools/make_branding.py` | Regenerate the bundled art catalogue and the launcher banner. |

## Build and test

Gradle 9 with AGP 9 and Kotlin 2. Versions are pinned in `gradle/libs.versions.toml`.
`./gradlew :app:testDebugUnitTest` runs 42 JVM tests (`CoreLogicTest`, `StickLimitsTest`). There are no
instrumented tests; device behaviour is checked by hand with [HARDWARE_CHECKLIST.md](HARDWARE_CHECKLIST.md).
CI (`.github/workflows/ci.yml`) runs the unit tests and builds the debug APK on every push and pull request.
Release APKs are built and signed locally, because the signing key is kept out of the repository and out of CI.
