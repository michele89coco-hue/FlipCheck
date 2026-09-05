#!/usr/bin/env bash
set -euo pipefail
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -t -r artifact/FlipCheck-v0.26.2-fix.apk
adb install -t -r legacy26/build/outputs/apk/androidTest/debug/legacy26-debug-androidTest.apk
adb shell am instrument -w -r -e class com.flipcheck.legacy26.AndroidUiRegressionTest com.flipcheck.beta.legacy26fix.test/androidx.test.runner.AndroidJUnitRunner | tee artifact/android-ui-test.txt
adb pull /sdcard/Android/data/com.flipcheck.beta.legacy26fix/files/ui159 artifact/android-ui
grep -q 'OK (1 test)' artifact/android-ui-test.txt
