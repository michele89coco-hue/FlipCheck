#!/usr/bin/env bash
set -euo pipefail
adb install -t artifact/FlipCheck-v1.34.apk | tee artifact/install-exact-apk.txt
grep -q Success artifact/install-exact-apk.txt
touch artifact/apk-installation.pass
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk | tee artifact/install-test-apk.txt
grep -q Success artifact/install-test-apk.txt
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 | tee artifact/launch.txt
sleep 5
test -n "$(adb shell pidof "$PACKAGE" | tr -d '\r')"
adb exec-out screencap -p > artifact/installed-candidate-launch.png
touch artifact/apk-launch.pass
if [[ -z "$FLIPCHECK_LIVE_API_KEY" ]]; then exit 0; fi
adb shell am force-stop "$PACKAGE"
set +e
adb shell am instrument -w -r -e live_api_key "$FLIPCHECK_LIVE_API_KEY" -e class com.flipcheck.nativebeta.LiveRegressionSuite "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner" | tee artifact/live-instrumentation.txt
instrumentation_status=${PIPESTATUS[0]}
set -e
adb pull "/sdcard/Android/data/$PACKAGE/files/v134-live" artifact/live || true
test "$instrumentation_status" -eq 0
grep -q 'OK (1 test)' artifact/live-instrumentation.txt
touch artifact/live-instrumentation.pass
