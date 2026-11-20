#!/usr/bin/env bash
# Boot the `mochy` AVD if needed, wait for it, install the debug APK, launch the app.
#
#   ./apple-sync/dev/run-emulator.sh            # boot + install + launch
#   ./apple-sync/dev/run-emulator.sh --install  # install into an already-booted device
#   AVD=other ./apple-sync/dev/run-emulator.sh
set -euo pipefail

# agent shells often lose DISPLAY and XAUTHORITY; scrcpy needs both
export DISPLAY="${DISPLAY:-:0}"
export XAUTHORITY="${XAUTHORITY:-$(ls /run/user/$(id -u)/xauth_* 2>/dev/null | head -1)}"

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD="${AVD:-mochy}"
PKG="com.mocha.mochymusic.debug"
root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)

"$ADB" start-server >/dev/null 2>&1 || true

running() { "$ADB" devices | grep -qE '^emulator-[0-9]+\s+device'; }

if [ "${1:-}" != "--install" ] && ! running; then
  echo "booting $AVD"
  # a window, because the Apple sign-in happens in an in-app WebView you type into
  # headless, because the emulator's bundled Qt cannot initialise its xcb plugin on this
  # Wayland desktop ("no Qt platform plugin could be initialized"). scrcpy gives the window.
  # software GPU: QEMU on the host NVIDIA driver starved this desktop until KWin logged its
  # main thread hanging. swiftshader is slower, not hungrier.
  nohup "$EMU" -avd "$AVD" -no-window -no-snapshot-save -no-boot-anim -no-audio -cores 4 \
    -gpu "${GPU:-swiftshader_indirect}" >/tmp/mochy-emulator.log 2>&1 &
fi

echo -n "waiting for device"
"$ADB" wait-for-device
for _ in $(seq 1 120); do
  if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
    echo " ok"; break
  fi
  echo -n "."; sleep 2
done

apk=$(ls "$root"/app/build/outputs/apk/*/*/*.apk 2>/dev/null | head -1 || true)
[ -n "$apk" ] || { echo "no debug APK; build first" >&2; exit 1; }
echo "installing $(basename "$apk")"
"$ADB" install -r "$apk"

echo "launching $PKG"
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || \
  "$ADB" shell am start -n "$PKG/com.mocha.mochymusic.MainActivity" >/dev/null

"$ADB" devices -l | sed -n '2p'

if [ "${1:-}" = "--screen" ] || [ "${2:-}" = "--screen" ]; then
  echo "opening scrcpy"
  nohup scrcpy --max-size 1024 --max-fps 30 --window-title "mochy emulator" \
    >/tmp/mochy-scrcpy.log 2>&1 &
  # KWin likes to remember the window off-screen; the screen is 1920 wide here
  sleep 5
  if command -v xdotool >/dev/null; then
    wid=$(xdotool search --name "mochy emulator" 2>/dev/null | head -1)
    [ -n "$wid" ] && xdotool windowmove "$wid" 60 60 && xdotool windowactivate "$wid"
  fi
fi
