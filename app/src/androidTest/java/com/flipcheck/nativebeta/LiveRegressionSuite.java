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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Rect;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
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
public final class LiveRegressionSuite {
    private static final long ANALYSIS_TIMEOUT_MS = 240_000L;
    private static final String[] MODES = {"original_file", "app_recompressed", "uncached_repeat"};

    @Test public void testFourRealRegressionsThreeTimesThroughInstalledApk() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context target = instrumentation.getTargetContext();
        String apiKey = requiredArgument(InstrumentationRegistry.getArguments(), "live_api_key");
        File output = new File(target.getFilesDir(), "v134-live");
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create live output directory");
        target.getSharedPreferences("flipcheck_native_beta", 0).edit().putString("api_key", apiKey).putBoolean("ci_mock_mode", false).commit();

        JSONObject report = header(target);
        JSONArray runs = new JSONArray();
        boolean allPassed = true;
        liveCases: for (Case fixture : cases()) {
            for (String mode : MODES) {
                JSONObject run = new JSONObject().put("case", fixture.key).put("mode", mode).put("startedAt", Instant.now().toString());
                try {
                    RunResult result = runThroughForegroundService(instrumentation, target, fixture, mode, run);
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
                            .put("editionStatus", result.id.exactEditionStatus).put("finishStatus", result.id.finishStatus)
                            .put("variantStatus", result.id.variantStatus).put("format", result.id.sealedFormat)
                            .put("formatStatus", result.id.commercialFormatStatus).put("exactModelStatus", result.id.exactModelStatus)
                            .put("physicalReleaseYear", result.id.physicalReleaseYear).put("catalogReleaseYear", result.id.sourceConfirmedReleaseYear)
                            .put("candidateWinnerId", result.id.candidateWinnerId).put("candidateRunnerUpId", result.id.candidateRunnerUpId)
                            .put("disproofStatus", result.id.disproofStatus).put("views", new JSONArray(result.id.photoViews))
                            .put("invariants", result.id.consistencyInvariants).put("requestedPhotoReason", result.id.requestedPhotoReason)
                            .put("visionCalls", result.usage.visionCalls).put("webCalls", result.usage.webCalls)
                            .put("marketCalls", result.id.marketCalls).put("requests", result.usage.requests)
                            .put("costUsd", result.usage.costUsd).put("queries", new JSONArray(result.id.webQueries))
                            .put("stagePayloads", new JSONArray(result.id.v2StagePayloads))
                            .put("observedTrace", result.id.v2ObservedFacts).put("inferredTrace", result.id.v2InferredFacts)
                            .put("retrievedTrace", result.id.v2RetrievedFacts).put("imagePreparationTrace", result.id.v2ImagePreparationTrace)
                            .put("rejectedSources", result.id.retrievedRejectedSources)
                            .put("candidateTrace", result.id.v2CandidateTrace).put("recoveryTrace", result.id.v2RecoveryTrace)
                            .put("screenshot", screenshot.getName());
                    assertExpected(fixture.key, result.id, result.usage);
                } catch (Throwable failure) {
                    allPassed = false;
                    run.put("status", "FAIL").put("error", failure.getClass().getSimpleName() + ": " + safe(failure.getMessage()).replace(apiKey, "[REDACTED]"));
                }
                run.put("completedAt", Instant.now().toString());
                runs.put(run);
                report.put("runs", runs).put("livePipelineTests", "INCOMPLETE");
                writeJson(new File(output, "v134-live-results.json"), report);
                if (ApiCallFailure.stopsLiveSuite(run.optString("pipelineFailureDomain"))) {
                    report.put("abortedReason", run.optString("pipelineFailureDomain"));
                    break liveCases;
                }
            }
        }
        report.put("runs", runs).put("livePipelineTests", allPassed ? "PASS" : "FAIL")
                .put("fourRealRegressions", allPassed ? "PASS" : "FAIL")
                .put("invariants", allInvariantsPassed(runs) ? "PASS" : "FAIL").put("costReport", "PRESENT")
                .put("apkLaunch", screenshotsPresent(output) ? "PASS" : "FAIL")
                .put("liveRunCount", runs.length()).put("expectedLiveRunCount", 12)
                .put("completedAt", Instant.now().toString());
        writeJson(new File(output, "v134-live-results.json"), report);
        assertTrue("Live installed-APK gate failed; see v134-live-results.json", allPassed && runs.length() == 12 && screenshotsPresent(output));
    }

    private static RunResult runThroughForegroundService(Instrumentation instrumentation, Context target, Case fixture, String mode, JSONObject run) throws Exception {
        AnalysisResultStore.reset(target);
        ArrayList<Uri> uris = new ArrayList<>();
        JSONArray inputs = new JSONArray();
        for (String asset : fixture.assets) {
            File input = materialize(instrumentation, target, asset, mode);
            inputs.put(new JSONObject().put("imageIndex", inputs.length()).put("sha256", sha256(input)).put("bytes", input.length()));
            uris.add(Uri.fromFile(input));
        }
        run.put("inputs", inputs).put("cachePolicy", "fresh_analysis_store_and_random_input_uris");
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
        run.put("computedTitle", snapshot.identification.title)
                .put("computedCoreStatus", snapshot.identification.coreIdentityStatus)
                .put("candidateTrace", snapshot.identification.v2CandidateTrace)
                .put("observedTrace", snapshot.identification.v2ObservedFacts)
                .put("inferredTrace", snapshot.identification.v2InferredFacts);
        if(snapshot.usage!=null)run.put("costUsd", snapshot.usage.costUsd);
        run.put("pipelineFailureDomain",snapshot.identification.pipelineFailureDomain)
                .put("callReasons",snapshot.identification.v2CallReasons)
                .put("recoveryTrace",snapshot.identification.v2RecoveryTrace)
                .put("stagePayloads",new JSONArray(snapshot.identification.v2StagePayloads));
        if(snapshot.identification.title==null||snapshot.identification.title.isEmpty())
            throw new AssertionError("Pipeline returned empty identity before rendering: "+snapshot.identification.pipelineFailureDomain+" "+snapshot.identification.v2CallReasons);
        awaitRenderedResult(instrumentation, activity, snapshot.identification.title);
        run.put("resultRenderedInActivity", true);
        return new RunResult(snapshot.identification, snapshot.usage == null ? new Models.Usage() : snapshot.usage);
    }

    private static void awaitRenderedResult(Instrumentation instrumentation, Activity activity, String title) {
        long deadline = SystemClock.elapsedRealtime() + 10_000L;
        boolean[] found = {false};
        do {
            instrumentation.runOnMainSync(() -> {
                TextView result = findText(activity.getWindow().getDecorView(), title);
                if (result != null && result.getWidth() > 0 && result.getHeight() > 0) {
                    result.requestRectangleOnScreen(new Rect(0, 0, result.getWidth(), result.getHeight()), true);
                    Rect visible = new Rect();
                    found[0] = result.isShown() && result.getGlobalVisibleRect(visible)
                            && visible.height() >= result.getHeight();
                }
            });
            if (found[0]) { instrumentation.waitForIdleSync(); SystemClock.sleep(250L); return; }
            SystemClock.sleep(100L);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Computed result was not rendered in the installed Activity");
    }
    private static TextView findText(View view, String title) {
        if (view instanceof TextView && !title.isEmpty() && title.contentEquals(((TextView)view).getText())) return (TextView)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) { TextView found=findText(group.getChildAt(i),title); if(found!=null)return found; }
        }
        return null;
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
            require("Topps".equalsIgnoreCase(id.brand) && contains(id.family, "Chrome") && (contains(id.family, "Update") || contains(id.observedSubSeries, "Update") || contains(id.sourceConfirmedSubSeries, "Update")), "sealed hierarchy wrong: " + id.title);
            require("2025-26".equals(id.physicalReleaseYear) || "2025-26".equals(id.sourceConfirmedReleaseYear), "sealed season wrong");
            require(contains(id.sealedFormat, "Hobby") && "CONFIRMED".equals(id.commercialFormatStatus), "Hobby configuration unresolved: " + id.sealedFormat);
            require(!contains(id.title, "Upper Deck") && ("FORMAT_PENDING".equals(id.exactIdentityStatus) || "CATALOG_MATCHED".equals(id.exactIdentityStatus)), "sealed result contaminated");
        } else if ("kobe".equals(key)) {
            require(id.title.startsWith("1997-98 ") && "SkyBox".equalsIgnoreCase(id.brand)
                    && contains(id.family, "Metal Universe") && "Kobe Bryant".equalsIgnoreCase(id.model)
                    && contains(id.title, "#81"), "sports identity wrong: " + id.title);
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
    private static boolean allInvariantsPassed(JSONArray runs) {
        if (runs.length() != 12) return false;
        for (int i = 0; i < runs.length(); i++)
            if (!"PASS".equals(runs.optJSONObject(i).optString("invariants", ""))) return false;
        return true;
    }
    private static boolean screenshotsPresent(File output) {File[] screenshots = output.listFiles((dir, name) -> name.endsWith(".png")); return screenshots != null && screenshots.length >= 12;}
    private static boolean contains(String text, String token) {return safe(text).toLowerCase(java.util.Locale.ROOT).contains(safe(token).toLowerCase(java.util.Locale.ROOT));}
    private static void require(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
    private static String safe(String value) {return value == null ? "" : value.trim();}
    private static final class Case {final String key; final String[] assets; Case(String key, String... assets) {this.key = key; this.assets = assets;}}
    private static final class RunResult {final Models.Identification id; final Models.Usage usage; RunResult(Models.Identification id, Models.Usage usage) {this.id = id; this.usage = usage;}}
}
