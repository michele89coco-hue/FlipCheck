# v0.26.2 — Android bar and photo picker fixes

The Samsung Android16 screenshot from v0.26.1 shows the fixed HTML toolbar under
the three-button system navigation bar. The previous host applied insets as
WebView padding, which does not reliably reduce the viewport of fixed web controls.

This revision wraps WebView in a FrameLayout. The native container reserves system
bars, cutout and keyboard space; handled insets are zeroed before reaching WebView,
including keyboard dismissal updates. The system navigation background is dark.

Empty photo slots now open the same multiple file input as the add button. Filled
slots retain single-photo replacement. Android13+ uses the system photo picker
with EXTRA_PICK_IMAGES_MAX=3 and Android15+ preserves selection order. The
document-picker fallback retains EXTRA_ALLOW_MULTIPLE.

References used:
- https://developer.android.com/develop/ui/views/layout/webapps/understand-window-insets
- https://developer.android.com/reference/android/provider/MediaStore#ACTION_PICK_IMAGES

VersionCode159, versionName0.26.2-android-ui, same applicationId/signing key as
v0.26.1. The recognition engine, edition rules, image resizing and API request
construction remain unchanged. No paid API tests.

Validation includes original policy/browser checks and a real Android16 emulator
with three-button navigation. Instrumentation touches both tabs, shows/hides the
keyboard, checks viewport bounds, sends three real MediaStore image URIs through
an instrumented picker result, checks cancellation/replacement, and separately
opens/screenshots the actual system photo picker. The deterministic URI result
test does not simulate a person manually selecting items in the system gallery.

Verified build: commit fbb4f1ead7cc65afe01f9aae7446c13161a32f10,
GitHub Actions run 33999976633. All 16 policy tests, 8 Chromium integration tests
and the Android16 instrumentation scenario passed. The latter verified native
tab touches, IME appearance/dismissal, restored viewport height, three decoded
MediaStore images, cancellation and single-photo replacement intent mode.
Android logcat independently records PhotoPickerActivity displayed successfully.
The photo screenshots were captured before compositor updates completed and are
not evidence of the final three-thumbnail state or the system gallery appearance;
those checks rely on DOM assertions, native intents and Android activity logs.
No live recognition/API calls and no test on the user's physical Samsung device.

APK SHA-256: a64d206c2461f237f12e20908b4837e8cf8180b2e3c42f10126fdf34070094ea.
Signer SHA-256 matches v0.26.1:
d4d02478ea31cb6bd83228047e9accd7dfb191ed991a4c8e8c06a228e54614c7.
