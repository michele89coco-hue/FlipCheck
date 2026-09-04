#!/usr/bin/env bash
set -euo pipefail

apk=${1:?APK path required}
out=${2:?output directory required}
baseline=${3:-}
package=com.flipcheck.beta.nativev098.clean.debug
activity=com.flipcheck.nativebeta.MainActivity
mkdir -p "$out"

fail() { echo "FAIL: $*" | tee -a "$out/smoke-test-results.txt" >&2; exit 1; }
pass() { echo "PASS: $*" | tee -a "$out/smoke-test-results.txt"; }
crash_pattern='FATAL EXCEPTION|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|VerifyError|IncompatibleClassChangeError|Resources\\\$NotFoundException|ExceptionInInitializerError'

check_crashes() {
  local log=$1
  adb logcat -d > "$log"
  if rg -n "$crash_pattern" "$log"; then
    fail "AndroidRuntime crash detected in $log"
  fi
}

dump_ui() {
  local name=$1
  local remote=/sdcard/flipcheck-window.xml local_file="$out/$name.xml"
  adb shell rm -f "$remote" >/dev/null 2>&1 || true
  find "$out" -maxdepth 1 -type f -name "$name.xml" -delete
  for _ in 1 2 3; do
    adb shell uiautomator dump "$remote" >/dev/null 2>&1 || true
    if adb shell test -s "$remote" && adb pull "$remote" "$local_file" >/dev/null 2>&1 \
        && [[ -s "$local_file" ]]; then
      return
    fi
    sleep 2
  done
  fail "unable to obtain a fresh UIAutomator hierarchy: $name"
}

tap_node() {
  local regex=$1 name=$2 coords
  dump_ui "$name"
  coords=$(python3 tools/ci/tap-node.py "$out/$name.xml" "$regex") \
    || fail "UI node not found: $regex"
  adb shell input tap $coords
}

dismiss_non_app_anr() {
  local attempt=$1 xml="$out/system-dialog-$1.xml" coords
  dump_ui "system-dialog-$attempt"
  if ! rg -qi "isn't responding|is not responding" "$xml"; then
    return
  fi
  if rg -qi 'FlipCheck[^<]*(isn.t|is not) responding' "$xml"; then
    fail "FlipCheck ANR detected"
  fi
  # TCG-only emulators without KVM can briefly starve System UI or Play
  # Services during first boot. Record that infrastructure dialog and choose
  # Wait; never dismiss an ANR belonging to FlipCheck itself.
  coords=$(python3 tools/ci/tap-node.py "$xml" 'android:id/aerr_wait|^wait$') \
    || fail "non-app ANR dialog could not be dismissed"
  adb shell input tap $coords
  sleep 5
  pass "dismissed non-app emulator ANR after cold start $attempt"
}

: > "$out/smoke-test-results.txt"
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell svc power stayon true

adb uninstall "$package" >/dev/null 2>&1 || true
adb install -t "$apk" > "$out/install-clean.txt"
rg -q 'Success' "$out/install-clean.txt" || fail "clean install"
pass "clean install"

adb shell cmd package resolve-activity --brief "$package" > "$out/resolve-activity.txt"
rg -q "$package/$activity" "$out/resolve-activity.txt" || fail "launcher resolution"
pass "launcher resolves to $activity"

for attempt in 1 2 3; do
  adb logcat -c
  adb shell am force-stop "$package"
  adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 \
    > "$out/launch-$attempt-monkey.txt"
  sleep 15
  pid=$(adb shell pidof "$package" | tr -d '\r')
  [[ -n "$pid" ]] || fail "process not alive after launch $attempt"
  check_crashes "$out/launch-$attempt-logcat.txt"
  pass "cold start $attempt process_alive=$pid logcat_clean"
  dismiss_non_app_anr "$attempt"
done
adb exec-out screencap -p > "$out/launch-screenshot.png"

