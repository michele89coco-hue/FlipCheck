#!/usr/bin/env bash
set -euo pipefail
adb install -t artifact/FlipCheck-v1.34.apk | tee artifact/install-exact-apk.txt
grep -q Success artifact/install-exact-apk.txt
touch artifact/apk-installation.pass
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk | tee artifact/install-test-apk.txt
grep -q Success artifact/install-test-apk.txt
if [[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]]; then
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS
fi
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 | tee artifact/launch.txt
sleep 5
test -n "$(adb shell pidof "$PACKAGE" | tr -d '\r')"
adb exec-out screencap -p > artifact/installed-candidate-launch.png
touch artifact/apk-launch.pass
if [[ -z "$FLIPCHECK_LIVE_API_KEY" ]]; then exit 0; fi
adb shell am force-stop "$PACKAGE"
# adb joins separate arguments into a remote shell command. Quote for that
# second shell too, so whitespace/newlines in a secret cannot split arguments.
instrumentation_command=$(python3 tools/ci/instrumentation_command.py)
set +e
adb shell "$instrumentation_command" | tee artifact/live-instrumentation.txt
instrumentation_status=${PIPESTATUS[0]}
set -e
mkdir -p artifact/live
# Read app-private reports as the debuggable target UID, including failed runs.
adb exec-out run-as "$PACKAGE" tar -C files/v134-live -cf - . > artifact/live-reports.tar
tar -xf artifact/live-reports.tar -C artifact/live
test "$instrumentation_status" -eq 0
grep -q 'OK (1 test)' artifact/live-instrumentation.txt
touch artifact/live-instrumentation.pass
