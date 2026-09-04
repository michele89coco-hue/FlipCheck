# FlipCheck v1.32

Build canonica: `versionCode=136`, `versionName=1.32-universal-identity-engine-v2`.

La v1.32 instrada tutte le categorie attraverso `UniversalIdentityEngineV2`: registro immutabile delle prove, separazione `OBSERVED`/`INFERRED`/`RETRIEVED`, normalizzazione tipizzata, recupero adattivo, verifica con disproof e un solo riduttore finale gerarchico. I comparabili restano indipendenti dall'identità.

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

The production identification route records Vision and local OCR output as
immutable evidence atoms, routes a domain profile, applies field-aware
normalization, selects the cheapest useful focused recovery, retrieves
identity-only Web candidates, performs disproof, and reduces public state once.
Vision hypotheses cannot masquerade as localized photo observations.

Architecture and validation details:

- `docs/v1.32-universal-identity-engine-v2.md`
- `docs/v1.32-validation-status.md`

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
