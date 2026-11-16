# MyFrame: project status

Last updated 2026-11-16. "Tested" means observed on the Android TV emulator (API 31, x86, 1080p, 2 GB RAM) or in
JVM unit tests. Nothing has run on the physical Xiaomi stick yet; its exact generation/firmware is unknown.

## Published (2026-11-16)

- Source: <https://github.com/reaanv/MyFrame> (public, branch `main`). The repository holds no keystore,
  no `keystore.properties`, no APK and no personal photos; `.gitignore` excludes them.
- Release: <https://github.com/reaanv/MyFrame/releases/tag/v1.1.0> with the signed `MyFrame.apk` and its SHA-256.
  The public download was fetched back and its SHA-256 matched the local file. Signature verified with `apksigner`
  (APK Signature Scheme v2).
- Site: <https://myframe-seven.vercel.app> (Vercel project `myframe`, root directory `site/`, deploys on every push
  to `main`). Checked in a browser at 390 px and 1440 px. It is a download and setup page only: it cannot reach
  a stick on a private network, and it says so.
- CI: `.github/workflows/ci.yml` runs the unit tests and builds a debug APK on push and pull request.
- Local history before publication is kept on the unpublished branch `legacy-local`.

## Blocked: needs the physical stick

No Xiaomi stick was connected (only the emulator), so these remain unverified: exact model and firmware,
install on hardware, Home / boot / undo on hardware, and Spotify or YouTube Music playing while art is shown.
Use `docs/HARDWARE_CHECKLIST.md` when the stick is available.

## MyFrame 1.1.0: Xiaomi 2 GB / 8 GB changes

- Release/debug builds and 42 JVM unit tests pass. Version code 2; original release signing key retained.
- Full-picture cache capped at 24 MB, thumbnail memory at 8 MB. Display decodes use a Full HD pixel budget,
  including cover crops and 4K output. Existing photos are preserved; new optimised imports use a 1920-pixel long edge.
- Imports are serialised and leave 1 GB free for Android/music/update storage, including while receiving bytes.
- Viewer releases current/transition pictures and cached images while another app is in front, then reloads on return.
- Home uses two readable rows, verified at 1080p with the emulator's D-pad.
- YouTube Music fallback opens the installed YouTube TV app; the separate Music launcher is optional.
  This checks launch/navigation, not authenticated playback or simultaneous YouTube Music and art.
- Appliance mode now selects a single adb device, stores undo state per serial, limits disabled packages to known
  launchers/recommendations, checks direct Home launch, verifies handover and rolls back if handover fails.
  Apply, repeat apply, Home → MyFrame and undo verified on emulator. Original Home and disabled-package state restored.
- PowerShell installer syntax checked. It installs an update without deleting an existing photo library.
- Upload smoke collection added for pairing/access control, storage limits, image optimisation, duplicates and logout.
  It caught unread POST bodies corrupting reused HTTP connections. POST responses now close the connection safely.
- Final release installed through `install-stick.ps1` on the emulator, preserving the existing library. The local
  Postman collection passed 12 requests / 21 assertions with zero failures against this release. All 42 JVM tests pass.

## Earlier tested baseline (1.0; historical measurements)

- **Builds.** Debug and release APKs build (AGP 9.5.1, Kotlin 2.4.21, Gradle 9.6.1). 37 JVM unit tests pass.
  The release APK (R8-minified, resources shrunk, signed with the local key) is 15.1 MB; debug is 24.5 MB.
- **Release build, fresh install:** first-run setup, home, Art Mode slideshow, My Photos, phone-upload API
  (pairing, wrong PIN, JPEG/PNG/WebP upload, fake-file rejection, duplicate detection, thumbnails, 500-id guard).
- **Phone upload web page**, driven in a real browser against the debug build: pair, wrong PIN, create album,
  upload four files, select, add to album, delete with confirmation, rename, "Show on TV", log out, duplicate
  and oversize handling. Two rounds of Codex bug review; all thirteen findings fixed and five rechecked as fixed.
- **Memory** (`dumpsys meminfo`, total PSS): release build 50 MB on the home screen and 62 MB in a slideshow.
  The debug build was 133 MB in a slideshow before pictures were decoded at their drawn size.
- **New paintings over Wi-Fi:** five public-domain paintings downloaded from the Met's paged v1.1 search and shown
  in Art Mode. Painter allow-list keeps abstract and cubist work out. Capped at 300 downloads.
- **Evening softening, burn-in shift, black screen at night** (night screen measured: fully black, any key wakes it).
- **Home tiles:** YouTube opens the real app; Spotify and YT Music show a clear message when not installed.
- **Appliance mode** (`tools/appliance.sh`): apply, apply again, undo. Home button opens MyFrame; undo restores the
  stock launcher as the preferred Home app, the three disabled packages and the overlay permission exactly.
- **Screensaver** (MyFrame as the system screensaver) and **open on boot** on the release build. Boot start needs
  `appops write-settings` after the permission grant, or a quick restart loses it.
- **Stability:** five minutes of a 5-second slideshow (about 60 pictures): picture memory flat at 38 to 40 MB, no crash.
- Earlier work: Art Mode, My Photos, Collections, Mixed presets, frame editor, All Apps, focus memory.

## Implemented, not tested

- Standalone Spotify and YouTube Music apps opening/playing (neither is installed on the emulator).
- Settings → Devices → Bluetooth: on the emulator Android refuses the direct pairing screen to third-party apps,
  so the row opens the TV's main Settings instead. May differ on the stick.
- Boot start and screensaver on real hardware; Ken Burns drift; long-run stability (hours).
- Everything on the Xiaomi stick itself, including which packages `appliance.sh list` finds there.

## Device constraints

- No compatible replacement OS or unlock procedure has been verified for the user's exact Xiaomi model. This
  project ships an Android APK, with reversible Home/screensaver configuration; it does not flash firmware.
- Background music depends on the media app, account and device. Spotify should be tested alongside art on real
  hardware; YouTube Music may stop when leaving YouTube. MyFrame cannot force third-party background playback.
- Disabling factory apps does not reclaim their read-only ROM partition. Usable storage is less than the 8 GB label.
- Changing the TV's backlight, switching the TV panel off over HDMI-CEC, or reading room brightness (no sensor).

## Not implemented

- Google Photos import (docs/GOOGLE_PHOTOS_SETUP.md), now-playing overlay for music, weather, extra museums.

## Commands

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
mkdir -p dist && cp app/build/outputs/apk/release/app-release.apk dist/MyFrame.apk   # dist/ is git-ignored
bash tools/tv.sh key right ok back     # drive the emulator remote
bash tools/tv.sh texts                 # visible text + focus
bash tools/appliance.sh list|apply|undo
```

Release signing uses `myframe-release.keystore` and `keystore.properties` in the project root. Both are git-ignored.
Keep a copy of both: an update can only be installed over an existing copy if it is signed with the same key.

Emulator: AVD `MyFrame_TV` (`system-images;android-31;android-tv;x86`); start with
`emulator -avd MyFrame_TV -no-snapshot -gpu swiftshader_indirect -memory 2048`.
