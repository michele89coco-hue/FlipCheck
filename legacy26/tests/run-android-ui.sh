#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob
apks=(artifact/FlipCheck-*.apk)
if [[ ${#apks[@]} -ne 1 ]]; then
  echo 'Expected exactly one distribution APK in artifact/.' >&2
  exit 1
fi
trap 'adb logcat -d > artifact/android-logcat.txt' EXIT
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -t -r "${apks[0]}"
adb install -t -r legacy26/build/outputs/apk/androidTest/release/legacy26-release-androidTest.apk
adb shell pm grant com.flipcheck.beta.legacy26fix android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -r -e class com.flipcheck.legacy26.AndroidUiRegressionTest,com.flipcheck.legacy26.GoogleDirectRegressionTest,com.flipcheck.legacy26.BackgroundScanRegressionTest com.flipcheck.beta.legacy26fix.test/androidx.test.runner.AndroidJUnitRunner | tee artifact/android-ui-test.txt
adb pull /sdcard/Android/data/com.flipcheck.beta.legacy26fix/files/ui159 artifact/android-ui
grep -q 'OK (31 tests)' artifact/android-ui-test.txt
