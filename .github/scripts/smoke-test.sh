#!/usr/bin/env bash
# Launches the debug APK on an emulator, walks through the signed-out screens
# in light and dark mode, saves screenshots and fails on any crash.
# No account is created and nothing is written to the backend.
set -euo pipefail

APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.wheredidiputit.app
OUT=smoke-test
mkdir -p "$OUT"

adb install -r "$APK"
adb logcat -c

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "screenshot: $1"
}

# Taps the first on-screen element whose text matches $1.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null
  local bounds
  bounds=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
target, path = sys.argv[1], sys.argv[2]
for node in ET.parse(path).iter("node"):
    if node.get("text") == target:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$bounds" ]; then
    echo "::error::Could not find '$1' on screen"
    exit 1
  fi
  adb shell input tap $bounds
}

run_flow() {
  local mode=$1
  adb shell pm clear "$PKG" > /dev/null
  adb shell cmd uimode night "$mode"
  adb shell am start -W -n "$PKG/com.wheredidiputit.MainActivity" > /dev/null
  shot "01-onboarding-$mode"
  tap_text "Get started"
  shot "02-sign-in-$mode"
  tap_text "Create an account"
  shot "03-sign-up-$mode"
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  tap_text "Forgot password?"
  shot "04-forgot-password-$mode"
}

run_flow no
run_flow yes

adb logcat -d > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt"; then
  echo "::error::The app crashed or froze during the smoke test"
  exit 1
fi
echo "::notice::Smoke test passed: no crash on the signed-out screens (light and dark)."
