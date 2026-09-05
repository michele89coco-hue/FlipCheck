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
