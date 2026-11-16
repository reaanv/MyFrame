#!/usr/bin/env bash
# Small helpers for driving the Android TV emulator: tools/tv.sh key <name...> | shot <file> | crash
ADB="${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe"
case "$1" in
  key) shift; for k in "$@"; do
         case "$k" in up) c=19;; down) c=20;; left) c=21;; right) c=22;; ok) c=23;; back) c=4;; home) c=3;; menu) c=82;; play) c=85;; *) c="$k";; esac
         "$ADB" shell input keyevent "$c"; sleep 0.6; done;;
  shot) "$ADB" exec-out screencap -p > "$2";;
  crash) "$ADB" logcat -d -b crash | tail -40;;
  focus) "$ADB" shell dumpsys activity activities | grep -m1 mResumedActivity;;
  # Visible on-screen text (first 14 strings) plus the focused element: a cheap way to assert which screen is showing.
  texts) "$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null | python -I -c "
import sys,re
x=sys.stdin.read()
t=[m for m in re.findall(r'text=\"([^\"]+)\"',x)]
f=re.findall(r'<node[^>]*focused=\"true\"[^>]*>',x)
ft=[re.search(r'text=\"([^\"]*)\"',n).group(1) for n in f]
print('TEXT:', ' | '.join(t[:${2:-14}])); print('FOCUSED nodes:', len(f), ft)";;
esac
