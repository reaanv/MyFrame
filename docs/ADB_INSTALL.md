# Installing on a Xiaomi Android TV stick with adb

Nothing here is destructive or needs root.

1. On the box: Settings → Device Preferences → About → tap **Build** 7 times (Developer options appear).
2. Developer options → enable **USB debugging** (older builds) / **Network debugging** if offered.
3. Find the box IP: Settings → Network & Internet → your Wi-Fi/Ethernet.
4. On the PC (platform-tools is in `%LOCALAPPDATA%\Android\Sdk\platform-tools`):

```bash
adb connect 192.168.1.50:5555      # accept the "Allow debugging" prompt on the TV
adb devices                        # should list the box as "device"
adb install -r MyFrame.apk
adb shell monkey -p com.myframe.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```

MyFrame also appears in the TV's app row.

## Optional startup features

All are off by default and reversible.

- **Open on boot**: Android 10+ blocks background activity starts. After turning the switch on in
  Settings → Startup, grant the one permission it needs:
  `adb shell appops set com.myframe.tv SYSTEM_ALERT_WINDOW allow`
  then `adb shell appops write-settings` (without it, restarting the box soon afterwards loses the permission;
  seen on the emulator). `tools/appliance.sh apply` does both for you.
  Undo: `adb shell appops set com.myframe.tv SYSTEM_ALERT_WINDOW default`
- **Home screen**: Settings → Startup → "Offer MyFrame as the Home screen". Press the remote's Home button;
  if Android asks, choose MyFrame. The stock launcher is never removed. To restore it:
  turn the switch off, or `adb shell pm clear com.myframe.tv`, or pick the stock launcher in
  Settings → Apps → Default apps → Home app. Android TV 12+ stock launchers may ignore third-party Home apps.
- **Screensaver**: Settings → Startup → Screensaver opens the TV's screensaver chooser. If the box hides it:
  `adb shell settings put secure screensaver_components com.myframe.tv/.system.FrameDreamService`
  Undo: `adb shell settings delete secure screensaver_components`

## Copying photos without the phone page

```bash
adb push photo.jpg /sdcard/Android/data/com.myframe.tv/files/inbox/
```
then Settings → Media → "Import from the inbox folder" (also scanned at every launch).

## Uninstall

`adb uninstall com.myframe.tv`

## Appliance mode (MyFrame as "the OS")

No replacement firmware or bootloader unlock has been verified for your exact stick. Keep Android so the TV
drivers, remote, updates and installed media apps continue working. Appliance mode makes MyFrame the preferred
Home app, disables only recognised launcher/recommendation packages, verifies the resulting Home selection
and runs undo if handover fails. Nothing is deleted; factory system
apps still occupy their ROM space. Use the same computer and keep `tools/.appliance/` for undo.

```bash
bash tools/appliance.sh list     # review what it would switch off on your stick (changes nothing)
bash tools/appliance.sh apply    # switch it off and make MyFrame the Home app
bash tools/appliance.sh undo     # put everything back
```

Spotify, YouTube, YouTube Music, the Play Store and Settings are never touched. Install Spotify and the TV's
YouTube/YouTube Music launcher from the Play Store. MyFrame opens YouTube's Music menu if the separate launcher
is absent. Playback after returning to art depends on the media app and device; test it before relying on it.

With multiple devices connected, select one explicitly in Git Bash:
`ANDROID_SERIAL=192.168.1.50:5555 bash tools/appliance.sh list` (then `apply` or `undo`).
The script refuses an ambiguous target and keeps a separate restore record for each device.
Bluetooth pairing: Settings → Devices → Bluetooth.
