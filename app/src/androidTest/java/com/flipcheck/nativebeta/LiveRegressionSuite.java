package com.flipcheck.nativebeta;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Live release gate. It runs the real foreground service, local OCR, production Vision/Web,
 * tournament and reducer from the installed candidate APK. No response is mocked or replayed.
 */
public final class LiveRegressionSuite extends InstrumentationTestCase {
    private static final long ANALYSIS_TIMEOUT_MS = 240_000L;
    private static final String[] MODES = {"original_file", "app_recompressed", "uncached_repeat"};

    public void testFourRealRegressionsThreeTimesThroughInstalledApk() throws Exception {
        Instrumentation instrumentation = getInstrumentation();
        Context target = instrumentation.getTargetContext();
        String apiKey = requiredArgument(InstrumentationRegistry.getArguments(), "live_api_key");
        File output = new File(target.getExternalFilesDir(null), "v133-live");
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create live output directory");
        target.getSharedPreferences("flipcheck_native_beta", 0).edit().putString("api_key", apiKey).putBoolean("ci_mock_mode", false).commit();

        JSONObject report = header(target);
        JSONArray runs = new JSONArray();
        boolean allPassed = true;
        for (Case fixture : cases()) {
            for (String mode : MODES) {
                JSONObject run = new JSONObject().put("case", fixture.key).put("mode", mode).put("startedAt", Instant.now().toString());
                try {
                    RunResult result = runThroughForegroundService(instrumentation, target, fixture, mode);
                    assertExpected(fixture.key, result.id, result.usage);
                    File screenshot = new File(output, fixture.key + "-" + mode + ".png");
                    Bitmap captured = instrumentation.getUiAutomation().takeScreenshot();
                    if (captured == null) throw new AssertionError("UI screenshot unavailable");
                    try (FileOutputStream stream = new FileOutputStream(screenshot)) {
                        if (!captured.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new AssertionError("Screenshot encoding failed");
                    } finally { captured.recycle(); }
                    run.put("status", "PASS").put("title", result.id.title).put("profile", result.id.v2Profile)
                            .put("coreIdentityStatus", result.id.coreIdentityStatus).put("exactIdentityStatus", result.id.exactIdentityStatus)
                            .put("brand", result.id.brand).put("family", result.id.family).put("model", result.id.model)
                            .put("physicalCardNumber", result.id.physicalCardNumber).put("collectorNumber", result.id.physicalCollectorNumber)
                            .put("edition", result.id.edition).put("finish", result.id.finish).put("language", result.id.language)
                            .put("candidateWinnerId", result.id.candidateWinnerId).put("candidateRunnerUpId", result.id.candidateRunnerUpId)
                            .put("disproofStatus", result.id.disproofStatus).put("views", new JSONArray(result.id.photoViews))
                            .put("invariants", result.id.consistencyInvariants).put("requestedPhotoReason", result.id.requestedPhotoReason)
                            .put("visionCalls", result.usage.visionCalls).put("webCalls", result.usage.webCalls)
                            .put("marketCalls", result.id.marketCalls).put("requests", result.usage.requests)
                            .put("costUsd", result.usage.costUsd).put("queries", new JSONArray(result.id.webQueries))
                            .put("candidateTrace", result.id.v2CandidateTrace).put("recoveryTrace", result.id.v2RecoveryTrace)
                            .put("screenshot", screenshot.getName());
                } catch (Throwable failure) {
                    allPassed = false;
                    run.put("status", "FAIL").put("error", failure.getClass().getSimpleName() + ": " + safe(failure.getMessage()));
                }
                run.put("completedAt", Instant.now().toString());
                runs.put(run);
            }
        }
        report.put("runs", runs).put("livePipelineTests", allPassed ? "PASS" : "FAIL")
                .put("fourRealRegressions", allPassed ? "PASS" : "FAIL")
                .put("invariants", allPassed ? "PASS" : "FAIL").put("costReport", "PRESENT")
                .put("apkLaunch", screenshotsPresent(output) ? "PASS" : "FAIL")
                .put("liveRunCount", runs.length()).put("expectedLiveRunCount", 12)
                .put("completedAt", Instant.now().toString());
        writeJson(new File(output, "v133-live-results.json"), report);
        assertTrue("Live installed-APK gate failed; see v133-live-results.json", allPassed && runs.length() == 12 && screenshotsPresent(output));
    }

    private static RunResult runThroughForegroundService(Instrumentation instrumentation, Context target, Case fixture, String mode) throws Exception {
        AnalysisResultStore.reset(target);
        ArrayList<Uri> uris = new ArrayList<>();
        for (String asset : fixture.assets) uris.add(Uri.fromFile(materialize(instrumentation, target, asset, mode)));
        Intent ui = new Intent(target, MainActivity.class).setAction(MainActivity.ACTION_DEBUG_SMOKE_IMPORT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(MainActivity.EXTRA_DEBUG_SMOKE_IMAGES, strings(uris)).putExtra(MainActivity.EXTRA_DEBUG_SMOKE_MOCK, false);
        Activity activity = instrumentation.startActivitySync(ui);
        instrumentation.waitForIdleSync();
        Intent service = new Intent(target, AnalysisForegroundService.class)
                .putParcelableArrayListExtra(AnalysisForegroundService.EXTRA_IMAGES, uris)
                .putExtra(AnalysisForegroundService.EXTRA_DETAILS, "");
        if (Build.VERSION.SDK_INT >= 26) target.startForegroundService(service); else target.startService(service);
        long deadline = SystemClock.elapsedRealtime() + ANALYSIS_TIMEOUT_MS;
        AnalysisResultStore.Snapshot snapshot;
        do {
            SystemClock.sleep(500L);
            snapshot = AnalysisResultStore.load(target);
        } while ((AnalysisResultStore.IDLE.equals(snapshot.state) || AnalysisResultStore.RUNNING.equals(snapshot.state))
                && SystemClock.elapsedRealtime() < deadline);
        instrumentation.waitForIdleSync();
        if (!AnalysisResultStore.COMPLETE.equals(snapshot.state) || snapshot.identification == null)
            throw new AssertionError("Foreground pipeline did not complete: " + snapshot.state + " " + snapshot.error);
        if (activity == null || activity.isFinishing()) throw new AssertionError("Candidate APK Activity is not running");
        return new RunResult(snapshot.identification, snapshot.usage == null ? new Models.Usage() : snapshot.usage);
    }

    private static File materialize(Instrumentation instrumentation, Context target, String asset, String mode) throws Exception {
        // The production pipeline receives a neutral random filename: fixture identity
        // labels remain test metadata and cannot become an identification shortcut.
        File destination = new File(target.getCacheDir(), "live-input-" + System.nanoTime() + ".jpg");
        try (InputStream input = instrumentation.getContext().getAssets().open(asset); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16_384]; int read; while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
        if (!"app_recompressed".equals(mode)) return destination;
        Bitmap bitmap = BitmapFactory.decodeFile(destination.getAbsolutePath());
        if (bitmap == null) throw new AssertionError("Fixture JPEG cannot be decoded: " + asset);
        File recompressed = new File(target.getCacheDir(), "live-processed-" + System.nanoTime() + ".jpg");
        try (FileOutputStream output = new FileOutputStream(recompressed)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 76, output)) throw new AssertionError("Fixture recompression failed");
        } finally { bitmap.recycle(); }
        return recompressed;
    }

