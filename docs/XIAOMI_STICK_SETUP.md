# Setting up a Xiaomi Android TV / Google TV stick

Target: 2 GB RAM, 8 GB advertised storage. The exact Xiaomi model/generation and firmware are not known yet.
Use the Android version of MyFrame; this APK is not firmware. Keep Android installed. No verified replacement
OS or bootloader-unlock path is supplied. No root or flashing is required.

## 1. First start (on the stick)

1. Finish Google TV's own setup and connect to Wi-Fi.
2. Keep/install **YouTube** and install **Spotify** from the TV's Play Store. Install the **YouTube Music** TV
   launcher if offered. It opens the Music section of YouTube; MyFrame also falls back to YouTube without it.
   Stream rather than download music: downloads are what fill an 8 GB stick.
3. In About, press **Android TV OS build** seven times. Enable debugging in Developer options. Menu names and
   network-debugging support vary by firmware. Use Wireless debugging's pairing/address if offered; do not
   assume every model exposes port 5555. Otherwise use the debugging connection supported by that model.
4. Note the stick's IP address: Settings → Network & Internet → your Wi-Fi.

## 2. Install MyFrame (on the PC)

```bash
adb connect <stick-ip>:5555          # accept the prompt on the TV
adb install -r MyFrame.apk
```

The `:5555` example applies only when the stick offers legacy network debugging. For Wireless debugging use
`adb pair <pairing-ip:port>`, then `adb connect <connection-ip:port>` with the two addresses shown on the TV.

Windows shortcut, after connecting: `powershell -File tools/install-stick.ps1 -Device <adb-serial> -Apk MyFrame.apk`.
`MyFrame.apk` is the signed, minified release from the [Releases page](https://github.com/reaanv/MyFrame/releases/latest). An update preserves photos/settings if its signing key matches.
If Android reports a signature mismatch, export the existing photo library before choosing to uninstall:
uninstalling erases MyFrame's local photos and settings. The installer never uninstalls automatically.

## 3. Make MyFrame the whole experience

```bash
cd MyFrame            # your clone of this repository
bash tools/appliance.sh list     # shows what would be switched off on THIS stick; changes nothing
bash tools/appliance.sh apply    # MyFrame becomes Home; only known launcher/recommendation packages are disabled
```

Run these in Git Bash. With multiple devices, prefix every command with `ANDROID_SERIAL=<stick-adb-serial>`.
Read `list` before `apply`. Only known launchers/recommendations are candidates; personal streaming apps are
left enabled. Unknown vendor launchers are left alone. Keep `tools/.appliance/` on this PC: it contains a separate
undo record per device. Do not run the script from another computer and expect it to know the old state.

In MyFrame: Settings → Startup → **When MyFrame opens, start in → Art Mode** (or My Photos).
Choose **Open MyFrame when the TV box starts** if needed; appliance mode grants/persists its boot permission.
Test a reboot and Home button on the real stick before leaving it with the family.

`apply` can be run twice. It checks that MyFrame's Home entry can launch, then disables the recognised stock
launchers and verifies that Home resolves to MyFrame. A failed handover automatically runs undo. Some TV
launchers have higher priority until disabled, so selecting a preferred app alone may not work.
It never removes Android or deletes an application.

To go back to stock Google TV at any time: `bash tools/appliance.sh undo`.

## 4. Check it on the real stick

```bash
adb shell df /data                                  # free storage
adb shell dumpsys meminfo com.myframe.tv | grep "TOTAL PSS"   # MyFrame's memory, in kB
adb shell cat /proc/meminfo | grep MemAvailable     # memory left for Spotify
```

Start Spotify from MyFrame's home row, start a playlist, press Home (back to MyFrame) and start Art Mode.
The music should carry on. If it stops after a while, the stick is short of memory: say so and send the three
numbers above. Also check the music app's own playback/account restrictions; low memory is only one possible cause.

YouTube Music on TV runs through YouTube. Test it separately: returning to MyFrame may stop playback, and MyFrame
cannot override that. For music while art stays visible, verify Spotify on this device first. A second audio
device is needed if YouTube Music stops when you leave YouTube and simultaneous art is essential.

## Space and speed

- Use the release APK; it bundles 22 paintings and works offline after photo import.
- New photos are optimised to a 1920-pixel long edge by default. Existing phone originals are untouched.
- Full-picture cache is capped at 24 MB and thumbnails at 8 MB; display decodes have a Full HD pixel budget.
- Imports stop before using the last 1 GB of storage. The 8 GB label includes Android, so usable space is less.
- Stream music; avoid offline downloads on the stick. Clear thumbnails in Settings → Media when needed.
- Disabling factory apps hides/stops them; it does not free their read-only system partition. No debloating
  command here promises extra ROM. Uninstall only optional user apps you recognise using Android Settings.

## Everyday use (for the family)

- **Home** on the remote always comes back to MyFrame.
- **Art Mode** shows paintings. **Photos** shows your own pictures. **Mixed** shows both.
- **Add photos:** Photos → Add photos, scan the code with a phone, type the 6-digit number, choose pictures.
- **Music:** Spotify or YT Music on the home row, then Home to return to the pictures.
- **YouTube Music:** choose Music in YouTube's left menu if the separate TV launcher is missing; playback may stop on Home.
- New paintings arrive by themselves over Wi-Fi, a few each day. Settings → Media has a switch for this.

Google documents the TV Music launcher and fallback in [Use YouTube Music on other apps & devices](https://support.google.com/youtubemusic/answer/9231765?hl=en).
