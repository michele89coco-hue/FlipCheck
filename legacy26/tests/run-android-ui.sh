#!/usr/bin/env bash
set -euo pipefail
trap 'adb logcat -d > artifact/android-logcat.txt' EXIT
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -t -r artifact/FlipCheck-v0.26.2-GoogleFix-171.apk
adb install -t -r legacy26/build/outputs/apk/androidTest/debug/legacy26-debug-androidTest.apk
adb shell am instrument -w -r -e class com.flipcheck.legacy26.AndroidUiRegressionTest,com.flipcheck.legacy26.GoogleDirectRegressionTest com.flipcheck.beta.legacy26fix.test/androidx.test.runner.AndroidJUnitRunner | tee artifact/android-ui-test.txt
adb pull /sdcard/Android/data/com.flipcheck.beta.legacy26fix/files/ui159 artifact/android-ui
grep -q 'OK (8 tests)' artifact/android-ui-test.txt