    private static void assertExpected(String key, Models.Identification id, Models.Usage usage) {
        require(id != null, "missing identification");
        require("CONFIRMED".equals(id.coreIdentityStatus), key + " core=" + id.coreIdentityStatus + " title=" + id.title);
        require(id.uploadedImageCount > 0 && !id.photoViews.isEmpty() && "STRUCTURED".equals(id.evidenceLedgerStatus), key + " lost structured views");
        require(id.consistencyInvariantErrors.isEmpty() && "PASS".equals(id.consistencyInvariants), key + " invariants=" + id.consistencyInvariantErrors);
        require(!id.candidateWinnerId.isEmpty() && "PASSED".equals(id.disproofStatus), key + " has no grounded winner/disproof");
        require(usage.visionCalls >= 1 && usage.webCalls >= 1 && id.marketCalls == 0, key + " call ladder was skipped or market search leaked");
        require(usage.costUsd <= .0250001d, key + " exceeded USD 0.025: " + usage.costUsd);
        if ("topps".equals(key)) {
            require("Topps".equalsIgnoreCase(id.brand) && contains(id.family, "Chrome") && (contains(id.family, "Update") || contains(id.sourceConfirmedSubSeries, "Update")), "sealed hierarchy wrong: " + id.title);
            require("2025-26".equals(id.physicalReleaseYear) || "2025-26".equals(id.sourceConfirmedReleaseYear), "sealed season wrong");
            require(!contains(id.title, "Upper Deck") && ("FORMAT_PENDING".equals(id.exactIdentityStatus) || "CATALOG_MATCHED".equals(id.exactIdentityStatus)), "sealed result contaminated");
        } else if ("kobe".equals(key)) {
            require("1997-98 SkyBox Metal Universe Kobe Bryant #81".equals(id.title), "sports identity wrong: " + id.title);
            require("81".equals(id.physicalCardNumber) && !contains(id.v2RetrievedFacts, "catalogCardNumber=3") && !contains(id.v2RetrievedFacts, "catalogCardNumber=86"), "sports rows fused");
            require(!"1996-97".equals(id.physicalReleaseYear), "statistics replaced product season");
        } else if ("vileplume".equals(key)) {
            require(contains(id.title, "Pokémon Jungle Vileplume #15/64") && !contains(id.title, "Base Set") && !contains(id.title, "Unlimited"), "TCG identity wrong: " + id.title);
            require("FIRST_EDITION".equals(id.edition) && "HOLO".equals(id.finish) && "English".equalsIgnoreCase(id.language), "TCG physical attributes missing");
        } else if ("philips".equals(key)) {
            require("television_remote_control".equals(id.v2Profile) && "Philips".equalsIgnoreCase(id.brand), "remote family wrong: " + id.title);
            require("TO_VERIFY".equals(id.exactModelStatus) && "rear_label_or_model_code".equals(id.requestedPhotoReason), "remote model request wrong");
            require(!contains(id.title, "Samsung") && !contains(id.title, "LG"), "inferred lookalike leaked into title");
        }
    }

