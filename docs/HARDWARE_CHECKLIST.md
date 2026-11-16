# Checklist for the physical Xiaomi stick + Samsung TV

Tick these when the hardware arrives. None were verifiable on the emulator.

## Install and basics
- [ ] `adb install` works; MyFrame appears in the app row with its banner.
- [ ] Remote D-pad/OK/Back work everywhere; no screen traps focus.
- [ ] Text readable from the sofa (UI is designed for 1080p; check overscan: nothing cut off at the edges).
- [ ] Home shows the backdrop smoothly (watch for dropped frames).

## Performance (2 GB RAM)
- [ ] Art slideshow runs 1 hour without a crash or visible slowdown (`adb shell dumpsys meminfo com.myframe.tv`).
- [ ] 12 MP phone photos display with no stutter during transitions; try Ken Burns drift on and off.
- [ ] Blurred-backdrop mode on portraits does not stall the next slide.
- [ ] Importing 100 photos from the phone does not exhaust storage or RAM.

## Phone upload
- [ ] QR/URL works from a phone on the same Wi-Fi; PIN pairing succeeds; 5 wrong PINs lock for a minute.
- [ ] Upload survives a phone leaving Wi-Fi mid-batch (only the in-flight photo is lost).
- [ ] Service stops after 30 idle minutes / when Wi-Fi drops.

## Launching other apps
- [ ] YouTube tile opens the official YouTube app; returning to MyFrame works.
- [ ] All Apps lists and launches Netflix etc.
- [ ] YouTube Music opens its TV launcher, or falls back to YouTube with a Music-menu hint.
- [ ] Spotify: start music, press Home, start Art Mode; listen for 30 minutes without interruption.
- [ ] Test YouTube Music separately: if it stops on Home, record that limitation. MyFrame cannot force another app to play in the background.
- [ ] Record exact Xiaomi model, Android/API version, free `/data` storage and usable RAM. 8 GB advertised storage includes Android.

## Startup (device-specific)
- [ ] Open-on-boot after granting `SYSTEM_ALERT_WINDOW` (see ADB_INSTALL.md).
- [ ] Home-screen option: does the box offer MyFrame when Home is pressed? Can you switch back to the stock launcher?
- [ ] Screensaver appears after the idle timeout and shows the chosen preset.
- [ ] `appliance.sh list` identifies only launchers/recommendations; apply checks direct launch, verifies Home after handover, and rolls back if it fails.
- [ ] Undo on the same device restores Home and all recorded packages. Keep the PC's `tools/.appliance/` record.

## HDMI-CEC / Samsung Anynet+ (optional, not implemented)
- [ ] TV: Settings → System → Anynet+ (HDMI-CEC) on; box: Settings → Device Preferences → HDMI-CEC on.
- [ ] Does the TV wake and switch input when the box wakes? Does it power down with the box?
  MyFrame sends no CEC commands itself; this is Android's own behaviour. A CEC integration would be a separate,
  optional module so it can never delay launch.

## Display
- [ ] Frame/mount proportions look right on 40" 1080p; adjust "Screen dimming" for evenings.
- [ ] Portrait photos are never stretched; blurred backdrop looks natural.
