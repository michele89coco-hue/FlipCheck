package com.flipcheck.legacy26;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Small Android host for the original v0.26 HTML engine; no native identity pipeline. */
public final class MainActivity extends Activity {
    private static final int PICK_IMAGES = 26, SAVE_DIAGNOSTIC = 27;
    private static final String ORIGIN = "https://flipcheck.local/";
    private WebView web;
    private ValueCallback<Uri[]> pickerCallback;
    private boolean pickerMultiple;
    private String pendingDiagnostic;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        web.setBackgroundColor(0xff0b1020);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(true);
        web.addJavascriptInterface(new DiagnosticBridge(), "FlipCheckHost");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !"flipcheck.local".equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                if (!path.matches("/(index\\.html|editions\\.js|targeted-fixes\\.js)"))
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
                try {
                    return new WebResourceResponse(path.endsWith(".js") ? "application/javascript" : "text/html", "UTF-8", getAssets().open(path.substring(1)));
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri.toString().equals(ORIGIN + "index.html")) return false;
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) { }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                finishPicker(null);
                pickerCallback = callback;
                pickerMultiple = params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, pickerMultiple);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivityForResult(intent, PICK_IMAGES); }
                catch (ActivityNotFoundException e) { finishPicker(null); }
                return true;
            }
        });
        setContentView(web);
        web.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.loadUrl(ORIGIN + "index.html");
    }

    private void finishPicker(Uri[] values) {
        ValueCallback<Uri[]> callback = pickerCallback;
        pickerCallback = null;
        if (callback != null) callback.onReceiveValue(values);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGES) {
            List<Uri> values = new ArrayList<>();
            if (resultCode == RESULT_OK && data != null) {
                ClipData clips = data.getClipData();
                if (clips != null) for (int i = 0; i < clips.getItemCount(); i++) {
                    Uri uri = clips.getItemAt(i).getUri();
                    if (uri != null && "content".equals(uri.getScheme()) && !values.contains(uri)) values.add(uri);
                    if (!pickerMultiple && !values.isEmpty()) break;
                }
                else if (data.getData() != null && "content".equals(data.getData().getScheme())) values.add(data.getData());
            }
            finishPicker(values.isEmpty() ? null : values.toArray(new Uri[0]));
        } else if (requestCode == SAVE_DIAGNOSTIC) {
            String payload = pendingDiagnostic;
            pendingDiagnostic = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || payload == null) return;
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new IllegalStateException("File non disponibile");
                stream.write(payload.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Diagnostica salvata", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "Impossibile salvare la diagnostica", Toast.LENGTH_LONG).show(); }
        }
    }

    public final class DiagnosticBridge {
        @JavascriptInterface public String buildInfo() {
            return "{\"versionCode\":158,\"versionName\":\"0.26.1-targeted\",\"sourceCommit\":\"" + BuildConfig.SOURCE_COMMIT + "\"}";
        }
        @JavascriptInterface public void saveDiagnostic(String json) {
            if (json == null || json.length() > 300000) return;
            runOnUiThread(() -> {
                if (!ORIGIN.concat("index.html").equals(web.getUrl()) || pendingDiagnostic != null) return;
                pendingDiagnostic = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "FlipCheck-26Fix-diagnostica.json");
                try { startActivityForResult(intent, SAVE_DIAGNOSTIC); }
                catch (ActivityNotFoundException e) { pendingDiagnostic = null; }
            });
        }
    }

    @Override protected void onDestroy() {
        finishPicker(null);
        if (web != null) { web.removeJavascriptInterface("FlipCheckHost"); web.destroy(); }
        super.onDestroy();
    }
}