    private static JSONObject header(Context target) throws Exception {
        PackageInfo info = target.getPackageManager().getPackageInfo(target.getPackageName(), 0);
        return new JSONObject().put("suite", "LiveRegressionSuite")
                .put("testMode", "LIVE_API_NO_MOCK_NO_REPLAY").put("packageName", target.getPackageName())
                .put("versionCode", Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode)
                .put("versionName", info.versionName).put("sourceCommit", BuildConfig.SOURCE_COMMIT)
                .put("apkSha256", sha256(new File(target.getApplicationInfo().sourceDir)))
                .put("device", Build.MANUFACTURER + " " + Build.MODEL).put("androidVersion", Build.VERSION.RELEASE)
                .put("androidApi", Build.VERSION.SDK_INT).put("startedAt", Instant.now().toString());
    }

    private static List<Case> cases() {List<Case> cases = new ArrayList<>(); cases.add(new Case("topps", "topps-front.jpg")); cases.add(new Case("kobe", "kobe-front.jpg", "kobe-back.jpg")); cases.add(new Case("vileplume", "vileplume-front.jpg")); cases.add(new Case("philips", "philips-front.jpg")); return cases;}
    private static String[] strings(List<Uri> uris) {String[] values = new String[uris.size()]; for (int i = 0; i < uris.size(); i++) values[i] = uris.get(i).toString(); return values;}
    private static String requiredArgument(Bundle arguments, String key) {String value = arguments == null ? "" : safe(arguments.getString(key)); if (value.isEmpty()) throw new IllegalStateException("Missing instrumentation argument: " + key); return value;}
    private static void writeJson(File file, JSONObject value) throws Exception {try (FileOutputStream output = new FileOutputStream(file)) {output.write(value.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
    private static String sha256(File file) throws Exception {MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (FileInputStream input = new FileInputStream(file)) {byte[] buffer = new byte[32_768]; int read; while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);} StringBuilder out = new StringBuilder(); for (byte b : digest.digest()) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return out.toString();}
    private static boolean screenshotsPresent(File output) {File[] screenshots = output.listFiles((dir, name) -> name.endsWith(".png")); return screenshots != null && screenshots.length >= 12;}
    private static boolean contains(String text, String token) {return safe(text).toLowerCase(java.util.Locale.ROOT).contains(safe(token).toLowerCase(java.util.Locale.ROOT));}
    private static void require(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
    private static String safe(String value) {return value == null ? "" : value.trim();}
    private static final class Case {final String key; final String[] assets; Case(String key, String... assets) {this.key = key; this.assets = assets;}}
    private static final class RunResult {final Models.Identification id; final Models.Usage usage; RunResult(Models.Identification id, Models.Usage usage) {this.id = id; this.usage = usage;}}
}
