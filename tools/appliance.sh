#!/usr/bin/env bash
# Turns a stock Google TV / Android TV stick into a MyFrame "appliance": MyFrame is Home, launcher clutter is switched off.
# No root, no bootloader unlock, nothing is deleted. Everything is reversible.
#   appliance.sh list     show which packages on THIS stick look like clutter (changes nothing)
#   appliance.sh apply    disable those packages for the current user and make MyFrame the Home app
#   appliance.sh undo     put back exactly what apply changed
# Needs `adb connect <stick-ip>:5555` first (docs/ADB_INSTALL.md).
#
# Safety rules this script follows:
#   * MyFrame's Home entry must be registered and directly launchable BEFORE anything is switched off.
#     Preferred Home is verified after handover; a failed handover automatically runs undo.
#   * The state from before the FIRST apply is recorded once and never overwritten, so apply can be run again safely.
#   * undo re-enables every package first, checks that another Home app is available again, and only then
#     lets go of MyFrame's Home role. If anything fails it stops, keeps MyFrame as Home, and exits non-zero.
ADB="${ADB:-adb}"
[ -x "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ] && ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
# Pin every command and restore record to one device. A second stick must never
# inherit the packages or Home app remembered for the first one.
SERIAL="${ANDROID_SERIAL:-}"
if [ -z "$SERIAL" ]; then
  mapfile -t devices < <("$ADB" devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}')
  [ "${#devices[@]}" = 1 ] || { echo "Connect exactly one device, or set ANDROID_SERIAL to the stick's adb serial."; exit 1; }
  SERIAL="${devices[0]}"
fi
[ "$("$ADB" -s "$SERIAL" get-state 2>/dev/null | tr -d '\r')" = device ] || { echo "Device $SERIAL is not ready or not authorized."; exit 1; }
PKG=com.myframe.tv
ALIAS=$PKG/$PKG.HomeAlias
DEVICE_KEY=$(printf '%s' "$SERIAL" | sha256sum | cut -c1-24)
DIR="$(dirname "$0")/.appliance/$DEVICE_KEY"
BASE="$DIR/baseline.txt"      # key=value lines recorded before the first change
JOURNAL="$DIR/disabled.txt"   # one package per line, appended as each is disabled

# Never touched: Play Store and services, WebView, keyboards, Settings, setup, Bluetooth, TTS, MyFrame, and the music/video apps.
KEEP='^(com\.myframe\.tv|com\.android\.vending|com\.google\.android\.gms|com\.google\.android\.gsf|com\.android\.chrome|com\.google\.android\.webview|com\.android\.webview|com\.google\.android\.tvsettings|com\.android\.settings|com\.android\.tv\.settings|com\.google\.android\.tungsten\.setupwraith|com\.android\.bluetooth|com\.google\.android\.tts|com\.google\.android\.inputmethod.*|com\.google\.android\.youtube.*|com\.google\.android\.apps\.youtube\.music|com\.spotify\..*|com\.android\.providers\..*|com\.android\.systemui|android)$'
# Launcher, recommendations, promos and stock extras. Review `list` before `apply`.
CLUTTER='^(com\.google\.android\.tvlauncher|com\.google\.android\.apps\.tv\.launcherx|com\.google\.android\.tvrecommendations|com\.mitv\.tvhome|com\.mitv\.launcher|com\.xiaomi\.mitv\.launcher)$'

# </dev/null matters: adb shell otherwise reads the caller's input and would swallow the rest of a `while read` list.
sh() { "$ADB" -s "$SERIAL" shell "$@" </dev/null 2>&1 | tr -d '\r'; }

candidates() {
  sh pm list packages -e | sed 's/^package://' | grep -E "$CLUTTER" | grep -Ev "$KEEP" | sort -u
}

# Real launchers enabled right now: MyFrame's own entry and the Settings app's placeholder "FallbackHome" do not count.
other_homes() {
  sh cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME \
    | grep -o 'packageName=[^ ]*' | sed 's/packageName=//' | grep -v -e "^$PKG$" -e 'tv\.settings$' -e '^com\.android\.settings$' | sort -u
}

alias_enabled() {
  sh cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME | grep -q "$PKG" && echo 1 || echo 0
}

overlay_mode() {
  sh appops get "$PKG" SYSTEM_ALERT_WINDOW | sed -n 's/.*SYSTEM_ALERT_WINDOW: *\([a-z_]*\).*/\1/p' | head -1
}

# The Home app Android currently prefers, as package/activity (empty if none is set).
preferred_home() {
  sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1
}

# adb is not allowed to enable another app's Home entry, so MyFrame is asked to switch it itself.
set_home() {
  sh am start -n "$PKG/.MainActivity" --ez myframe.home "$1" >/dev/null
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if [ "$1" = true ] && [ "$(alias_enabled)" = 1 ]; then return 0; fi
    if [ "$1" = false ] && [ "$(alias_enabled)" = 0 ]; then return 0; fi
    sleep 1
  done
  return 1
}

cmd_list() {
  echo "Device: $SERIAL"; echo "Would disable (launcher and recommendations only):"; candidates | sed 's/^/  /'; echo "(nothing changed; disabling system apps does not reclaim their ROM space)"
}

cmd_apply() {
  sh pm path "$PKG" | grep -q package: || { echo "MyFrame is not installed. Run: adb install -r MyFrame.apk"; exit 1; }
  mkdir -p "$DIR" || { echo "Cannot create $DIR"; exit 1; }
  if [ ! -s "$BASE" ]; then
    # First apply: remember how things were, once.
    { echo "alias_enabled=$(alias_enabled)"; echo "overlay=$(overlay_mode)"; echo "other_homes=$(other_homes | tr '\n' ' ')"; echo "preferred_home=$(preferred_home)"; } > "$BASE" \
      || { echo "Cannot write $BASE; nothing was changed."; exit 1; }
    : >> "$JOURNAL"
  fi
  set_home true
  if [ "$(alias_enabled)" != 1 ]; then echo "Android did not accept MyFrame as a Home app. Nothing was switched off."; exit 1; fi
  # Some TV launchers have a higher resolver priority, even after set-home-activity succeeds.
  # Check our entry is launchable first, then verify the handover after disabling known launchers.
  if ! sh am start -W -n "$ALIAS" | grep -q 'Status: ok'; then
    echo "MyFrame's Home entry could not start. Nothing was switched off."; exit 1
  fi
  sh cmd package set-home-activity "$ALIAS" >/dev/null
  for p in $(candidates); do
    # Write the journal line first: if the script is interrupted, undo still knows about this package.
    echo "$p" >> "$JOURNAL" || { echo "Cannot write the journal; stopping."; exit 1; }
    if sh pm disable-user --user 0 "$p" | grep -q "new state: disabled"; then echo "disabled $p"
    else echo "skipped  $p (stick refused)"; sed -i "/^${p//./\\.}\$/d" "$JOURNAL"; fi
  done
  sh appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null
  # Android saves this lazily; without an explicit write, a restart soon afterwards loses it.
  sh appops write-settings >/dev/null
  sh cmd package set-home-activity "$ALIAS" | tail -1
  case "$(preferred_home)" in "$PKG/"*) ;; *) echo "Home handover failed. Restoring the previous setup."; cmd_undo; exit 1;; esac
  echo "Done. Press Home on the remote. Undo any time: appliance.sh undo"
}

