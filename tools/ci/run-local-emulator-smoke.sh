#!/usr/bin/env bash
set -euo pipefail

apk=${1:?APK path required}
out=${2:?output directory required}
baseline=${3:-}
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
avd_home=${ANDROID_AVD_HOME:-$HOME/.android/avd}
avd_name=${AVD_NAME:-FlipCheck_API_36_Run}
host_libs=${EMULATOR_HOST_LIBS:-}
mkdir -p "$out"

export ANDROID_HOME="$sdk" ANDROID_SDK_ROOT="$sdk" ANDROID_AVD_HOME="$avd_home"
if [[ -n "$host_libs" ]]; then export LD_LIBRARY_PATH="$host_libs:${LD_LIBRARY_PATH:-}"; fi
find "$avd_home/$avd_name.avd" -maxdepth 1 -type f -name '*.lock' -delete 2>/dev/null || true
find "$avd_home/$avd_name.avd" -maxdepth 1 -type f -name 'multiinstance.lock' -delete 2>/dev/null || true

"$sdk/platform-tools/adb" start-server >/dev/null
emulator_args=(-avd "$avd_name" -no-window -no-audio -no-boot-anim
  -accel off -gpu swiftshader_indirect -no-snapshot)
if [[ ${EMULATOR_WIPE_DATA:-1} == 1 ]]; then emulator_args+=(-wipe-data); fi
"$sdk/emulator/emulator" "${emulator_args[@]}" > "$out/emulator.txt" 2>&1 &
emulator_pid=$!
cleanup() {
  "$sdk/platform-tools/adb" emu kill >/dev/null 2>&1 || true
  wait "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT

for attempt in $(seq 1 240); do
  state=$("$sdk/platform-tools/adb" get-state 2>/dev/null || true)
  boot=$("$sdk/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [[ "$state" == device && "$boot" == 1 ]]; then break; fi
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    echo "Emulator terminated before boot" >&2
    exit 1
  fi
  sleep 5
done
[[ $("$sdk/platform-tools/adb" shell getprop sys.boot_completed | tr -d '\r') == 1 ]] \
  || { echo "Emulator did not boot within 20 minutes" >&2; exit 1; }

PATH="$sdk/platform-tools:$PATH" tools/ci/run-device-smoke.sh "$apk" "$out" "$baseline"
