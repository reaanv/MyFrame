# Google Photos

**Status: not implemented in this build.** The rest of MyFrame does not depend on it.

## What Google currently allows

Since March 2025 the Google Photos *Library API* can no longer read a user's whole library; apps may only
see items they uploaded themselves. The supported way to get a user's existing photos is the
**Google Photos Picker API**: the app creates a session, the user picks photos in Google's own UI on their
phone (a `pickerUri` shown as a QR code works well on a TV), and the app then reads only those picked items.
There is no supported continuous sync of albums, so MyFrame will never claim one.

Any future integration must verify current Picker authorization, scopes and device support before choosing
an OAuth flow. No Google credentials are needed for this Xiaomi build. Official guide:
https://developers.google.com/photos/picker

## Planned flow (when you want it)

1. Verify supported OAuth clients/flows against current Google documentation; do not assume the TV device-code
   flow supports Photos Picker scopes.
2. Complete sign-in and explicit photo selection on a phone; keep secrets out of the APK and repository.
3. Import selected items through `MediaImporter`, respecting the stick's 1920-pixel optimisation and 1 GB
   storage reserve. This is future work, not a feature or continuous album sync in this release.

## Meanwhile

Phone upload covers the same need without an account: pick photos in Google Photos → Share/Save → upload page.