# Save a non-secret fixture key through the real UI.
tap_node '^flipcheck-api-key$' key-field
sleep 3
adb shell input text 'ci-smoke-key-not-sent'
sleep 2
# A hardware-keyboard emulator often keeps the soft IME hidden. Pressing Back
# unconditionally would then leave the Activity instead of hiding a keyboard.
if adb shell dumpsys input_method | rg -q 'm(InputShown|IsInputViewShown)=true'; then
  adb shell input keyevent KEYCODE_BACK
  sleep 2
fi
adb shell dumpsys window | rg 'mCurrentFocus|mFocusedApp' > "$out/settings-focus.txt"
rg -q "$package" "$out/settings-focus.txt" || fail "settings entry left FlipCheck"
tap_node '^flipcheck-save-key$' save-key
adb shell run-as "$package" sh -c 'cat shared_prefs/flipcheck_native_beta.xml' \
  > "$out/shared-preferences-after-save.xml"
rg -q 'ci-smoke-key-not-sent' "$out/shared-preferences-after-save.xml" \
  || fail "settings key persistence"
pass "settings opened and non-secret key persisted"

# Open the actual system gallery/document picker from the app.
adb shell input swipe 240 720 240 160 500
tap_node '^flipcheck-gallery$' gallery-button
sleep 3
adb shell dumpsys window | rg 'mCurrentFocus|mFocusedApp' > "$out/gallery-focus.txt"
rg -qi 'documentsui|photopicker|resolveractivity' "$out/gallery-focus.txt" \
  || fail "gallery/document picker did not open"
pass "gallery/document picker opened"
adb shell input keyevent KEYCODE_BACK

# Install JPEGs into app-private cache, then enter through the debug URI bridge.
adb push tools/ci/fixtures/smoke-photo.jpg /data/local/tmp/flipcheck-smoke.jpg >/dev/null
adb shell run-as "$package" cp /data/local/tmp/flipcheck-smoke.jpg cache/smoke-one.jpg
adb shell run-as "$package" cp /data/local/tmp/flipcheck-smoke.jpg cache/smoke-two.jpg
uri1="file:///data/user/0/$package/cache/smoke-one.jpg"
uri2="file:///data/user/0/$package/cache/smoke-two.jpg"

adb shell am force-stop "$package"
adb shell am start -W -n "$package/$activity" \
  -a com.flipcheck.nativebeta.DEBUG_SMOKE_IMPORT \
  --esa smoke_image_uris "$uri1" --ez smoke_mock_mode true \
  > "$out/import-one.txt"
sleep 3
adb shell input swipe 240 720 240 150 600
dump_ui preview-one
rg -q 'content-desc="flipcheck-photo-1"' "$out/preview-one.xml" \
  || fail "single JPEG preview"
pass "single JPEG URI rendered as preview"

tap_node '^flipcheck-remove-photo-1$' remove-one
sleep 2
dump_ui preview-removed
if rg -q 'content-desc="flipcheck-photo-1"' "$out/preview-removed.xml"; then
  fail "remove image"
fi
pass "image removed without crash"

adb shell am force-stop "$package"
adb shell am start -W -n "$package/$activity" \
  -a com.flipcheck.nativebeta.DEBUG_SMOKE_IMPORT \
  --esa smoke_image_uris "$uri1,$uri2" --ez smoke_mock_mode true \
  > "$out/import-two.txt"
sleep 3
adb shell input swipe 240 720 240 150 600
dump_ui preview-two
rg -q 'content-desc="flipcheck-photo-1"' "$out/preview-two.xml" || fail "first preview"
rg -q 'content-desc="flipcheck-photo-2"' "$out/preview-two.xml" || fail "second preview"
pass "two JPEG URIs rendered"

adb logcat -c
adb shell input swipe 240 720 240 120 600
tap_node '^flipcheck-identify$' identify-button
for _ in $(seq 1 30); do
  sleep 2
  dump_ui identification-result
  if rg -q 'Fixture|Identificazione' "$out/identification-result.xml"; then break; fi
done
rg -q 'Fixture|Identificazione' "$out/identification-result.xml" \
  || fail "mock identification result did not render"
adb logcat -d > "$out/mock-identification-logcat.txt"
rg -q 'foreground_service_started' "$out/mock-identification-logcat.txt" \
  || fail "foreground service did not start"
