# Troubleshooting

MyFrame has been tested on the Android TV emulator, not yet on a physical Xiaomi stick. Fixes marked
*(expected)* follow from how Android works but have not been confirmed on real hardware.

## Installing

**`adb connect` fails or times out.**
Check the stick and the computer are on the same Wi-Fi and that debugging is enabled. Not every stick opens
port 5555. If the TV offers **Wireless debugging**, use `adb pair <ip:port>` with the pairing code, then
`adb connect <ip:port>` with the second address shown on the TV.

**`adb devices` shows `unauthorized`.**
Accept the "Allow debugging" prompt on the TV. If no prompt appears, turn debugging off and on again.

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE` or a signature mismatch.**
The installed copy was signed with a different key, for example a debug build or your own build.
Android will not update across keys. Uninstalling erases MyFrame's photos and settings, so copy the photos
off first if you need them, then run `adb uninstall com.myframe.tv` and install again.

**`INSTALL_FAILED_INSUFFICIENT_STORAGE`.**
Free space in the TV's Settings → Storage. An 8 GB stick has much less than 8 GB usable.

**MyFrame is not in the app row.**
Start it once with `adb shell monkey -p com.myframe.tv -c android.intent.category.LEANBACK_LAUNCHER 1`.
Some launchers only list an app after its first run.

## Phone upload

**The phone cannot open the address.**
The phone must be on the same Wi-Fi as the stick. Guest networks and "client isolation" block devices from
seeing each other. Mobile data and VPNs on the phone also prevent it. The page is served by the stick at
`http://<stick-ip>:8080`; no public website can reach it.

**The page says the PIN is wrong.**
A new PIN is created each time Phone upload is turned on. Use the one on the TV now. Five wrong tries lock
pairing for 60 seconds.

**Upload stops with a storage message.**
Imports stop before the last 1 GB of storage is used. Delete photos, or clear thumbnails in Settings → Media.

**A file is rejected.**
Only JPEG, PNG and WebP are accepted, up to 40 MB, and the file is checked by decoding it. HEIC photos from
an iPhone need converting first; the phone's "Most Compatible" camera setting produces JPEG.

**The page stopped working after a while.**
Phone upload switches itself off after 30 idle minutes or when Wi-Fi drops. Turn it on again on the TV.

## Display

**Pictures look soft on a 4K TV.**
This is deliberate. Pictures are decoded with a Full HD pixel budget to protect memory on a 2 GB stick.

**The screen goes black at night.**
That is the night black-out. Any remote key wakes it. Change it in Settings.

**No new paintings arrive.**
Check Settings → Media → new paintings over Wi-Fi, and that the stick has internet. Downloads are a few per
day and stop at 300.

## Music

**Spotify or YT Music tile says the app is not installed.**
Install the app from the TV's Play Store. MyFrame only opens apps that are already installed.

**YouTube Music stops when I return to MyFrame.**
On TV, YouTube Music runs inside YouTube, which may stop playing when it leaves the screen. MyFrame cannot
override another app's playback rules. Try Spotify for music while art is shown.

**Music stops after some minutes.** *(expected)*
The stick may be short of memory and Android has closed the music app. Check with:

```bash
adb shell dumpsys meminfo com.myframe.tv | grep "TOTAL PSS"
adb shell cat /proc/meminfo | grep MemAvailable
```

## Home screen, boot and undo

**Pressing Home still opens the stock launcher.**
Some Android TV launchers keep priority over third-party Home apps until they are disabled.
`bash tools/appliance.sh apply` handles this and rolls back by itself if the handover fails.

**MyFrame does not open after a restart.**
Boot start needs an app-op that a quick restart can lose:

```bash
adb shell appops set com.myframe.tv SYSTEM_ALERT_WINDOW allow
adb shell appops write-settings
```

**I want the stock Google TV back.**
Run `bash tools/appliance.sh undo` from the same computer that ran `apply`; the undo record lives in
`tools/.appliance/` on that computer. Without the record: `adb shell pm clear com.myframe.tv` removes MyFrame
as Home, and `adb shell pm enable <package>` re-enables a launcher you know was disabled.

**`appliance.sh` says the target is ambiguous.**
More than one adb device is connected. Prefix the command with `ANDROID_SERIAL=<serial>`.

## Building

**`SDK location not found`.**
Create `local.properties` with `sdk.dir=<path to the Android SDK>`, or set `ANDROID_HOME`.

**The release APK is unsigned.**
That is the behaviour without `keystore.properties`. See "Build from source" in the README.

## Reporting a problem

Open an issue at <https://github.com/reaanv/MyFrame/issues> with the stick's model, its Android version, and
the output of `adb logcat -d -b crash | tail -40`. Check the log for personal details before posting.