cmd_undo() {
  [ -f "$BASE" ] || { echo "Nothing recorded, nothing to undo."; exit 0; }
  local failed=0 p mode prev
  local -a todo=() left=()
  # Read the whole record into memory first, so nothing inside the loop can cut the list short.
  mapfile -t todo < <(grep -v '^[[:space:]]*$' "$JOURNAL" 2>/dev/null | sort -u)
  for p in "${todo[@]}"; do
    if sh pm enable "$p" | grep -q "new state: enabled"; then echo "re-enabled $p"; else echo "FAILED    $p"; failed=1; left+=("$p"); fi
  done
  # The record keeps only what still needs restoring, and is rewritten before anything else is undone.
  : > "$JOURNAL.tmp"; for p in "${left[@]}"; do echo "$p" >> "$JOURNAL.tmp"; done; mv "$JOURNAL.tmp" "$JOURNAL"
  # Another real launcher must be back before MyFrame lets go of the Home role.
  if [ -n "$(other_homes)" ]; then
    if grep -q '^alias_enabled=0' "$BASE"; then set_home false || { echo "MyFrame could not release the Home role."; failed=1; }; fi
  else
    echo "No other Home app is available yet, so MyFrame stays as Home."; failed=1
  fi
  # Hand the Home role back to whichever launcher had it before, unless that was MyFrame itself.
  prev=$(sed -n 's/^preferred_home=//p' "$BASE")
  case "$prev" in ""|"$PKG"/*) ;; *) sh cmd package set-home-activity "$prev" >/dev/null;; esac
  mode=$(sed -n 's/^overlay=//p' "$BASE"); sh appops set "$PKG" SYSTEM_ALERT_WINDOW "${mode:-default}" >/dev/null
  sh appops write-settings >/dev/null
  if [ "$failed" = 0 ]; then rm -f "$JOURNAL" "$BASE"; rmdir "$DIR" 2>/dev/null; echo "Back to how it was."
  else echo "Some steps failed; the record was kept. Run undo again, or: adb shell pm enable <package>"; exit 1; fi
}

case "$1" in
  list) cmd_list;;
  apply) cmd_apply;;
  undo) cmd_undo;;
  *) sed -n 2,6p "$0";;
esac