rg -q 'mock_pipeline_complete' "$out/mock-identification-logcat.txt" \
  || fail "mock pipeline did not complete"
check_crashes "$out/smoke-logcat.txt"
adb exec-out screencap -p > "$out/smoke-result-screenshot.png"
pass "Identify traversed service, URI encoder, canonical pipeline, store, receiver and UI"

# Camera contract: launch the resolved camera Activity from FlipCheck. Capture is
# attempted when the emulator camera exposes a recognizable shutter control.
adb shell input swipe 240 150 240 720 700
tap_node '^flipcheck-camera$' camera-button
sleep 4
adb shell dumpsys window | rg 'mCurrentFocus|mFocusedApp' > "$out/camera-focus.txt"
if rg -q "$package" "$out/camera-focus.txt"; then fail "camera activity did not open"; fi
pass "camera activity opened through FileProvider URI"
dump_ui camera-ui
if coords=$(python3 tools/ci/tap-node.py "$out/camera-ui.xml" 'while using|allow only|consenti|allow'); then
  adb shell input tap $coords
  sleep 3
  dump_ui camera-ui-ready
else
  cp "$out/camera-ui.xml" "$out/camera-ui-ready.xml"
fi
coords=$(python3 tools/ci/tap-node.py "$out/camera-ui-ready.xml" 'shutter|take photo|capture|scatta') \
  || fail "emulator camera shutter not available"
adb shell input tap $coords
sleep 5
dump_ui camera-after-shutter
if coords=$(python3 tools/ci/tap-node.py "$out/camera-after-shutter.xml" 'done|ok|use photo|salva|conferma'); then
  adb shell input tap $coords
fi
sleep 5
adb shell dumpsys window | rg 'mCurrentFocus|mFocusedApp' > "$out/camera-return-focus.txt"
rg -q "$package" "$out/camera-return-focus.txt" || fail "camera did not return to FlipCheck"
dump_ui camera-return
rg -q 'content-desc="flipcheck-photo-3"' "$out/camera-return.xml" \
  || fail "captured photo preview did not render"
pass "camera capture returned and rendered preview"
check_crashes "$out/camera-logcat.txt"

if [[ -n "$baseline" && -f "$baseline" ]]; then
  adb uninstall "$package" >/dev/null 2>&1 || true
  adb install -t "$baseline" > "$out/install-baseline.txt"
  rg -q 'Success' "$out/install-baseline.txt" || fail "baseline install"
  printf '%s\n' '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>' \
    '<map><string name="api_key">upgrade-preserved-key</string><string name="pending_camera_uri_v095">content://stale.invalid/image</string><long name="pending_camera_started_v095" value="1" /></map>' \
    > "$out/update-preferences.xml"
  adb push "$out/update-preferences.xml" /data/local/tmp/update-preferences.xml >/dev/null
  adb shell run-as "$package" mkdir -p shared_prefs
  adb shell run-as "$package" cp /data/local/tmp/update-preferences.xml shared_prefs/flipcheck_native_beta.xml
  adb install -r -t "$apk" > "$out/install-update.txt"
  rg -q 'Success' "$out/install-update.txt" || fail "APK update install"
  adb logcat -c
  adb shell am force-stop "$package"
  adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 15
  [[ -n "$(adb shell pidof "$package" | tr -d '\r')" ]] || fail "updated process not alive"
  check_crashes "$out/update-logcat.txt"
  adb shell run-as "$package" sh -c 'cat shared_prefs/flipcheck_native_beta.xml' \
    > "$out/shared-preferences-after-update.xml"
  rg -q 'upgrade-preserved-key' "$out/shared-preferences-after-update.xml" \
    || fail "SharedPreferences not preserved across update"
  pass "baseline to current Gradle build update, startup and preferences"
fi

adb shell getprop ro.build.version.release > "$out/android-version.txt"
adb shell getprop ro.build.version.sdk > "$out/android-api.txt"
adb shell getprop ro.product.cpu.abi > "$out/android-abi.txt"
adb shell getprop ro.product.model > "$out/android-model.txt"
pass "all requested device smoke stages completed"
