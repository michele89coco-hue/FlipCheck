# FlipCheck v1.28

Build canonica: `versionCode=132`, `versionName=1.28-exact-catalog-resolution`.

La v1.28 conserva la build Android Gradle funzionante della v1.27 e completa la risoluzione catalografica con token OCR ricostruiti, gerarchia set/subset/parallel/formato, disproof esplicito e blocco dei comparabili finché identità esatta o SKU non sono verificati.

This directory is the complete, self-contained Android Gradle project for
FlipCheck. Production artifacts are built from source; no previous APK,
precompiled DEX, extracted binary manifest, handcrafted Android stub, ZIP
patching, or post-build signing step is used.

## Reproducible debug build

Requirements:

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0
- accepted Android SDK licences

With `JAVA_HOME` and `ANDROID_SDK_ROOT` configured, run:

```sh
./gradlew clean :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The Gradle Wrapper pins Gradle 9.1.0 and verifies the distribution SHA-256.
`gradle/libs.versions.toml` centralizes Android Gradle Plugin and application
dependency versions. D8 performs all DEX generation and multidex placement.

The production identification route normalizes Vision and local OCR evidence
into typed canonical fields before profile selection, identifier verification,
candidate canonicalization, catalog enrichment, market filtering and UI
rendering. A second Vision request is permitted only as recovery from a
truncated, invalid or non-parseable technical response.

## Verification

```sh
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
tools/ci/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk apk-analyzer-report.txt
tools/ci/run-local-emulator-smoke.sh app/build/outputs/apk/debug/app-debug.apk smoke-results
```

The device smoke test installs the exact APK, resolves and launches the
launcher three times, waits 15 seconds after every cold start, exercises
settings persistence, gallery picker, JPEG URI/preview/removal, two-image
loading, foreground service, canonical pipeline in no-cost mock mode, result
receiver/rendering, and camera capture. The test fails if the selected emulator
cannot expose and complete the camera contract.

## Historical tooling

Obsolete APK-patching scripts are isolated under
`tools/historical_apk_patching/` for forensic reference only. No Gradle task or
CI workflow references them.
