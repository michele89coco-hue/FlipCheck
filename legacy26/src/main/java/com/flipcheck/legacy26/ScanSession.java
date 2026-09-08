package com.flipcheck.legacy26;

import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;

/** One retained engine per process. Activity recreation never starts another paid scan. */
final class ScanSession {
    static final String ORIGIN = "https://flipcheck.local/";
    static final String PREFS = "scan_lifetime_178";
    private static ScanSession instance;
    final Context app;
    final WebView web;
    final GoogleVisionBridge google;
    final Handler main = new Handler(Looper.getMainLooper());
    final MutableContextWrapper webContext;
    private final SharedPreferences prefs;
    volatile MainActivity owner;
    volatile String token = "", state = "idle", failure = "";
    volatile boolean running, foregroundReady, ownerVisible;
    volatile long startedAt, finishedAt;
    volatile int backgroundTransitions, detachedActivities;
    boolean loaded, destroyed;

    static ScanSession obtain(Context context) {
        if (instance == null) instance = new ScanSession(context.getApplicationContext());
        return instance;
    }
    static ScanSession current() { return instance; }

    private ScanSession(Context context) {
        app = context;
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("running".equals(prefs.getString("state", ""))) {
            // Process death is an interrupted attempt, never permission to repeat an API request.
            prefs.edit().putString("state", "interrupted").putString("reason", "process_restarted").apply();
        }
        webContext = new MutableContextWrapper(app);
        web = new WebView(webContext);
        web.setFocusable(true); web.setFocusableInTouchMode(true);
        web.setBackgroundColor(0xff0b1020);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        google = new GoogleVisionBridge(web);
        web.addJavascriptInterface(google, "FlipCheckGoogle");
        web.addJavascriptInterface(new HostBridge(), "FlipCheckHost");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                android.net.Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !"flipcheck.local".equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                if (!path.matches("/(index\\.html|editions\\.js|targeted-fixes\\.js|visual-policy\\.js|visual-runtime\\.js|google-direct\\.js|background-runtime\\.js|slab-identity\\.js|identity-final\\.js)"))
                    return missing();
                try { return new WebResourceResponse(path.endsWith(".js") ? "application/javascript" : "text/html", "UTF-8", app.getAssets().open(path.substring(1))); }
                catch (Exception ignored) { return missing(); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if ((ORIGIN + "index.html").equals(request.getUrl().toString())) return false;
                if (owner != null && ("https".equals(request.getUrl().getScheme()) || "http".equals(request.getUrl().getScheme()))) {
                    try { owner.startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())); } catch (Exception ignored) { }
                }
                return true;
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                running = false; foregroundReady = false; state = "interrupted"; failure = "renderer_gone";
                prefs.edit().putString("state", state).putString("reason", failure).apply();
                app.stopService(new Intent(app, ScanForegroundService.class));
                google.close();
                if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
                web.destroy(); destroyed = true; instance = null;
                if (owner != null) owner.recreate();
                return true;
            }
        });
    }
    private static WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }
    void attach(MainActivity activity) {
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        owner = activity; webContext.setBaseContext(activity);
        web.onResume(); web.resumeTimers();
    }
    void visible(MainActivity activity, boolean value) {
        if (owner != activity) return;
        if (!value && ownerVisible && running) backgroundTransitions++;
        ownerVisible = value;
        // Do not pause the engine/timers while its bounded foreground scan is active.
        if (value) { web.onResume(); web.resumeTimers(); }
    }
    void detach(MainActivity activity) {
        if (owner != activity || destroyed) return;
        detachedActivities++;
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        web.setWebChromeClient(null);
        owner = null; ownerVisible = false; webContext.setBaseContext(app);
        if (!running) { google.close(); web.removeJavascriptInterface("FlipCheckGoogle"); web.removeJavascriptInterface("FlipCheckHost"); web.destroy(); destroyed = true; if (instance == this) instance = null; }
    }
    private boolean localPage() { return (ORIGIN + "index.html").equals(web.getUrl()); }
    private void replyStart(String id, boolean ok, String reason) {
        web.evaluateJavascript("window.FlipCheckBackground?.started(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(reason) + ")", null);
    }
    void foregroundStarted(String id) {
        if (!running || !token.equals(id)) return;
        foregroundReady = true; state = "running";
        replyStart(id, true, "");
    }
    void foregroundFailed(String id, String reason) {
        if (!token.equals(id)) return;
        running = false; foregroundReady = false; state = "interrupted"; failure = reason;
        prefs.edit().putString("state", state).putString("reason", reason).apply();
        replyStart(id, false, reason);
    }
    void cancel(String reason) {
        if (!running) return;
        failure = reason;
        web.evaluateJavascript("window.FlipCheckBackground?.cancel(" + JSONObject.quote(token) + "," + JSONObject.quote(reason) + ")", null);
    }
    void interrupted(String reason) {
        if (!running) return;
        cancel(reason); running = false; foregroundReady = false; state = "interrupted"; finishedAt = System.currentTimeMillis();
        prefs.edit().putString("state", state).putString("reason", reason).putLong("finishedAt", finishedAt).apply();
    }
    String info() {
        return GoogleVisionBridge.json("state", state, "running", running, "foregroundReady", foregroundReady,
            "startedAt", startedAt, "finishedAt", finishedAt, "backgroundTransitions", backgroundTransitions,
            "detachedActivities", detachedActivities, "activityAttached", owner != null, "activityVisible", ownerVisible,
            "wakeLockHeld", ScanForegroundService.wakeLockHeld(), "failure", failure, "retainedRuntime", true).toString();
    }
    public final class HostBridge {
        @JavascriptInterface public String buildInfo() {
            return GoogleVisionBridge.json("versionCode", BuildConfig.VERSION_CODE, "versionName", BuildConfig.VERSION_NAME, "sourceCommit", BuildConfig.SOURCE_COMMIT).toString();
        }
        @JavascriptInterface public String photoPickerInfo() { return owner == null ? "{}" : owner.photoPickerInfo(); }
        @JavascriptInterface public void preparePhotoPicker(boolean multiple) { if (owner != null) owner.preparePhotoPicker(multiple); }
        @JavascriptInterface public void prepareDocumentPicker() { if (owner != null) owner.prepareDocumentPicker(); }
        @JavascriptInterface public void saveDiagnostic(String json) { main.post(() -> { if (localPage() && owner != null) owner.saveDiagnostic(json); }); }
        @JavascriptInterface public String backgroundInfo() { return info(); }
        @JavascriptInterface public String lastScan() {
            return GoogleVisionBridge.json("state", prefs.getString("state", "idle"), "snapshot", prefs.getString("snapshot", ""), "finishedAt", prefs.getLong("finishedAt", 0)).toString();
        }
        @JavascriptInterface public void beginScan(String id) {
            if (id == null || !id.matches("[a-zA-Z0-9-]{8,80}")) return;
            main.post(() -> {
                if (!localPage() || owner == null || !ownerVisible || running) { replyStart(id, false, "scan_not_available"); return; }
                token = id; running = true; foregroundReady = false; state = "starting"; failure = "";
                startedAt = System.currentTimeMillis(); finishedAt = 0; backgroundTransitions = 0;
                prefs.edit().putString("state", "running").putString("snapshot", "").putString("reason", "").putLong("startedAt", startedAt).apply();
                try {
                    Intent intent = new Intent(app, ScanForegroundService.class).putExtra("token", id);
                    if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent); else app.startService(intent);
                    owner.requestScanNotifications();
                } catch (Exception error) { foregroundFailed(id, error.getClass().getSimpleName()); }
            });
        }
        @JavascriptInterface public void endScan(String id, String snapshot, String outcome) {
            if (id == null || snapshot == null || snapshot.length() > 400000) return;
            main.post(() -> {
                if (!localPage() || !token.equals(id)) return;
                try {
                    JSONObject parsed = new JSONObject(snapshot);
                    // Keep result data only. API keys, request bodies and image payloads never enter this store.
                    JSONObject safe = new JSONObject();
                    for (String key : new String[]{"identification", "visionResult", "usage", "versionCode", "versionName", "sourceCommit", "exportedAt"}) if (parsed.has(key)) safe.put(key, parsed.get(key));
                    running = false; foregroundReady = false; finishedAt = System.currentTimeMillis();
                    state = "cancelled".equals(outcome) ? "cancelled" : "failed".equals(outcome) ? "failed" : "completed";
                    prefs.edit().putString("state", state).putString("snapshot", safe.toString()).putLong("finishedAt", finishedAt).putBoolean("completedInBackground", !ownerVisible).apply();
                    app.stopService(new Intent(app, ScanForegroundService.class));
                } catch (Exception ignored) { interrupted("invalid_result_snapshot"); app.stopService(new Intent(app, ScanForegroundService.class)); }
            });
        }
    }
}
